package com.mynavisccooter.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
class OnlineMapPanel(context: Context, private val requestLocation: () -> Unit) : LinearLayout(context), LocationListener {
    private val message = TextView(context).apply {
        text = "Mapa online · abre esta página para carregar."
        setTextColor(0xFF99B1C5.toInt())
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
        orientation = VERTICAL
        addView(message)
        addView(Button(context).apply {
            text = "A minha localização"
            isAllCaps = false
            setOnClickListener { locate() }
        })
        addView(Button(context).apply {
            text = "Recarregar mapa"
            isAllCaps = false
            setOnClickListener { map?.setStyle(STYLE) }
        })
    }
    fun show(visible: Boolean) {
        active = visible
        if (visible && mapView == null) {
            MapLibre.getInstance(context)
            mapView = MapView(context).also { view ->
                addView(view, LayoutParams(-1, (360 * resources.displayMetrics.density).toInt()))
                view.onCreate(null)
                view.addOnDidFailLoadingMapListener { message.text = "Não foi possível carregar. Verifica a internet e toca em Recarregar mapa." }
                view.setOnTouchListener { v, event ->
                    v.parent.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
                    false
                }
                view.getMapAsync { loaded ->
                    map = loaded
                    loaded.setStyle(STYLE) {
                        message.text = "Mapa online · OpenFreeMap · usa dados móveis ou Wi-Fi."
                        loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(50.0, 10.0), 4.0))
                        lastLocation?.let { onLocationChanged(it) }
                    }
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
        val point = LatLng(location.latitude, location.longitude)
        marker?.let { loaded.removeMarker(it) }
        marker = loaded.addMarker(MarkerOptions().position(point).title("A tua localização"))
        loaded.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0))
        message.text = "Localização · precisão aproximada ${location.accuracy.toInt()} m"
    }
    fun destroy() { show(false); locationManager?.removeUpdates(this); mapView?.onDestroy(); mapView = null; map = null }
    companion object { const val STYLE = "https://tiles.openfreemap.org/styles/liberty" }
}
