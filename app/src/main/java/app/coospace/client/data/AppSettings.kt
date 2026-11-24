package app.coospace.client.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { System, Light, Dark }

data class SavedPage(val title: String, val url: String) {
    fun toJson(): JSONObject = JSONObject().put("t", title).put("u", url)

    companion object {
        fun fromJson(o: JSONObject) = SavedPage(o.optString("t"), o.optString("u"))
    }
}

/** Small SharedPreferences-backed store exposed as Compose state. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.System)
    )
        private set
    var dynamicColor by mutableStateOf(prefs.getBoolean("dynamic", true))
        private set
    var restylePages by mutableStateOf(prefs.getBoolean("restyle", true))
        private set
    var textZoom by mutableStateOf(prefs.getInt("textZoom", 100))
        private set
    var baseUrl by mutableStateOf(
        // Earlier builds defaulted to other hosts; move those installs to the current CooSpace 5 address.
        prefs.getString("baseUrl", null)?.takeUnless { url -> OLD_DEFAULTS.any { it in url } } ?: DEFAULT_BASE_URL
    )
        private set

    /** Best guess from the last CooSpace page we loaded; see CooWebController.detectSession. */
    var signedIn by mutableStateOf(prefs.getBoolean("signedIn", false))
        private set

    val pinned = mutableStateListOf<SavedPage>().apply { addAll(readList("pinned")) }
    val recent = mutableStateListOf<SavedPage>().apply { addAll(readList("recent")) }

    fun updateSignedIn(v: Boolean) {
        if (v == signedIn) return
        signedIn = v; prefs.edit().putBoolean("signedIn", v).apply()
    }
    fun updateThemeMode(v: ThemeMode) { themeMode = v; prefs.edit().putString("theme", v.name).apply() }
    fun updateDynamicColor(v: Boolean) { dynamicColor = v; prefs.edit().putBoolean("dynamic", v).apply() }
    fun updateRestylePages(v: Boolean) { restylePages = v; prefs.edit().putBoolean("restyle", v).apply() }
    fun updateTextZoom(v: Int) { textZoom = v; prefs.edit().putInt("textZoom", v).apply() }
    fun updateBaseUrl(v: String) {
        val clean = v.trim().let { if (it.startsWith("http")) it else "https://$it" }.let { if (it.endsWith("/")) it else "$it/" }
        baseUrl = clean; prefs.edit().putString("baseUrl", clean).apply()
    }

    fun isPinned(url: String?) = url != null && pinned.any { it.url == url }

    fun togglePin(page: SavedPage): Boolean {
        val existing = pinned.indexOfFirst { it.url == page.url }
        val nowPinned = if (existing >= 0) { pinned.removeAt(existing); false } else { pinned.add(page); true }
        writeList("pinned", pinned)
        return nowPinned
    }

    fun unpin(page: SavedPage) { pinned.removeAll { it.url == page.url }; writeList("pinned", pinned) }

    fun addRecent(page: SavedPage) {
        if (page.title.isBlank()) return
        recent.removeAll { it.url == page.url }
        recent.add(0, page)
        while (recent.size > MAX_RECENT) recent.removeAt(recent.lastIndex)
        writeList("recent", recent)
    }

    fun clearRecent() { recent.clear(); writeList("recent", recent) }

    private fun readList(key: String): List<SavedPage> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        List(arr.length()) { SavedPage.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun writeList(key: String, list: List<SavedPage>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://unics.szte.hu/"
        private const val MAX_RECENT = 12
        private val OLD_DEFAULTS = listOf("coospace.szte.hu", "coosp.etr.u-szeged.hu")
    }
}
