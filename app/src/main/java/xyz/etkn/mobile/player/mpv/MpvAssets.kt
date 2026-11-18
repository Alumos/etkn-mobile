package xyz.etkn.mobile.player.mpv

import android.content.Context
import android.system.Os
import android.util.Log
import `is`.xyz.mpv.Utils
import java.io.File

/**
 * Files libmpv reads from its config directory: the CA bundle for HTTPS and a CJK subtitle font.
 * libass on Android has no system font lookup, so the system's CJK font is linked (not copied)
 * into a private fonts directory that holds nothing else. Idempotent; call off the main thread
 * when possible (the first call copies the AAR's assets).
 */
internal object MpvAssets {
    @Volatile private var ready = false
    var fontsDir: String? = null
        private set
    var fontFamily: String? = null
        private set

    private val CANDIDATES = listOf(
        "/system/fonts/NotoSansCJK-Regular.ttc" to "Noto Sans CJK SC",
        "/system/fonts/NotoSansSC-Regular.otf" to "Noto Sans SC",
        "/system/fonts/DroidSansFallbackFull.ttf" to "Droid Sans Fallback",
        "/system/fonts/DroidSansFallback.ttf" to "Droid Sans Fallback",
    )

    fun prepare(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val app = context.applicationContext
            runCatching { Utils.copyAssets(app) }.onFailure { Log.e(TAG, "mpv assets", it) }
            runCatching { linkFont(File(app.filesDir, "mpv-fonts")) }.onFailure { Log.e(TAG, "subtitle font", it) }
            ready = true
        }
    }

    private fun linkFont(dir: File) {
        val (path, family) = CANDIDATES.firstOrNull { File(it.first).canRead() }
            ?: File("/system/fonts").listFiles()?.firstOrNull { it.name.contains("CJK", ignoreCase = true) }?.let { it.path to null }
            ?: return
        dir.mkdirs()
        val source = File(path)
        val link = File(dir, source.name)
        dir.listFiles()?.filter { it.name != link.name }?.forEach { it.delete() }
        if (!link.exists()) Os.symlink(source.path, link.path)
        fontsDir = dir.path
        fontFamily = family
    }

    private const val TAG = "ETKN-Mpv"
}
