package tr.oynat

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Eklenti (provider) arayüzü. Her kaynak arama yapar ve seçilen sonucu
 * oynatılabilir bir bağlantıya çevirir. Yeni bir kaynak eklemek için
 * bu arayüzü uygulayıp [Providers.all] içine eklemek yeterli.
 *
 * Not: Bu arayüzler ağ çağrısı yapar, ana iş parçacığından çağrılmamalı.
 */
interface Provider {
    val name: String
    fun search(query: String): List<Entry>
    /** Arama sonucunu oynatılabilir hale getirir (gerçek video + altyazı bağlantısı). */
    fun resolve(e: Entry): Entry = e
}

object Http {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", "Oynat/1.0")
        try {
            if (c.responseCode !in 200..299) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

/** Internet Archive: kamu malı ve serbest lisanslı film/videolar. */
class ArchiveProvider : Provider {
    override val name = "Internet Archive"

    override fun search(query: String): List<Entry> {
        val q = if (query.isBlank()) "mediatype:movies" else "($query) AND mediatype:movies"
        val url = "https://archive.org/advancedsearch.php?q=" + URLEncoder.encode(q, "UTF-8") +
            "&fl%5B%5D=identifier&fl%5B%5D=title&rows=40&sort%5B%5D=downloads+desc&output=json"
        val docs = JSONObject(Http.get(url)).getJSONObject("response").getJSONArray("docs")
        return (0 until docs.length()).map { i ->
            val d = docs.getJSONObject(i)
            val id = d.getString("identifier")
            Entry(
                id = "a$id",
                title = d.optString("title", id),
                uri = "archive:$id",
                kind = Kind.SEARCH,
                info = name
            )
        }
    }

    override fun resolve(e: Entry): Entry {
        val id = e.uri.removePrefix("archive:")
        val files = JSONObject(Http.get("https://archive.org/metadata/$id")).optJSONArray("files")
            ?: throw RuntimeException("Dosya listesi yok")

        fun list(): List<JSONObject> = (0 until files.length()).map { files.getJSONObject(it) }
        fun ext(f: JSONObject) = f.optString("name").substringAfterLast('.', "").lowercase()

        val video = list().filter { ext(it) in setOf("mp4", "m4v", "webm", "mkv") }
            .sortedWith(
                compareBy<JSONObject> { if (ext(it) == "mp4") 0 else 1 }
                    .thenBy { it.optString("format").contains("h.264", true).not() }
                    // çok büyük orijinaller yerine makul boyuttakini seç
                    .thenBy { it.optString("size", "0").toLongOrNull() ?: Long.MAX_VALUE }
            ).firstOrNull() ?: throw RuntimeException("Oynatılabilir video bulunamadı")

        val sub = list().firstOrNull { ext(it) in setOf("srt", "vtt") }

        fun link(f: JSONObject) = "https://archive.org/download/$id/" + Uri.encode(f.getString("name"), "/")
        return e.copy(uri = link(video), subtitle = sub?.let(::link))
    }
}

/**
 * Basit JSON katalog kaynağı. Herkes kendi kataloğunu yayınlayabilir:
 * {"name":"Katalogum","items":[{"title":"Film","url":"https://.../film.mp4","subtitle":"https://.../film.srt"}]}
 */
class JsonCatalogProvider(private val catalogUrl: String) : Provider {
    override var name: String = Uri.parse(catalogUrl).host ?: "Katalog"
        private set

    override fun search(query: String): List<Entry> {
        val root = JSONObject(Http.get(catalogUrl))
        root.optString("name").takeIf { it.isNotBlank() }?.let { name = it }
        val items: JSONArray = root.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { items.getJSONObject(it) }
            .filter { query.isBlank() || it.optString("title").contains(query, ignoreCase = true) }
            .mapIndexed { i, o ->
                Entry(
                    id = "j${catalogUrl.hashCode()}_$i",
                    title = o.optString("title", "Adsız"),
                    uri = o.getString("url"),
                    subtitle = o.optString("subtitle").ifBlank { null },
                    kind = Kind.SEARCH,
                    info = name
                )
            }
    }
}

object Providers {
    fun all(store: Store): List<Provider> =
        listOf<Provider>(ArchiveProvider()) + store.sources().map { JsonCatalogProvider(it) }

    fun resolverFor(store: Store, e: Entry): Provider? =
        if (e.uri.startsWith("archive:")) ArchiveProvider() else null
}
