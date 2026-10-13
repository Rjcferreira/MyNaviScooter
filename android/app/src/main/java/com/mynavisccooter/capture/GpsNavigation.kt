package com.mynavisccooter.capture

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

data class GpsPoint(val latitude: Double, val longitude: Double) {
    fun valid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}
data class GpsStep(val point: GpsPoint, val startsAt: Double, val instruction: String)
data class GpsRoute(val points: List<GpsPoint>, val distance: Double, val duration: Double, val steps: List<GpsStep>)
data class GpsProgress(val offRouteMeters: Double, val remainingMeters: Double, val seconds: Double,
    val nextInstruction: String, val nextMeters: Double, val nextIndex: Int, val arrived: Boolean)

object GpsGeometry {
    fun distance(a: GpsPoint, b: GpsPoint): Double {
        val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
        val dl = Math.toRadians(b.longitude - a.longitude)
        val h = sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dl / 2).pow(2)
        return 6371000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun progress(route: GpsRoute, position: GpsPoint): GpsProgress {
        require(route.points.size >= 2)
        var total = 0.0; var travelled = 0.0; var best = Double.MAX_VALUE
        val scale = cos(Math.toRadians(position.latitude)).coerceAtLeast(.01)
        route.points.zipWithNext().forEach { (a, b) ->
            val ax = (a.longitude - position.longitude) * scale; val ay = a.latitude - position.latitude
            val dx = (b.longitude - a.longitude) * scale; val dy = b.latitude - a.latitude
            val squared = dx * dx + dy * dy
            val t = if (squared < 1e-18) 0.0 else (-(ax * dx + ay * dy) / squared).coerceIn(0.0, 1.0)
            val projected = GpsPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
            val away = distance(position, projected); val segment = distance(a, b)
            if (away < best) { best = away; travelled = total + segment * t }
            total += segment
        }
        val fraction = if (total > 0) (travelled / total).coerceIn(0.0, 1.0) else 0.0
        val done = route.distance * fraction
        val next = route.steps.indexOfFirst { it.startsAt >= done + 10 }.let { if (it < 0) route.steps.lastIndex else it }
        val step = route.steps.getOrNull(next)
        val remaining = route.distance * (1 - fraction)
        val arrived = remaining < 35 && distance(position, route.points.last()) < 35
        return GpsProgress(best, remaining, route.duration * (1 - fraction),
            step?.instruction ?: "Segue o percurso", max(0.0, (step?.startsAt ?: route.distance) - done), next, arrived)
    }
}

object GpsRouting {
    const val PROVIDER = "https://routing.openstreetmap.de/routed-bike"
    fun request(start: GpsPoint, destination: GpsPoint): GpsRoute {
        require(start.valid() && destination.valid())
        val coordinates = "${start.longitude},${start.latitude};${destination.longitude},${destination.latitude}"
        val connection = URL("$PROVIDER/route/v1/driving/$coordinates?steps=true&overview=full&geometries=geojson&alternatives=false").openConnection() as HttpURLConnection
        connection.connectTimeout = 15000; connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "MyNaviScooter/${BuildConfig.VERSION_NAME} (https://github.com/Rjcferreira/MyNaviScooter)")
        try {
            check(connection.responseCode == 200) { "Servidor de percursos indisponível (${connection.responseCode})." }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (output.size() <= 2_000_000) {
                    val count = input.read(buffer); if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            check(bytes.size <= 2_000_000) { "Percurso demasiado grande." }
            return parse(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    fun parse(json: String): GpsRoute {
        val root = JSONObject(json)
        check(root.optString("code") == "Ok") { "Não foi encontrado um percurso ciclável." }
        val route = root.getJSONArray("routes").getJSONObject(0)
        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coordinates.length()).map { index ->
            val pair = coordinates.getJSONArray(index); GpsPoint(pair.getDouble(1), pair.getDouble(0))
        }
        check(points.size in 2..50000 && points.all { it.valid() }) { "Geometria de percurso inválida." }
        val steps = mutableListOf<GpsStep>(); var along = 0.0
        val legs = route.getJSONArray("legs")
        for (leg in 0 until legs.length()) {
            val items = legs.getJSONObject(leg).getJSONArray("steps")
            for (index in 0 until items.length()) {
                val step = items.getJSONObject(index); val maneuver = step.getJSONObject("maneuver")
                val pair = maneuver.getJSONArray("location")
                steps += GpsStep(GpsPoint(pair.getDouble(1), pair.getDouble(0)), along,
                    instruction(maneuver.optString("type"), maneuver.optString("modifier"), step.optString("name"), maneuver.optInt("exit", 0)))
                along += step.optDouble("distance", 0.0)
            }
        }
        val meters = route.getDouble("distance"); val seconds = route.getDouble("duration")
        check(meters.isFinite() && seconds.isFinite() && meters >= 0 && seconds >= 0)
        return GpsRoute(points, meters, seconds, steps)
    }
    fun instruction(type: String, modifier: String, street: String, exit: Int = 0): String {
        val verb = when {
            type == "arrive" -> "Chegaste ao destino"
            type == "depart" -> "Segue em frente"
            type.contains("roundabout") || type == "rotary" -> if (exit > 0) "Na rotunda, toma a saída $exit" else "Entra na rotunda"
            modifier == "uturn" -> "Inverte o sentido"
            modifier.contains("left") -> "Vira à esquerda"
            modifier.contains("right") -> "Vira à direita"
            else -> "Continua em frente"
        }
        return if (street.isBlank() || type == "arrive") verb else "$verb · $street"
    }
    fun meters(value: Double) = if (value < 1000) "${(value / 10).roundToInt() * 10} m" else String.format(Locale.getDefault(), "%.1f km", value / 1000)
    fun minutes(seconds: Double) = "${ceil(seconds / 60).toInt().coerceAtLeast(1)} min"
}
