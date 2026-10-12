package tr.oynat

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var store: Store
    private var uri = ""
    private var title: String? = null
    private var subtitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player_view)
        store = Store(this)

        // Uygulama içinden ya da "Birlikte aç" ile gelen video
        uri = intent.getStringExtra(EXTRA_URI) ?: intent.data?.toString() ?: run {
            finish(); return
        }
        title = intent.getStringExtra(EXTRA_TITLE) ?: intent.data?.lastPathSegment
        subtitle = intent.getStringExtra(EXTRA_SUBTITLE)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initPlayer() {
        if (player != null) return
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())

        val lower = uri.lowercase()
        when {
            ".m3u8" in lower -> item.setMimeType(MimeTypes.APPLICATION_M3U8)
            ".mpd" in lower -> item.setMimeType(MimeTypes.APPLICATION_MPD)
        }

        subtitle?.let { s ->
            item.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(s))
                        .setMimeType(subtitleMime(s))
                        .setLanguage("tr")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
        }

        val p = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        // Türkçe ses ve altyazı varsa onları tercih et
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage("tr")
            .setPreferredTextLanguage("tr")
            .build()

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Oynatılamadı: ${error.errorCodeName}",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    store.savePosition(uri, 0)
                    finish()
                }
            }
        })

        playerView.player = p
        p.setMediaItem(item.build())
        val resume = store.position(uri)
        if (resume > 0) p.seekTo(resume)
        p.playWhenReady = true
        p.prepare()
        player = p
        playerView.requestFocus()
    }

    private fun releasePlayer() {
        player?.let {
            if (it.playbackState != Player.STATE_ENDED) {
                store.savePosition(uri, it.currentPosition)
            }
            it.release()
        }
        player = null
        playerView.player = null
    }

    private fun subtitleMime(s: String): String {
        val l = s.lowercase()
        return when {
            l.contains(".vtt") -> MimeTypes.TEXT_VTT
            l.contains(".ass") || l.contains(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // TV kumandası: tuşlar oynatıcı kontrollerine gitsin
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
    }
}
