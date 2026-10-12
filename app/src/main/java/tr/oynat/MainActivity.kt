package tr.oynat

import android.Manifest
import android.app.DownloadManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: EntryAdapter
    private lateinit var empty: TextView
    private lateinit var addButton: MaterialButton
    private var tab = Kind.LINK
    private var askedPermission = false
    private lateinit var search: EditText
    private var results: List<Entry> = emptyList()

    private val videoPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) refresh()
            else showEmpty("Cihazdaki videoları görmek için izin gerekli.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store(this)
        empty = findViewById(R.id.empty)
        addButton = findViewById(R.id.add)
        search = findViewById(R.id.search)
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(); true } else false
        }

        adapter = EntryAdapter(::describe, ::play, ::showMenu)
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        addButton.setOnClickListener { if (tab == Kind.SEARCH) showSourceDialog() else showAddDialog() }

        findViewById<MaterialButtonToggleGroup>(R.id.tabs)
            .addOnButtonCheckedListener { _, id, checked ->
                if (!checked) return@addOnButtonCheckedListener
                tab = when (id) {
                    R.id.tabDiscover -> Kind.SEARCH
                    R.id.tabDevice -> { askedPermission = false; Kind.DEVICE }
                    R.id.tabDownloads -> Kind.DOWNLOAD
                    else -> Kind.LINK
                }
                refresh()
                if (tab == Kind.SEARCH && results.isEmpty()) runSearch()
            }
    }

    override fun onResume() {
        super.onResume()
        refresh() // kaldığın yerler ve biten indirmeler güncellensin
    }

    // ---------- Listeler ----------

    private fun refresh() {
        addButton.visibility =
            if (tab == Kind.LINK || tab == Kind.SEARCH) View.VISIBLE else View.GONE
        addButton.text = if (tab == Kind.SEARCH) "+ Kaynak ekle" else "+ Bağlantı ekle"
        search.visibility = if (tab == Kind.SEARCH) View.VISIBLE else View.GONE
        val items = when (tab) {
            Kind.LINK -> store.links()
            Kind.DEVICE -> {
                if (ContextCompat.checkSelfPermission(this, videoPermission)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    if (!askedPermission) {
                        askedPermission = true
                        permissionLauncher.launch(videoPermission)
                    }
                    emptyList()
                } else deviceVideos()
            }
            Kind.DOWNLOAD -> downloadedFiles()
            Kind.SEARCH -> results
        }
        adapter.items = items
        if (items.isEmpty()) showEmpty(
            when (tab) {
                Kind.LINK -> "Henüz bağlantı yok. \"+ Bağlantı ekle\" ile bir video bağlantısı ekle."
                Kind.DEVICE -> "Cihazda video bulunamadı."
                Kind.DOWNLOAD -> "İndirilen video yok. Bir bağlantıya uzun basıp \"İndir\"i seç."
                Kind.SEARCH -> "Sonuç yok. Yukarıya yazıp klavyedeki ara tuşuna bas."
            }
        ) else empty.visibility = View.GONE
    }

    private fun showEmpty(msg: String) {
        empty.text = msg
        empty.visibility = View.VISIBLE
    }

    private fun deviceVideos(): List<Entry> {
        val out = mutableListOf<Entry>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE
        )
        contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out += Entry(
                    id = "d$id",
                    title = c.getString(nameCol) ?: "Video",
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    kind = Kind.DEVICE,
                    info = sizeText(c.getLong(sizeCol))
                )
            }
        }
        return out
    }

    private fun downloadDir(): File? = getExternalFilesDir(Environment.DIRECTORY_MOVIES)

    private fun downloadedFiles(): List<Entry> {
        val files = downloadDir()?.listFiles().orEmpty()
            .filter { it.isFile && it.length() > 0 && it.extension.lowercase() !in SUB_EXTS }
            .sortedByDescending { it.lastModified() }
        return files.map { f ->
            val sub = SUB_EXTS.map { File(f.parentFile, f.nameWithoutExtension + "." + it) }
                .firstOrNull { it.exists() }
            Entry(
                id = "f" + f.name,
                title = f.nameWithoutExtension,
                uri = Uri.fromFile(f).toString(),
                subtitle = sub?.let { Uri.fromFile(it).toString() },
                kind = Kind.DOWNLOAD,
                info = sizeText(f.length())
            )
        }
    }

    private fun describe(e: Entry): String {
        val parts = mutableListOf<String>()
        when (e.kind) {
            Kind.LINK -> parts += Uri.parse(e.uri).host ?: "bağlantı"
            else -> if (e.info.isNotEmpty()) parts += e.info
        }
        if (e.subtitle != null) parts += "altyazılı"
        val pos = store.position(e.uri)
        if (pos > 5_000) parts += "kaldığın yer ${formatTime(pos)}"
        return parts.joinToString(" · ")
    }

    // ---------- Eylemler ----------

    private fun play(e: Entry) {
        if (e.kind == Kind.SEARCH) {
            resolveThen(e) { play(it.copy(kind = Kind.LINK)) }
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, e.uri)
            putExtra(PlayerActivity.EXTRA_TITLE, e.title)
            putExtra(PlayerActivity.EXTRA_SUBTITLE, e.subtitle)
        })
    }

    private fun showMenu(e: Entry) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += "Oynat" to { play(e) }
        if (store.position(e.uri) > 0) actions += "Baştan oynat" to {
            store.savePosition(e.uri, 0); play(e)
        }
        when (e.kind) {
            Kind.LINK -> {
                actions += "İndir" to { download(e) }
                actions += "Sil" to { deleteLink(e) }
            }
            Kind.DOWNLOAD -> actions += "Dosyayı sil" to { deleteFile(e) }
            Kind.DEVICE -> Unit
            Kind.SEARCH -> {
                actions += "İndir" to { resolveThen(e) { download(it) } }
                actions += "Listeme ekle" to {
                    resolveThen(e) { r ->
                        val list = store.links()
                        list.add(0, Entry(UUID.randomUUID().toString(), r.title, r.uri, r.subtitle, Kind.LINK))
                        store.saveLinks(list)
                        toast("Bağlantılar listene eklendi.")
                    }
                }
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(e.title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .show()
    }

    private fun showAddDialog() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val title = EditText(this).apply { hint = "Başlık" }
        val url = EditText(this).apply { hint = "Video bağlantısı (mp4, mkv, m3u8…)" }
        val sub = EditText(this).apply { hint = "Altyazı bağlantısı (isteğe bağlı .srt/.vtt)" }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(title); addView(url); addView(sub)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Bağlantı ekle")
            .setView(box)
            .setPositiveButton("Ekle") { _, _ ->
                val u = url.text.toString().trim()
                if (!u.startsWith("http://") && !u.startsWith("https://")) {
                    toast("Geçerli bir http/https bağlantısı gir.")
                    return@setPositiveButton
                }
                val t = title.text.toString().trim()
                    .ifEmpty { Uri.parse(u).lastPathSegment ?: "Video" }
                val list = store.links()
                list.add(0, Entry(UUID.randomUUID().toString(), t, u,
                    sub.text.toString().trim().ifEmpty { null }, Kind.LINK))
                store.saveLinks(list)
                refresh()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    // ---------- Eklentiler / kaynaklar ----------

    private fun runSearch() {
        val q = search.text.toString().trim()
        adapter.items = emptyList()
        showEmpty("Aranıyor…")
        val providers = Providers.all(store)
        Thread {
            val out = mutableListOf<Entry>()
            val errors = mutableListOf<String>()
            providers.forEach { p ->
                try { out += p.search(q) } catch (ex: Exception) { errors += "${p.name}: ${ex.message}" }
            }
            runOnUiThread {
                results = out
                if (tab == Kind.SEARCH) refresh()
                if (errors.isNotEmpty()) toast(errors.joinToString("\n"))
            }
        }.start()
    }

    private fun resolveThen(e: Entry, done: (Entry) -> Unit) {
        val provider = Providers.resolverFor(store, e)
        if (provider == null) { done(e); return }
        toast("Hazırlanıyor…")
        Thread {
            try {
                val r = provider.resolve(e)
                runOnUiThread { done(r) }
            } catch (ex: Exception) {
                runOnUiThread { toast("Açılamadı: ${ex.message}") }
            }
        }.start()
    }

    private fun showSourceDialog() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val url = EditText(this).apply { hint = "Katalog JSON bağlantısı (https://…)" }
        val box = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(url, LinearLayout.LayoutParams(-1, -2))
        }
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle("Kaynak ekle")
            .setMessage("Internet Archive yerleşik. Kendi kataloğunu eklemek için JSON bağlantısı gir:\n{\"name\":\"Katalog\",\"items\":[{\"title\":\"..\",\"url\":\"..\",\"subtitle\":\"..\"}]}")
            .setView(box)
            .setPositiveButton("Ekle") { _, _ ->
                val u = url.text.toString().trim()
                if (!u.startsWith("http://") && !u.startsWith("https://")) {
                    toast("Geçerli bir http/https bağlantısı gir.")
                } else {
                    store.saveSources(store.sources() + u)
                    runSearch()
                }
            }
            .setNegativeButton("Vazgeç", null)
        if (store.sources().isNotEmpty()) {
            dlg.setNeutralButton("Kaynakları sil") { _, _ ->
                val all = store.sources()
                MaterialAlertDialogBuilder(this)
                    .setTitle("Silmek için dokun")
                    .setItems(all.toTypedArray()) { _, i ->
                        store.saveSources(all.filterIndexed { idx, _ -> idx != i })
                        runSearch()
                    }
                    .show()
            }
        }
        dlg.show()
    }

    private fun deleteLink(e: Entry) {
        store.saveLinks(store.links().filter { it.id != e.id })
        refresh()
    }

    private fun deleteFile(e: Entry) {
        Uri.parse(e.uri).path?.let { File(it).delete() }
        e.subtitle?.let { s -> Uri.parse(s).path?.let { File(it).delete() } }
        refresh()
    }

    /** Doğrudan dosya bağlantılarını sistemin indirme yöneticisiyle indirir. */
    private fun download(e: Entry) {
        val lower = e.uri.lowercase()
        if (".m3u8" in lower || ".mpd" in lower) {
            toast("Akış bağlantıları (m3u8/mpd) indirilemez; yalnızca doğrudan video dosyaları.")
            return
        }
        val base = e.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80).ifBlank { "video" }
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        fun enqueue(url: String, fileName: String, title: String) {
            dm.enqueue(
                DownloadManager.Request(Uri.parse(url))
                    .setTitle(title)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalFilesDir(
                        this, Environment.DIRECTORY_MOVIES, fileName
                    )
            )
        }
        try {
            enqueue(e.uri, base + extensionOf(e.uri, VIDEO_EXTS, ".mp4"), e.title)
            e.subtitle?.let { enqueue(it, base + extensionOf(it, SUB_EXTS, ".srt"), "${e.title} (altyazı)") }
            toast("İndirme başladı. Bildirimden takip edebilirsin.")
        } catch (ex: Exception) {
            toast("İndirme başlatılamadı: ${ex.message}")
        }
    }

    private fun extensionOf(url: String, allowed: Set<String>, fallback: String): String {
        val ext = Uri.parse(url).lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return if (ext in allowed) ".$ext" else fallback
    }

    private fun sizeText(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
        else -> "${bytes / 1024} KB"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        val SUB_EXTS = setOf("srt", "vtt", "ass", "ssa")
        val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "3gp")
    }
}
