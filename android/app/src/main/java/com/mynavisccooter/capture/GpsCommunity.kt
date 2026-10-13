package com.mynavisccooter.capture

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The APK downloads public, reviewed data only. It contains no GitHub credentials. */
class GpsCommunity(context: Context) {
    val library = GpsLibrary(context, "gps_community_v1")
    private val prefs = context.getSharedPreferences("gps_community_sync_v1", Context.MODE_PRIVATE)
    fun shouldRefresh(force: Boolean): Boolean {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("attempt", 0) < (if (force) 30000L else 300000L)) return false
        return force || now - prefs.getLong("success", 0) >= 6 * 3600000L
    }
    fun sync(): Int {
        prefs.edit().putLong("attempt", System.currentTimeMillis()).apply()
        val connection = URL(CATALOG_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000; connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "MyNaviScooter/${BuildConfig.VERSION_NAME} (https://github.com/Rjcferreira/MyNaviScooter)")
        prefs.getString("etag", null)?.let { connection.setRequestProperty("If-None-Match", it) }
        try {
            if (connection.responseCode == 304) { prefs.edit().putLong("success", System.currentTimeMillis()).apply(); return count() }
            check(connection.responseCode == 200) { "Catálogo GitHub indisponível. A cópia local foi mantida." }
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (out.size() <= 2_000_000) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                out.toByteArray()
            }
            require(bytes.size <= 2_000_000) { "Catálogo demasiado grande." }
            val text = bytes.toString(Charsets.UTF_8); validateCatalog(text)
            library.importJson(text, replace = true)
            prefs.edit().putLong("success", System.currentTimeMillis()).putString("etag", connection.getHeaderField("ETag")).apply()
            return count()
        } finally { connection.disconnect() }
    }
    fun count() = library.places().size + library.hazards().size + library.routes().size
    companion object {
        const val CATALOG_URL = "https://raw.githubusercontent.com/Rjcferreira/MyNaviScooter/main/community/catalog.json"
        const val ISSUES_URL = "https://github.com/Rjcferreira/MyNaviScooter/issues/new"
        fun validateCatalog(text: String) {
            val root = JSONObject(text)
            require(root.optString("schema") == "mynavi-gps-community/v1" && root.optString("publication") == "moderated") { "Catálogo incompatível." }
            for (key in listOf("places", "hazards", "routes")) {
                val items = root.getJSONArray(key)
                for (index in 0 until items.length()) require(items.getJSONObject(index).getString("id").startsWith("github:")) { "Identificador comunitário inválido." }
            }
        }
    }
}
