package xyz.etkn.mobile.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.content.Context
import android.os.Bundle
import xyz.etkn.mobile.data.settings.UiScale
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.VideoSize
import xyz.etkn.mobile.data.settings.Settings
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.ui.components.LocalImages
import xyz.etkn.mobile.ui.theme.EtknTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

class PlayerActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        UiScale.applyTo(this, newBase)
    }

    private var session: VideoSession? = null
    private var inPip by mutableStateOf(false)
    private var wasPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()

        val active = graph.sessions.current
        if (active == null) {
            finish()
            return
        }
        val itemId = intent.getStringExtra(PlayerLauncher.EXTRA_ITEM)
        if (itemId == null) {
            finish()
            return
        }
        graph.music.pause()
        val video = VideoSession(this, graph, lifecycleScope)
        session = video
        video.start(
            itemId = itemId,
            type = intent.getStringExtra(PlayerLauncher.EXTRA_TYPE),
            seriesId = intent.getStringExtra(PlayerLauncher.EXTRA_SERIES),
            seasonId = intent.getStringExtra(PlayerLauncher.EXTRA_SEASON),
            startMs = intent.getLongExtra(PlayerLauncher.EXTRA_START_MS, 0L),
            resume = intent.getBooleanExtra(PlayerLauncher.EXTRA_RESUME, true),
            sourceId = intent.getStringExtra(PlayerLauncher.EXTRA_SOURCE),
        )
        val urls = graph.images(active)
        updatePipParams()
        setContent {
            val settings by graph.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
            EtknTheme(settings, forceDark = true) {
                CompositionLocalProvider(LocalImages provides urls) {
                    PlayerScreen(
                        session = video,
                        inPip = inPip,
                        onBack = { finish() },
                        onPip = { enterPip() },
                    )
                }
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val size: VideoSize = session?.player?.videoSize ?: VideoSize.UNKNOWN
        val ratio = if (size.width > 0 && size.height > 0) {
            val r = size.width.toFloat() / size.height
            Rational(size.width, size.height).takeIf { r in 0.42f..2.38f } ?: Rational(16, 9)
        } else Rational(16, 9)
        val builder = PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(session?.player?.isPlaying == true)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) pipParams()?.let { setPictureInPictureParams(it) }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) pipParams()?.let { enterPictureInPictureMode(it) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S && session?.player?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onStart() {
        super.onStart()
        if (wasPlaying) session?.player?.play()
    }

    override fun onPause() {
        super.onPause()
        // Release now, not in onDestroy: that can run after the next player activity has already
        // started its own core, and libmpv allows one instance per process.
        if (isFinishing) {
            session?.release()
            session = null
        }
    }

    override fun onStop() {
        super.onStop()
        val player = session?.player ?: return
        wasPlaying = player.isPlaying
        // Leaving PiP (or the app) stops playback; the progress report goes out on pause.
        player.pause()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
