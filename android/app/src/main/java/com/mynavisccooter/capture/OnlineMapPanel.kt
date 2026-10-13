package com.mynavisccooter.capture

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.*
import android.net.Uri
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.*
import android.view.inputmethod.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.*
import org.maplibre.android.annotations.*
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** Online maps and GPS guidance while this page is foreground. Community data is shared explicitly. */
@Suppress("DEPRECATION")
class OnlineMapPanel(context: Context, private val requestLocation: () -> Unit,
    private val requestImport: () -> Unit = {}) : FrameLayout(context), LocationListener {
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun surface(dark: Boolean = false) = GradientDrawable().apply {
        setColor(if (dark) 0xFF0A263E.toInt() else 0xFFF7F9FC.toInt()); cornerRadius = dp(24).toFloat()
    }
    private fun label(value: String, size: Float = 14f) = TextView(context).apply {
        text = value; textSize = size; setTextColor(0xFF142537.toInt())
    }
    private fun button(value: String, action: () -> Unit) = Button(context).apply {
        text = value; textSize = 13f; isAllCaps = false; minWidth = 0; minimumWidth = 0
        setTextColor(0xFF142537.toInt()); background = surface(); minHeight = dp(44)
        setPadding(dp(8), 0, dp(8), 0); setOnClickListener { action() }
    }
    private val worker = Executors.newSingleThreadExecutor()
    private val library = GpsLibrary(context)
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val message = label("Mapa online · a carregar…", 12f)
    private val summary = label("Explorar · MyNaviScooter", 19f)
    private val instruction = label("", 18f).apply {
        setTextColor(Color.WHITE); background = surface(true); elevation = dp(6).toFloat()
        setPadding(dp(16), dp(12), dp(16), dp(12)); visibility = View.GONE
    }
    private val routeButton = button("Percurso") { calculateRoute() }
    private val navigateButton = button("Iniciar") { if (navigating) stopNavigation() else startNavigation() }
    private val actions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var active = false
    private var started = false
    private var destroyed = false
    private var styleReady = false
    private var locating = false
    private var followLocation = true
    private var dark = false
    private var bearingMode = false
    private var lastLocation: Location? = null
    private var marker: Marker? = null
    private var destinationMarker: Marker? = null
    private var routeLine: Polyline? = null
    private val dataMarkers = mutableListOf<Marker>()
    private var destination: GpsPoint? = null
    private var destinationName = "Destino"
    private var manualStart: GpsPoint? = null
    private var route: GpsRoute? = null
    private var routeGeneration = 0
    private var requestingRoute = false
    private var lastRouteRequest = -30000L
    private var pendingRoute = false
    private var navigating = false
    private var voice = true
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private var spokenStep = -1
    private var spokenNearStep = -1
    private val announced = mutableSetOf<String>()
    private var offRouteFixes = 0
    private var recording = false
    private val track = mutableListOf<GpsPoint>()
    private var recordedDistance = 0.0
    init {
        setBackgroundColor(0xFFE4E9EF.toInt())
        val searchRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; background = surface(); elevation = dp(5).toFloat(); setPadding(dp(12), 0, dp(8), 0)
        }
        val search = EditText(context).apply {
            hint = "Pesquisar local ou endereço"; textSize = 15f; setSingleLine(); background = null
            setTextColor(0xFF142537.toInt()); setHintTextColor(0xFF62758A.toInt()); imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEARCH) {
                searchPlace(text.toString()); clearFocus(); true
            } else false }
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, -1, 1f))
        searchRow.addView(button("⌕") { searchPlace(search.text.toString()); search.clearFocus() }.apply {
            contentDescription = "Pesquisar destino"; background = null
        }, LinearLayout.LayoutParams(dp(48), -1))
        addView(searchRow, LayoutParams(-1, dp(56), Gravity.TOP).apply {
            leftMargin = dp(12); rightMargin = dp(72); topMargin = dp(10)
        })
        addView(instruction, LayoutParams(-1, -2, Gravity.TOP).apply { leftMargin = dp(12); rightMargin = dp(12); topMargin = dp(76) })
        val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun control(symbol: String, description: String, action: () -> Unit) {
            controls.addView(button(symbol, action).apply { contentDescription = description; textSize = 22f; elevation = dp(4).toFloat() },
                LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(8) })
        }
        control("◎", "A minha localização") { followLocation = true; manualStart = null; locate() }
        control("↥", "Orientação do mapa") { bearingMode = !bearingMode; followLocation = true; lastLocation?.let { onLocationChanged(it) } }
        control("◐", "Alterar mapa claro ou escuro") { dark = !dark; loadStyle() }
        control("+", "Aproximar mapa") { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
        control("−", "Afastar mapa") { map?.animateCamera(CameraUpdateFactory.zoomOut()) }
        addView(controls, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(12) })
        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; background = surface(); elevation = dp(5).toFloat(); setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(summary); addView(message); addView(actions)
            summary.setOnClickListener { actions.visibility = if (actions.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }
        fun row(vararg buttons: Button) { actions.addView(LinearLayout(context).apply { buttons.forEach {
            addView(it, LinearLayout.LayoutParams(0, dp(44), 1f).apply { topMargin = dp(6); rightMargin = dp(4) })
        } }) }
        row(routeButton, navigateButton, button("Guardar") { saveSelected() })
        row(button("Reportar") { reportHazard() }, button("Biblioteca") { showLibrary() }, button("Mais") { moreOptions() })
        addView(bottom, LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(38) })
        routeButton.isEnabled = false; navigateButton.isEnabled = false
    }
    private fun fail(value: String) { message.text = value }
    private fun guard(action: () -> Unit) { runCatching(action).onFailure { fail(it.message ?: "Operação indisponível.") } }
    private fun freshFix() = lastLocation?.takeIf { System.currentTimeMillis() - it.time in 0..30000 && it.accuracy <= 60 }
    private fun Location.point() = GpsPoint(latitude, longitude)
    private fun GpsPoint.latLng() = LatLng(latitude, longitude)
    fun show(visible: Boolean) {
        if (destroyed) return
        active = visible
        if (visible && mapView == null) {
            MapLibre.getInstance(context)
            mapView = MapView(context).also { view ->
                addView(view, 0, LayoutParams(-1, -1)); view.onCreate(null)
                view.addOnDidFailLoadingMapListener { fail("Mapa indisponível. Verifica a internet; usa Mais → Recarregar mapa.") }
                view.setOnTouchListener { v, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) followLocation = false
                    v.parent.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL); false
                }
                view.getMapAsync { loaded -> if (!destroyed) {
                    map = loaded; loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(50.0, 10.0), 4.0))
                    loaded.addOnMapLongClickListener { point -> pointOptions(GpsPoint(point.latitude, point.longitude)); true }; loadStyle()
                } }
            }
        }
        if (visible && !started) { mapView?.onStart(); mapView?.onResume(); started = true }
        if (!visible && started) { mapView?.onPause(); mapView?.onStop(); started = false }
        if (!visible) {
            locationManager?.removeUpdates(this); speech?.stop(); keepScreenOn = false
            if (navigating) instruction.text = "Navegação em pausa · regressa à página GPS"
        } else { keepScreenOn = navigating || recording; if (locating) locate() }
    }
    private fun loadStyle() {
        styleReady = false; marker = null; destinationMarker = null; routeLine = null; dataMarkers.clear()
        map?.setStyle(if (dark) "https://tiles.openfreemap.org/styles/dark" else STYLE) { if (!destroyed) {
            styleReady = true; redrawLibrary(); redrawRoute(); destination?.let { drawDestination(it) }; lastLocation?.let { onLocationChanged(it) }
            if (!navigating && route == null) fail("Pesquisa um destino ou mantém premido um ponto do mapa.")
        } }
    }
    private fun drawDestination(point: GpsPoint) {
        if (!styleReady) return
        destinationMarker?.let { map?.removeMarker(it) }
        destinationMarker = map?.addMarker(MarkerOptions().position(point.latLng()).title(destinationName))
    }
    private fun setDestination(point: GpsPoint, name: String) {
        if (!point.valid()) return
        stopNavigation(false); routeGeneration++; route = null; pendingRoute = false; destination = point; destinationName = name.take(160); followLocation = false
        routeLine?.let { map?.removePolyline(it) }; routeLine = null; drawDestination(point)
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point.latLng(), 16.0))
        summary.text = destinationName; fail("Toca em Percurso para calcular a rota."); routeButton.isEnabled = true; navigateButton.isEnabled = false
    }
    private fun pointOptions(point: GpsPoint) {
        AlertDialog.Builder(context).setTitle("Ponto no mapa").setItems(arrayOf("Ir para aqui", "Começar percurso aqui", "Guardar local", "Assinalar perigo")) { _, index -> when (index) {
            0 -> setDestination(point, "Ponto selecionado")
            1 -> { manualStart = point; fail("Origem escolhida. Seleciona o destino e toca em Percurso.") }
            2 -> askName("Guardar local") { name -> guard { library.addPlace(name, point); redrawLibrary(); fail("Local guardado no telemóvel.") } }
            3 -> reportHazard(point)
        } }.setNegativeButton("Fechar", null).show()
    }
    private fun askName(title: String, action: (String) -> Unit) {
        val input = EditText(context).apply { hint = "Nome"; setSingleLine() }
        val dialog = AlertDialog.Builder(context).setTitle(title).setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim(); if (name.isBlank()) input.error = "Escreve um nome" else { action(name); dialog.dismiss() }
        } }; dialog.show()
    }
    private fun searchPlace(query: String) {
        if (query.isBlank()) return
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
        if (!Geocoder.isPresent()) { fail("Pesquisa indisponível neste telemóvel. Seleciona um ponto no mapa."); return }
        fail("A pesquisar…")
        worker.execute {
            val result = runCatching { Geocoder(context, Locale.getDefault()).getFromLocationName(query, 5).orEmpty() }
            post { if (!destroyed && active) result.onSuccess { addresses ->
                if (addresses.isEmpty()) fail("Local não encontrado. Inclui a cidade na pesquisa.")
                else AlertDialog.Builder(context).setTitle("Escolher destino").setItems(addresses.map { it.getAddressLine(0) ?: query }.toTypedArray()) { _, index ->
                    val place = addresses[index]; setDestination(GpsPoint(place.latitude, place.longitude), place.getAddressLine(0) ?: query)
                }.setNegativeButton("Cancelar", null).show()
            }.onFailure { fail("Pesquisa indisponível. Verifica a internet e tenta novamente.") } }
        }
    }
    private fun calculateRoute(automatic: Boolean = false) {
        val target = destination ?: run { fail("Seleciona primeiro um destino."); return }
        if (requestingRoute) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRouteRequest < (if (automatic) 30000 else 2000)) { if (!automatic) fail("Aguarda antes de pedir outro percurso."); return }
        val start = manualStart ?: freshFix()?.point() ?: run { pendingRoute = true; locate(); fail("A aguardar posição GPS recente para calcular."); return }
        requestingRoute = true; routeButton.isEnabled = false; lastRouteRequest = now; val generation = ++routeGeneration
        fail(if (automatic) "A recalcular percurso…" else "A calcular percurso ciclável…")
        worker.execute {
            val result = runCatching { GpsRouting.request(start, target) }
            post {
                requestingRoute = false; routeButton.isEnabled = destination != null
                if (!destroyed && generation == routeGeneration) result.onSuccess { loaded ->
                    route = loaded; offRouteFixes = 0; spokenStep = -1; spokenNearStep = -1; redrawRoute(); navigateButton.isEnabled = true
                    summary.text = "${GpsRouting.minutes(loaded.duration)} · ${GpsRouting.meters(loaded.distance)}"; fail("Base ciclável · OSRM/FOSSGIS · toca em Iniciar.")
                    if (automatic && navigating) { speak("Percurso recalculado"); freshFix()?.let { updateGuidance(it) } }
                    else map?.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.fromLatLngs(loaded.points.map { it.latLng() }), dp(56)))
                }.onFailure { fail(it.message ?: "Não foi possível calcular. Tenta novamente com internet.") }
            }
        }
    }
    private fun redrawRoute() {
        if (!styleReady) return
        routeLine?.let { map?.removePolyline(it) }
        routeLine = route?.let { map?.addPolyline(PolylineOptions().addAll(it.points.map { point -> point.latLng() }).color(Color.rgb(0, 167, 235)).width(6f)) }
    }
    private fun ensureSpeech() {
        if (speech != null) return
        speech = TextToSpeech(context.applicationContext) { status -> post { if (!destroyed && status == TextToSpeech.SUCCESS) {
            val engine = speech ?: return@post; val available = engine.setLanguage(Locale("pt", "PT"))
            speechReady = available != TextToSpeech.LANG_MISSING_DATA && available != TextToSpeech.LANG_NOT_SUPPORTED; engine.setSpeechRate(.95f)
            if (!speechReady) fail("Voz portuguesa indisponível. Instala-a nas definições de texto para voz do Android.")
            else if (navigating) { spokenStep = -1; freshFix()?.let { updateGuidance(it) } }
        } } }
    }
    private fun speak(value: String) { if (voice && active && speechReady) speech?.speak(value, TextToSpeech.QUEUE_FLUSH, null, "gps-${SystemClock.elapsedRealtime()}") }
    private fun startNavigation() {
        if (route == null) { calculateRoute(); return }
        val fix = freshFix() ?: run { locate(); fail("Aguarda uma posição GPS recente e toca novamente em Iniciar."); return }
        navigating = true; locating = true; followLocation = true; manualStart = null; spokenStep = -1; spokenNearStep = -1; announced.clear()
        ensureSpeech(); locate(); navigateButton.text = "Terminar"; instruction.visibility = View.VISIBLE; keepScreenOn = true; updateGuidance(fix)
    }
    private fun stopNavigation(showMessage: Boolean = true) {
        navigating = false; navigateButton.text = "Iniciar"; instruction.visibility = View.GONE; speech?.stop(); keepScreenOn = recording
        if (showMessage) fail("Navegação terminada. O percurso continua no mapa.")
    }
    private fun updateGuidance(location: Location) {
        val current = route ?: return; if (!navigating) return
        if (location.accuracy > 60) { instruction.text = "Sinal GPS impreciso · a aguardar melhor posição"; return }
        val progress = GpsGeometry.progress(current, location.point())
        if (progress.arrived && destination?.let { GpsGeometry.distance(it, location.point()) < 50 } != false) {
            stopNavigation(false); speak("Chegaste ao destino"); summary.text = "Chegaste ao destino"; fail(destinationName); return
        }
        val speed = if (location.hasSpeed()) " · GPS ${(location.speed * 3.6).toInt()} km/h" else ""
        summary.text = "${GpsRouting.minutes(progress.seconds)} · ${GpsRouting.meters(progress.remainingMeters)}$speed"
        instruction.text = "${GpsRouting.meters(progress.nextMeters)} · ${progress.nextInstruction}"
        if (progress.offRouteMeters > 60 && location.accuracy <= 30) {
            offRouteFixes++; instruction.text = "Fora do percurso · a procurar nova rota"; if (offRouteFixes >= 3) calculateRoute(true)
        } else offRouteFixes = 0
        if (progress.offRouteMeters <= 60 && progress.nextIndex != spokenStep) {
            speak("Dentro de ${GpsRouting.meters(progress.nextMeters)}, ${progress.nextInstruction}"); spokenStep = progress.nextIndex
        } else if (progress.offRouteMeters <= 60 && progress.nextMeters <= 40 && progress.nextIndex != spokenNearStep) {
            speak(progress.nextInstruction); spokenNearStep = progress.nextIndex
        }
        library.hazards().firstOrNull { it.id !in announced && System.currentTimeMillis() - it.created < 30L * 86400000 &&
            GpsGeometry.distance(it.point, location.point()) < 80 }?.let {
            announced.add(it.id); fail("Alerta próximo: ${it.kind}"); speak("Atenção, ${it.kind} próximo")
        }
    }
    @Suppress("MissingPermission")
    fun locate() {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!allowed) { requestLocation(); return }; locating = true; if (!active) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { locationManager?.isProviderEnabled(it) == true }
        if (providers.isEmpty()) { fail("Ativa a localização nas definições do telemóvel."); return }; locationManager?.removeUpdates(this)
        providers.forEach { provider -> runCatching {
            locationManager?.getLastKnownLocation(provider)?.takeIf { System.currentTimeMillis() - it.time in 0..30000 }?.let { onLocationChanged(it) }
            locationManager?.requestLocationUpdates(provider, 1500L, 3f, this, Looper.getMainLooper())
        }.onFailure { fail("Localização indisponível. Verifica as permissões.") } }
    }
    override fun onLocationChanged(location: Location) {
        if (destroyed || !active || System.currentTimeMillis() - location.time !in 0..30000) return
        if (lastLocation?.let { System.currentTimeMillis() - it.time in 0..10000 && it.accuracy < location.accuracy / 2 } == true) return
        lastLocation = location
        if (styleReady) {
            marker?.let { map?.removeMarker(it) }; marker = map?.addMarker(MarkerOptions().position(location.point().latLng()).title("A tua localização"))
            if (followLocation) map?.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(location.point().latLng())
                .zoom(if (navigating) 17.0 else 16.0).bearing(if (bearingMode && location.hasBearing()) location.bearing.toDouble() else 0.0)
                .tilt(if (navigating) 40.0 else 0.0).build()))
        }
        if (pendingRoute && freshFix() != null) { pendingRoute = false; calculateRoute() }; updateGuidance(location)
        if (recording && location.accuracy <= 40) {
            val point = location.point(); val distance = track.lastOrNull()?.let { GpsGeometry.distance(it, point) } ?: 0.0
            if (track.isEmpty() || distance in 5.0..100.0) { if (track.size < 10000) { track += point; recordedDistance += distance }
                else { recording = false; fail("Registo cheio. Guarda o trajeto em Mais → Terminar registo.") } }
            if (!navigating) summary.text = "A registar · ${GpsRouting.meters(recordedDistance)}"
        }
        if (!navigating && !recording && route == null && destination == null) fail("GPS · precisão aproximada ${location.accuracy.toInt()} m")
    }
    private fun reportHazard(point: GpsPoint? = null) {
        val at = point ?: freshFix()?.point() ?: run { fail("Atualiza o GPS ou mantém premido o ponto no mapa para reportar."); locate(); return }
        AlertDialog.Builder(context).setTitle("Assinalar perigo neste ponto").setItems(GpsLibrary.KINDS.toTypedArray()) { _, index ->
            val input = EditText(context).apply { hint = "Descrição opcional" }
            AlertDialog.Builder(context).setTitle(GpsLibrary.KINDS[index]).setView(input).setMessage("Guardado no teu telemóvel. Partilha-o pela biblioteca GPS.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Adicionar") { _, _ -> guard {
                    library.addHazard(GpsLibrary.KINDS[index], at, input.text.toString()); redrawLibrary(); fail("Alerta guardado.")
                } }.show()
        }.setNegativeButton("Cancelar", null).show()
    }
    private fun redrawLibrary() {
        if (!styleReady) return
        dataMarkers.forEach { map?.removeMarker(it) }; dataMarkers.clear()
        library.places().forEach { place -> map?.addMarker(MarkerOptions().position(place.point.latLng()).title("★ ${place.name}"))?.let { dataMarkers += it } }
        library.hazards().filter { System.currentTimeMillis() - it.created < 30L * 86400000 }.forEach { hazard ->
            map?.addMarker(MarkerOptions().position(hazard.point.latLng()).title("⚠ ${hazard.kind}").snippet(hazard.note))?.let { dataMarkers += it }
        }
    }
    private fun saveSelected() {
        val point = destination ?: freshFix()?.point() ?: run { fail("Seleciona um local no mapa primeiro."); return }
        val items = if (route == null) arrayOf("Guardar local") else arrayOf("Guardar local", "Guardar trajeto", "Recomendar trajeto")
        AlertDialog.Builder(context).setTitle("Guardar na biblioteca GPS").setItems(items) { _, index -> askName(items[index]) { name -> guard {
            if (index == 0) library.addPlace(name, point) else library.addRoute(name, route!!.points, index == 2, route)
            redrawLibrary(); fail("Guardado na biblioteca GPS deste telemóvel.")
        } } }.setNegativeButton("Cancelar", null).show()
    }
    private fun showLibrary() {
        AlertDialog.Builder(context).setTitle("Biblioteca GPS · neste telemóvel").setItems(arrayOf("Locais guardados", "Trajetos guardados", "Trajetos recomendados", "Alertas", "Partilhar biblioteca", "Importar ficheiro")) { _, index -> when (index) {
            0 -> choosePlace(); 1 -> chooseSavedRoute(false); 2 -> chooseSavedRoute(true); 3 -> chooseHazard(); 4 -> shareLibrary(); 5 -> requestImport()
        } }.setNegativeButton("Fechar", null).show()
    }
    private fun choosePlace() {
        val places = library.places(); if (places.isEmpty()) { fail("Ainda não guardaste locais."); return }
        AlertDialog.Builder(context).setTitle("Locais guardados").setItems(places.map { it.name }.toTypedArray()) { _, index ->
            val place = places[index]; AlertDialog.Builder(context).setTitle(place.name).setItems(arrayOf("Ir para", "Apagar")) { _, action ->
                if (action == 0) setDestination(place.point, place.name) else guard { library.remove("places", place.id); redrawLibrary(); fail("Local apagado.") }
            }.show()
        }.show()
    }
    private fun chooseSavedRoute(recommended: Boolean) {
        val routes = library.routes().filter { it.recommended == recommended }
        if (routes.isEmpty()) { fail("Ainda não existem trajetos nesta lista. Guarda um percurso ou importa uma biblioteca."); return }
        AlertDialog.Builder(context).setTitle(if (recommended) "Trajetos recomendados" else "Trajetos guardados").setItems(routes.map { it.name }.toTypedArray()) { _, index ->
            val saved = routes[index]; AlertDialog.Builder(context).setTitle(saved.name).setItems(arrayOf("Mostrar no mapa", "Calcular rota até ao início", "Apagar")) { _, action -> when (action) {
                0 -> {
                    stopNavigation(false); routeGeneration++; destination = saved.points.last(); destinationName = saved.name
                    val meters = saved.points.zipWithNext().sumOf { (a, b) -> GpsGeometry.distance(a, b) }
                    route = saved.navigation ?: GpsRoute(saved.points, meters, meters / 4.0, emptyList()); redrawRoute(); drawDestination(saved.points.last()); summary.text = saved.name
                    fail(if (saved.navigation != null) "Trajeto guardado com instruções. Toca em Iniciar." else "Viagem gravada · segue a linha no mapa, sem instruções de curvas.")
                    navigateButton.isEnabled = saved.navigation != null; routeButton.isEnabled = true
                    map?.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.fromLatLngs(saved.points.map { it.latLng() }), dp(56)))
                }
                1 -> { setDestination(saved.points.first(), "Início · ${saved.name}"); calculateRoute() }
                2 -> guard { library.remove("routes", saved.id); fail("Trajeto apagado.") }
            } }.show()
        }.show()
    }
    private fun chooseHazard() {
        val hazards = library.hazards(); if (hazards.isEmpty()) { fail("Ainda não existem alertas locais."); return }
        AlertDialog.Builder(context).setTitle("Alertas guardados").setItems(hazards.map { "${it.kind} · ${it.note.ifBlank { "Sem descrição" }}" }.toTypedArray()) { _, index ->
            val hazard = hazards[index]; AlertDialog.Builder(context).setTitle(hazard.kind).setItems(arrayOf("Mostrar no mapa", "Já não existe · apagar")) { _, action ->
                if (action == 0) { followLocation = false; map?.animateCamera(CameraUpdateFactory.newLatLngZoom(hazard.point.latLng(), 17.0)) }
                else guard { library.remove("hazards", hazard.id); redrawLibrary(); fail("Alerta apagado.") }
            }.show()
        }.show()
    }
    private fun shareLibrary() = guard {
        val directory = File(context.cacheDir, "gps-sharing").apply { mkdirs() }
        val file = File(directory, "mynavi-gps.json").apply { writeText(library.exportJson()) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Partilhar locais, trajetos e alertas"))
    }
    fun importLibrary(text: String) = guard { val count = library.importJson(text); redrawLibrary(); fail("$count elementos importados para a biblioteca GPS.") }
    private fun toggleRecording() {
        if (recording || track.isNotEmpty()) {
            recording = false; keepScreenOn = navigating
            if (track.size < 2) { track.clear(); fail("Não existem pontos suficientes para guardar."); return }
            askName("Guardar viagem GPS") { name -> guard { library.addRoute(name, track.toList(), false); track.clear(); fail("Viagem GPS guardada.") } }; return
        }
        recording = true; track.clear(); recordedDistance = 0.0; locate(); keepScreenOn = true; fail("Registo GPS iniciado enquanto esta página estiver aberta.")
    }
    private fun moreOptions() {
        val options = arrayOf("Instruções do percurso", if (voice) "Desativar voz" else "Ativar voz",
            if (recording || track.isNotEmpty()) "Terminar registo GPS" else "Registar viagem GPS",
            "Recalcular percurso", "Limpar destino e percurso", "Recarregar mapa", "Biblioteca GPS", "Sobre o GPS e os dados")
        AlertDialog.Builder(context).setTitle("Opções GPS").setItems(options) { _, index -> when (index) {
            0 -> AlertDialog.Builder(context).setTitle("Instruções").setMessage(route?.steps?.joinToString("\n\n") {
                "${GpsRouting.meters(it.startsAt)} · ${it.instruction}"
            }?.ifBlank { "Este trajeto guardado não tem instruções de curvas." } ?: "Calcula um percurso primeiro.").setPositiveButton("Fechar", null).show()
            1 -> { voice = !voice; if (voice) ensureSpeech() else speech?.stop(); fail(if (voice) "Voz ativada." else "Voz desativada.") }
            2 -> toggleRecording(); 3 -> calculateRoute()
            4 -> {
                stopNavigation(false); routeGeneration++; pendingRoute = false; destination = null; route = null; manualStart = null
                routeLine?.let { map?.removePolyline(it) }; destinationMarker?.let { map?.removeMarker(it) }; routeLine = null; destinationMarker = null
                routeButton.isEnabled = false; navigateButton.isEnabled = false; summary.text = "Explorar · MyNaviScooter"; fail("Destino e percurso limpos.")
            }
            5 -> loadStyle(); 6 -> showLibrary()
            7 -> AlertDialog.Builder(context).setTitle("GPS MyNaviScooter")
                .setMessage("Mapas: OpenFreeMap/OpenStreetMap. Rotas: OSRM/FOSSGIS, perfil de bicicleta. Este perfil não valida as regras locais para scooters. Pesquisa: serviço de endereços do Android.\n\nOs pedidos de rotas enviam origem e destino ao fornecedor. Biblioteca e viagens ficam neste telemóvel até as partilhares. Alertas são observações dos utilizadores; ainda não existe sincronização automática da comunidade.\n\nOrientação e registo param ao sair da página GPS ou colocar a app em segundo plano.")
                .setNeutralButton("Corrigir mapa") { _, _ -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/fixthemap"))) }
                .setPositiveButton("Fechar", null).show()
        } }.setNegativeButton("Fechar", null).show()
    }
    fun destroy() {
        destroyed = true; routeGeneration++; worker.shutdownNow(); locationManager?.removeUpdates(this); speech?.stop(); speech?.shutdown(); speech = null
        if (started) { mapView?.onPause(); mapView?.onStop() }; mapView?.onDestroy(); mapView = null; map = null; started = false; keepScreenOn = false
    }
    companion object { const val STYLE = "https://tiles.openfreemap.org/styles/liberty" }
}
