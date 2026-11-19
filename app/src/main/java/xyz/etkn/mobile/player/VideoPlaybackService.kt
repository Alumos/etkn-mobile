package xyz.etkn.mobile.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 后台播放: a MediaSession around the video player while the setting is on, so leaving the app
 * keeps the sound going with the system media notification (and lock-screen / headset controls)
 * instead of pausing. Started while the player is in front (a service may not be started from the
 * background); the activity hands over a new core when the player switches engines.
 */
class VideoPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val player = pending ?: return stopSelf()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setId("video").setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away ends video playback, unlike music.
        session?.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private var pending: Player? = null
        private var instance: VideoPlaybackService? = null

        /** Starts the service around [player], or moves a running session to it (engine switch). */
        fun attach(context: Context, player: Player) {
            pending = player
            val running = instance?.session
            if (running != null) {
                if (running.player !== player) running.player = player
            } else {
                runCatching { context.startService(Intent(context, VideoPlaybackService::class.java)) }
            }
        }

        fun detach(context: Context) {
            pending = null
            instance?.session?.let { it.release(); instance?.session = null }
            runCatching { context.stopService(Intent(context, VideoPlaybackService::class.java)) }
        }
    }
}
