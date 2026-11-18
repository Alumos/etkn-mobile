package xyz.etkn.mobile.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Display
import xyz.etkn.mobile.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local diagnostic log the user can export from 我的 → 导出日志.
 *
 * The app's own logcat (this UID only; Media3 EventLogger, player and disc-reader lines, crashes)
 * is mirrored by a `logcat -f` child into a small rotating file in private storage, so the lines
 * leading up to a crash survive the restart. Java crashes are also written synchronously.
 * Nothing leaves the device unless the user exports it, and the export strips tokens and URL
 * query strings (signed links, `X-Emby-Token`, `UserId` …).
 */
object Diagnostics {
    private const val TAG = "ETKN"
    private const val LOG = "app.log"
    private const val CRASH = "crash.txt"
    private const val ROTATE_KB = "1024"
    private const val FILES = 3

    fun start(context: Context) {
        val dir = directory(context)
        runCatching {
            val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            ProcessBuilder("logcat", "-v", "threadtime", "-T", since, "-f", File(dir, LOG).path, "-r", ROTATE_KB, "-n", FILES.toString())
                .redirectErrorStream(true)
                .start()
        }.onFailure { Log.w(TAG, "log recorder unavailable", it) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(dir, CRASH).writeText("${stamp()} ${thread.name}\n${Log.getStackTraceString(error)}") }
            previous?.uncaughtException(thread, error)
        }
        Log.i(TAG, "start ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}")
    }

    fun fileName(): String = "etkn-mobile-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

    /** Writes the redacted report to [target]; call off the main thread. */
    fun export(context: Context, target: Uri) {
        val report = redact(report(context))
        val output = context.contentResolver.openOutputStream(target, "wt") ?: error("无法写入所选位置")
        output.use { it.write(report.toByteArray()) }
    }

    private fun report(context: Context): String = buildString {
        appendLine("ETKN Mobile ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · exported ${stamp()}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) · Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= 31) appendLine("SoC: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Display HDR: ${hdr(context)}")
        appendLine()
        appendLine("== Decoders ==")
        decoders(this)
        appendLine()
        appendLine("== Recent exits ==")
        exits(context, this)
        val dir = directory(context)
        File(dir, CRASH).takeIf { it.isFile }?.let {
            appendLine()
            appendLine("== Last Java crash ==")
            appendLine(it.readText())
        }
        appendLine()
        appendLine("== Log ==")
        // logcat rotation: app.log.N is the oldest, app.log the newest.
        for (index in FILES downTo 0) {
            val file = File(dir, if (index == 0) LOG else "$LOG.$index")
            if (file.isFile) append(file.readText())
        }
    }

    private fun hdr(context: Context): String = runCatching {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        val types = display.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
        types.joinToString { type ->
            when (type) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                else -> type.toString()
            }
        }.ifEmpty { "none" }
    }.getOrDefault("unknown")

    /** Decoders for the formats discs and remuxes use, with profiles and size limits. */
    private fun decoders(out: StringBuilder) = runCatching {
        for (info in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
            if (info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.lowercase(Locale.ROOT) !in WATCHED) continue
                val caps = runCatching { info.getCapabilitiesForType(type) }.getOrNull()
                val kind = if (Build.VERSION.SDK_INT >= 29) (if (info.isHardwareAccelerated) "hw" else "sw") else "?"
                val size = caps?.videoCapabilities?.let { " max ${it.supportedWidths.upper}x${it.supportedHeights.upper}" }.orEmpty()
                val audio = caps?.audioCapabilities?.let { " ch ${it.maxInputChannelCount}" }.orEmpty()
                val profiles = caps?.profileLevels?.map { it.profile }?.distinct()?.joinToString(",").orEmpty()
                out.appendLine("${info.name} [$kind] $type$size$audio profiles=$profiles")
            }
        }
    }.onFailure { out.appendLine("unavailable: $it") }

    private fun exits(context: Context, out: StringBuilder) {
        if (Build.VERSION.SDK_INT < 30) {
            out.appendLine("unavailable before Android 11")
            return
        }
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            for (exit in manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)) {
                out.appendLine("${stamp(exit.timestamp)} ${reason(exit.reason)} status=${exit.status} ${exit.description.orEmpty()}")
            }
        }.onFailure { out.appendLine("unavailable: $it") }
    }

    private fun reason(code: Int): String = when (code) {
        ApplicationExitInfo.REASON_CRASH -> "java-crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native-crash"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low-memory"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit-self"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user-requested"
        ApplicationExitInfo.REASON_OTHER -> "other"
        else -> "reason-$code"
    }

    /** Removes credentials: whole URL query strings and any token-like key/value. */
    fun redact(text: String): String = text
        .replace(URL_QUERY, "$1?…")
        .replace(SECRET, "$1=…")

    private fun directory(context: Context) = File(context.filesDir, "diagnostics").apply { mkdirs() }

    private fun stamp(time: Long = System.currentTimeMillis()) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(time))

    private val WATCHED = setOf(
        "video/hevc", "video/dolby-vision", "video/avc", "video/av01", "video/x-vnd.on2.vp9", "video/mpeg2",
        "audio/vnd.dts", "audio/vnd.dts.hd", "audio/true-hd", "audio/ac3", "audio/eac3", "audio/eac3-joc", "audio/ac4",
    )
    private val URL_QUERY = Regex("""((?:https?://|/)[^\s"'<>?]+)\?[^\s"'<>]+""")
    private val SECRET = Regex("""(?i)\b(x-emby-token|x-mediabrowser-token|api_key|access_?token|token|password|pwd|authorization|cookie)\s*[=:]\s*"?[^\s",&;]+"?""")
}
