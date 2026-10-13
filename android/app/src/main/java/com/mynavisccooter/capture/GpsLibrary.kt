package com.mynavisccooter.capture

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class GpsPlace(val id: String, val name: String, val point: GpsPoint)
data class GpsHazard(val id: String, val kind: String, val point: GpsPoint, val note: String, val created: Long)
data class GpsSavedRoute(val id: String, val name: String, val points: List<GpsPoint>, val recommended: Boolean, val navigation: GpsRoute? = null)

/** Private on-device data; sharing/import is explicit, never an automatic public upload. */
class GpsLibrary(context: Context, preferencesName: String = "gps_library_v1") {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private var root = runCatching { JSONObject(prefs.getString("data", null) ?: "{}") }.getOrDefault(JSONObject())
    private var committed = root.toString()
    private fun array(key: String) = root.optJSONArray(key) ?: JSONArray()
    private fun point(json: JSONObject) = GpsPoint(json.getDouble("lat"), json.getDouble("lon")).also { require(it.valid()) }
    private fun pointJson(point: GpsPoint) = JSONObject().put("lat", point.latitude).put("lon", point.longitude)
    fun places(): List<GpsPlace> = (0 until array("places").length()).mapNotNull { index -> runCatching {
        val item = array("places").getJSONObject(index); GpsPlace(item.getString("id"), item.getString("name"), point(item))
    }.getOrNull() }
    fun hazards(): List<GpsHazard> = (0 until array("hazards").length()).mapNotNull { index -> runCatching {
        val item = array("hazards").getJSONObject(index)
        GpsHazard(item.getString("id"), item.getString("kind"), point(item), item.optString("note"), item.getLong("created"))
    }.getOrNull() }
    fun routes(): List<GpsSavedRoute> = (0 until array("routes").length()).mapNotNull { index -> runCatching {
        val item = array("routes").getJSONObject(index); val points = item.getJSONArray("points")
        val geometry = (0 until points.length()).map { point(points.getJSONObject(it)) }
        val steps = item.optJSONArray("steps")?.let { values -> (0 until values.length()).map { i ->
            val step = values.getJSONObject(i); GpsStep(point(step), step.getDouble("startsAt"), step.getString("instruction"))
        } }.orEmpty()
        val navigation = if (steps.isEmpty()) null else GpsRoute(geometry, item.getDouble("distance"), item.getDouble("duration"), steps)
        GpsSavedRoute(item.getString("id"), item.getString("name"), geometry, item.optBoolean("recommended"), navigation)
    }.getOrNull() }
    private fun save() {
        try {
            val text = root.toString()
            require(text.length <= 1_500_000) { "Biblioteca GPS cheia. Remove trajetos antigos antes de adicionar outros." }
            check(prefs.edit().putString("data", text).commit()) { "Não foi possível guardar os dados GPS." }
            committed = text
        } catch (error: Exception) { root = JSONObject(committed); throw error }
    }
    fun addPlace(name: String, point: GpsPoint) {
        val values = array("places"); require(values.length() < 200) { "Limite de 200 locais atingido." }
        require(point.valid() && name.isNotBlank())
        values.put(pointJson(point).put("id", UUID.randomUUID().toString()).put("name", name.take(120)))
        root.put("places", values); save()
    }
    fun addHazard(kind: String, point: GpsPoint, note: String) {
        val values = array("hazards"); require(values.length() < 500) { "Limite de 500 alertas atingido." }
        require(point.valid() && kind in KINDS)
        values.put(pointJson(point).put("id", UUID.randomUUID().toString()).put("kind", kind)
            .put("note", note.take(300)).put("created", System.currentTimeMillis()))
        root.put("hazards", values); save()
    }
    fun addRoute(name: String, points: List<GpsPoint>, recommended: Boolean, navigation: GpsRoute? = null) {
        val values = array("routes"); require(values.length() < 30) { "Limite de 30 trajetos atingido." }
        require(points.size in 2..10000 && points.all { it.valid() } && name.isNotBlank())
        val item = JSONObject().put("id", UUID.randomUUID().toString()).put("name", name.take(120))
            .put("recommended", recommended).put("points", JSONArray().apply { points.forEach { put(pointJson(it)) } })
        navigation?.takeIf { it.steps.isNotEmpty() }?.let { route ->
            item.put("distance", route.distance).put("duration", route.duration)
                .put("steps", JSONArray().apply { route.steps.forEach { put(pointJson(it.point).put("startsAt", it.startsAt).put("instruction", it.instruction)) } })
        }
        values.put(item)
        root.put("routes", values); save()
    }
    fun remove(key: String, id: String) {
        require(key in listOf("places", "hazards", "routes"))
        root.put(key, JSONArray().apply { val values = array(key); for (i in 0 until values.length()) {
            val item = values.getJSONObject(i); if (item.optString("id") != id) put(item)
        } }); save()
    }
    fun exportJson(): String = JSONObject(root.toString()).put("schema", "mynavi-gps-community/v1")
        .put("sharedAtUtc", CaptureReport.nowUtc()).toString(2)
    fun importJson(text: String, replace: Boolean = false): Int {
        require(text.length <= 2_000_000) { "Ficheiro demasiado grande." }
        val incoming = JSONObject(text)
        require(incoming.optString("schema") == "mynavi-gps-community/v1") { "Formato GPS incompatível." }
        val merged = if (replace) JSONObject() else JSONObject(root.toString()); var imported = 0
        for ((key, limit) in listOf("places" to 200, "hazards" to 500, "routes" to 30)) {
            val values = merged.optJSONArray(key) ?: JSONArray()
            val ids = (0 until values.length()).map { values.getJSONObject(it).getString("id") }.toMutableSet()
            val items = incoming.optJSONArray(key) ?: JSONArray()
            require(items.length() <= limit) { "Demasiados elementos no ficheiro." }
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i); val id = item.getString("id")
                require(id.length in 1..80)
                if (id in ids) continue
                require(values.length() < limit) { "A biblioteca GPS está cheia." }
                val cleaned = JSONObject().put("id", id)
                when (key) {
                    "places" -> {
                        val p = point(item); cleaned.put("lat", p.latitude).put("lon", p.longitude)
                            .put("name", item.getString("name").take(120))
                    }
                    "hazards" -> {
                        val p = point(item); require(item.getString("kind") in KINDS)
                        cleaned.put("lat", p.latitude).put("lon", p.longitude).put("kind", item.getString("kind"))
                            .put("note", item.optString("note").take(300)).put("created", item.getLong("created"))
                    }
                    "routes" -> {
                        val points = item.getJSONArray("points"); require(points.length() in 2..10000)
                        val filtered = JSONArray()
                        for (j in 0 until points.length()) filtered.put(pointJson(point(points.getJSONObject(j))))
                        cleaned.put("name", item.getString("name").take(120)).put("recommended", item.optBoolean("recommended"))
                            .put("points", filtered)
                        item.optJSONArray("steps")?.let { steps ->
                            require(steps.length() <= 2000)
                            val distance = item.getDouble("distance"); val duration = item.getDouble("duration")
                            require(distance.isFinite() && duration.isFinite() && distance >= 0 && duration >= 0)
                            val validated = JSONArray(); var previous = -1.0
                            for (j in 0 until steps.length()) {
                                val step = steps.getJSONObject(j); val along = step.getDouble("startsAt")
                                require(along.isFinite() && along >= previous && along <= distance + 10); previous = along
                                validated.put(pointJson(point(step)).put("startsAt", along).put("instruction", step.getString("instruction").take(400)))
                            }
                            cleaned.put("steps", validated).put("distance", distance).put("duration", duration)
                        }
                    }
                }
                values.put(cleaned); ids.add(id); imported++
            }
            merged.put(key, values)
        }
        val old = root; root = merged
        try { save() } catch (error: Exception) { root = old; throw error }
        return imported
    }
    companion object { val KINDS = listOf("Buraco", "Obstáculo", "Piso irregular", "Elevação", "Troço perigoso") }
}
