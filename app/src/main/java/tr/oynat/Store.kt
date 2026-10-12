package tr.oynat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Kind { LINK, DEVICE, DOWNLOAD, SEARCH }

data class Entry(
    val id: String,
    val title: String,
    val uri: String,
    val subtitle: String? = null,
    val kind: Kind,
    val info: String = ""
)

/** Bağlantı listesini ve kaldığın yerleri saklar. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("oynat", Context.MODE_PRIVATE)

    fun links(): MutableList<Entry> {
        val arr = JSONArray(prefs.getString("links", "[]"))
        val out = mutableListOf<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += Entry(
                id = o.getString("id"),
                title = o.getString("title"),
                uri = o.getString("uri"),
                subtitle = o.optString("subtitle").ifBlank { null },
                kind = Kind.LINK
            )
        }
        return out
    }

    fun saveLinks(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("uri", it.uri)
                put("subtitle", it.subtitle ?: "")
            })
        }
        prefs.edit().putString("links", arr.toString()).apply()
    }

    fun sources(): List<String> {
        val arr = JSONArray(prefs.getString("sources", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun saveSources(list: List<String>) {
        prefs.edit().putString("sources", JSONArray(list).toString()).apply()
    }

    fun position(uri: String): Long = prefs.getLong("pos:$uri", 0L)

    fun savePosition(uri: String, positionMs: Long) {
        prefs.edit().putLong("pos:$uri", positionMs).apply()
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
