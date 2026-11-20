package xyz.etkn.mobile.player.mpv

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * mpv's counterpart of ExoPlayer's `WAKE_MODE_NETWORK`: CPU and Wi-Fi stay awake only while the
 * video plays, so 后台播放 with the screen off keeps streaming, and nothing is held while paused.
 */
internal class PlaybackLocks(context: Context) {
    private val wake = context.getSystemService(PowerManager::class.java)
        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "etkn:mpv")?.apply { setReferenceCounted(false) }

    @Suppress("DEPRECATION") // The mode ExoPlayer uses; LOW_LATENCY only applies while the app is in front.
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "etkn:mpv")?.apply { setReferenceCounted(false) }

    private var held = false

    fun hold(playing: Boolean) {
        if (playing == held) return
        held = playing
        runCatching {
            if (playing) {
                wake?.acquire(MAX_HOLD_MS)
                wifi?.acquire()
            } else {
                wake?.release()
                wifi?.release()
            }
        }
    }

    private companion object {
        /** Safety net: the lock lapses on its own even if a release were ever missed. */
        const val MAX_HOLD_MS = 4 * 60 * 60 * 1000L
    }
}
