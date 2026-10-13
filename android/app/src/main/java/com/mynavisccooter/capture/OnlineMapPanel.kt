package com.mynavisccooter.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.Geocoder
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.os.Build
import java.util.Locale
import java.util.concurrent.Executors
import android.os.Looper
import android.view.MotionEvent
import android.widget.*
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.MarkerOptions

/** Loads online tiles only when the GPS page is opened. No offline region downloads. */
class OnlineMapPanel(context: Context, private val requestLocation: () -> Unit) : FrameLayout(context), LocationListener {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun surface() = GradientDrawable().apply { setColor(0xFFF7F9FC.toInt()); cornerRadius = dp(26).toFloat() }
    private val worker = Executors.newSingleThreadExecutor()
    private var destroyed = false
    private var followLocation = true
    private var styleReady = false
    private var destination: org.maplibre.android.annotations.Marker? = null
    private val message = TextView(context).apply {
        text = "Mapa online · abre esta página para carregar."
        setTextColor(0xFF35465A.toInt())
        textSize = 13f
    }
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var active = false
    private var started = false
    private var locating = false
    private var lastLocation: Location? = null
    private var marker: org.maplibre.android.annotations.Marker? = null
    init {
        setBackgroundColor(0xFFE4E9EF.toInt())
        val searchRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; background = surface(); elevation = dp(5).toFloat()
            setPadding(dp(12), 0, dp(8), 0)
        }
        val search = EditText(context).apply {
            hint = "Pesquisar local ou endereço"
            setTextColor(0xFF142537.toInt()); setHintTextColor(0xFF62758A.toInt())
            textSize = 15f; setSingleLine(); background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { searchPlace(text.toString()); clearFocus(); true } else false
            }
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, -1, 1f))
        searchRow.addView(Button(context).apply {
            text = "⌕"; contentDescription = "Pesquisar destino"; background = null
            setTextColor(0xFF142537.toInt())
            setOnClickListener { searchPlace(search.text.toString()); search.clearFocus() }
        }, LinearLayout.LayoutParams(dp(48), -1))
        addView(searchRow, LayoutParams(-1, dp(56), Gravity.TOP).apply { leftMargin = dp(12); rightMargin = dp(72); topMargin = dp(10) })
        val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun control(title: String, description: String, action: () -> Unit) {
            controls.addView(Button(context).apply {
                text = title; contentDescription = description; setTextColor(0xFF142537.toInt())
                background = surface(); elevation = dp(4).toFloat(); setOnClickListener { action() }
            }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { bottomMargin = dp(8) })
        }
        control("◎", "A minha localização") { followLocation = true; locate() }
        control("+", "Aproximar mapa") { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
        control("−", "Afastar mapa") { map?.animateCamera(CameraUpdateFactory.zoomOut()) }
        control("↻", "Recarregar mapa") { loadStyle() }
        addView(controls, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(12) })
        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; background = surface(); elevation = dp(5).toFloat()
            setPadding(dp(18), dp(12), dp(18), dp(12))
            addView(TextView(context).apply { text = "Explorar · MyNaviScooter"; textSize = 18f; setTextColor(0xFF142537.toInt()) })
            addView(message)
            addView(TextView(context).apply { text = "Pesquisa um destino ou mantém premido um ponto do mapa."; textSize = 12f; setTextColor(0xFF62758A.toInt()) })
        }
        addView(bottom, LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(48) })
    }
    private fun loadStyle() {
        styleReady = false; marker = null; destination = null
        map?.setStyle(STYLE) {
            styleReady = true
            message.text = "Mapa online · OpenFreeMap"
            lastLocation?.let { onLocationChanged(it) }
        }
    }
    private fun setDestination(point: LatLng, name: String) {
        val loaded = map ?: return
        if (!styleReady) { message.text = "Aguarda o carregamento do mapa."; return }
        followLocation = false
        destination?.let { loaded.removeMarker(it) }
        destination = loaded.addMarker(MarkerOptions().position(point).title(name))
        loaded.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0))
        message.text = "Destino: $name · orientação por voz ainda em desenvolvimento."
    }
    @Suppress("DEPRECATION")
    private fun searchPlace(query: String) {
        if (query.isBlank()) return
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
        if (!Geocoder.isPresent()) { message.text = "Pesquisa indisponível neste telemóvel. Seleciona um ponto no mapa."; return }
        message.text = "A pesquisar…"
        worker.execute {
            val results = runCatching { Geocoder(context, Locale.getDefault()).getFromLocationName(query, 5).orEmpty() }
            post {
                if (!destroyed) results.onSuccess { addresses ->
                    if (addresses.isEmpty()) message.text = "Local não encontrado. Inclui a cidade na pesquisa."
                    else androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle("Escolher destino")
                        .setItems(addresses.map { it.getAddressLine(0) ?: query }.toTypedArray()) { _, index ->
                            val place = addresses[index]
                            setDestination(LatLng(place.latitude, place.longitude), place.getAddressLine(0) ?: query)
                        }.setNegativeButton("Cancelar", null).show()
                }.onFailure { message.text = "Pesquisa indisponível. Verifica a internet e tenta novamente." }
            }
        }
    }
    fun show(visible: Boolean) {
        active = visible
        if (visible && mapView == null) {
            MapLibre.getInstance(context)
            mapView = MapView(context).also { view ->
                addView(view, 0, LayoutParams(-1, -1))
                view.onCreate(null)
                view.addOnDidFailLoadingMapListener { message.text = "Não foi possível carregar. Verifica a internet e toca em Recarregar mapa." }
                view.setOnTouchListener { v, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) followLocation = false
                    v.parent.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
                    false
                }
                view.getMapAsync { loaded ->
                    map = loaded
                    loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(50.0, 10.0), 4.0))
                    loaded.addOnMapLongClickListener { point -> setDestination(point, "Ponto selecionado"); true }
                    loadStyle()
                }
            }
        }
        if (visible && !started) { mapView?.onStart(); mapView?.onResume(); started = true }
        if (!visible && started) { mapView?.onPause(); mapView?.onStop(); started = false }
        if (!visible) locationManager?.removeUpdates(this)
        else if (locating) locate()
    }
    @Suppress("MissingPermission")
    fun locate() {
        val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permitted) { requestLocation(); return }
        locating = true
        if (!active) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { locationManager?.isProviderEnabled(it) == true }
        if (providers.isEmpty()) { message.text = "Ativa a localização nas definições do telemóvel."; return }
        message.text = "A procurar a tua localização…"
        locationManager?.removeUpdates(this)
        providers.forEach { provider ->
            runCatching {
                locationManager?.getLastKnownLocation(provider)?.takeIf { System.currentTimeMillis() - it.time < 120000 }?.let { onLocationChanged(it) }
                locationManager?.requestLocationUpdates(provider, 2000L, 5f, this, Looper.getMainLooper())
            }.onFailure { message.text = "Localização indisponível. Verifica as permissões." }
        }
    }
    override fun onLocationChanged(location: Location) {
        lastLocation = location
        val loaded = map ?: return
        if (!styleReady) return
        val point = LatLng(location.latitude, location.longitude)
        marker?.let { loaded.removeMarker(it) }
        marker = loaded.addMarker(MarkerOptions().position(point).title("A tua localização"))
        if (followLocation) {
            loaded.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0))
            message.text = "Localização · precisão aproximada ${location.accuracy.toInt()} m"
        }
    }
    fun destroy() { destroyed = true; worker.shutdownNow(); show(false); locationManager?.removeUpdates(this); mapView?.onDestroy(); mapView = null; map = null }
    companion object { const val STYLE = "https://tiles.openfreemap.org/styles/liberty" }
}
