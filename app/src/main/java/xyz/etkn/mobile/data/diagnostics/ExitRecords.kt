package xyz.etkn.mobile.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Deaths the app cannot log itself. A native crash, a libmpv fatal error (`exit(1)`) or a kill
 * ends the process before any Java handler runs, and the log recorder dies with it, often before
 * it has written the last lines. On the next start Android's exit records are read instead: each
 * new [abnormal] exit is written to crash.log at the time it happened, with the dead process's
 * final log lines (still in the system log buffer), the decoded tombstone of a native crash
 * (signal, abort message, crashing thread's backtrace) and the main thread of an ANR, so
 * 导出日志 includes it for that day and hour.
 */
internal object ExitRecords {
    private const val PREFS = "diagnostics"
    private const val SEEN = "exit_seen"
    private const val MAX_FRAMES = 40

    fun record(context: Context, crashLog: File) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching { collect(context, crashLog) }
    }

    /**
     * Crashes and ANRs always; a process that ended itself with a failure status; any other death
     * while on screen. Deaths the user or an update asked for, and background reclaims, are normal.
     */
    @RequiresApi(30)
    fun abnormal(exit: ApplicationExitInfo): Boolean = when (exit.reason) {
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR -> true
        ApplicationExitInfo.REASON_EXIT_SELF -> exit.status != 0
        ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_USER_STOPPED,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE, ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> false
        else -> exit.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    @RequiresApi(30)
    fun label(exit: ApplicationExitInfo): String = when (exit.reason) {
        ApplicationExitInfo.REASON_CRASH -> "JavaCrash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NativeCrash"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_EXIT_SELF -> "ExitSelf"
        ApplicationExitInfo.REASON_SIGNALED -> "Signaled"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LowMemory"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "InitFailure"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "ExcessiveResources"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DependencyDied"
        ApplicationExitInfo.REASON_FREEZER -> "Freezer"
        else -> "Exit${exit.reason}"
    }

    @RequiresApi(30)
    private fun collect(context: Context, crashLog: File) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getLong(SEEN, 0L)
        val exits = context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, 8)
            .filter { it.timestamp > seen }
        if (exits.isEmpty()) return
        prefs.edit().putLong(SEEN, exits.maxOf { it.timestamp }).apply()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val out = StringBuilder()
        for (exit in exits.filter(::abnormal).sortedBy { it.timestamp }) {
            Diagnostics.processLog(exit.pid).forEach { out.append(it).append('\n') }
            val head = "${stamp.format(Date(exit.timestamp))} ${exit.pid} ${exit.pid} F ${label(exit)}: "
            val lines = ArrayList<String>()
            lines += "exit status=${exit.status} ${exit.description.orEmpty()} pss=${exit.pss}kB rss=${exit.rss}kB importance=${exit.importance}"
            when (exit.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> if (Build.VERSION.SDK_INT >= 31) {
                    exit.traceInputStream?.use { lines += Tombstone.describe(it.readBytes(), MAX_FRAMES) }
                }
                ApplicationExitInfo.REASON_ANR -> exit.traceInputStream?.use { stream ->
                    lines += mainThread(stream.bufferedReader().readLines())
                }
            }
            lines.forEach { out.append(head).append(it).append('\n') }
        }
        if (out.isNotEmpty()) crashLog.appendText(out.toString())
    }

    /** The main thread's section of an ANR dump. */
    private fun mainThread(dump: List<String>): List<String> {
        val start = dump.indexOfFirst { it.startsWith("\"main\"") }.takeIf { it >= 0 } ?: return dump.take(MAX_FRAMES)
        return dump.drop(start).takeWhile { it.isNotBlank() }.take(MAX_FRAMES)
    }
}

/**
 * Minimal reader for Android's tombstone protobuf (system/core/debuggerd/proto/tombstone.proto):
 * Tombstone.tid = 6, signal_info = 10 (Signal.name = 2, code_name = 4, fault_address = 9),
 * abort_message = 14, threads = 16 (map entry key = 1, value = 2); Thread.name = 2,
 * current_backtrace = 4; BacktraceFrame.rel_pc = 1, function_name = 4, function_offset = 5,
 * file_name = 6. Unknown fields are skipped, so a newer layout degrades to fewer lines.
 */
internal object Tombstone {
    fun describe(bytes: ByteArray, maxFrames: Int): List<String> = runCatching {
        val top = Proto(bytes).fields()
        val tid = top.firstOrNull { it.number == 6 }?.varint ?: -1L
        val lines = ArrayList<String>()
        top.firstOrNull { it.number == 10 }?.bytes?.let { signal ->
            val f = Proto(signal).fields()
            val name = f.firstOrNull { it.number == 2 }?.text.orEmpty()
            val code = f.firstOrNull { it.number == 4 }?.text.orEmpty()
            val fault = f.firstOrNull { it.number == 9 }?.varint
            lines += "signal $name $code" + (fault?.let { " fault addr 0x" + java.lang.Long.toHexString(it) } ?: "")
        }
        top.firstOrNull { it.number == 14 }?.text?.takeIf { it.isNotBlank() }?.let { lines += "abort: $it" }
        val threads = top.filter { it.number == 16 }.mapNotNull { entry ->
            val e = Proto(entry.bytes ?: return@mapNotNull null).fields()
            val key = e.firstOrNull { it.number == 1 }?.varint ?: return@mapNotNull null
            val value = e.firstOrNull { it.number == 2 }?.bytes ?: return@mapNotNull null
            key to value
        }
        val crashing = threads.firstOrNull { it.first == tid }?.second ?: threads.firstOrNull()?.second
        if (crashing != null) {
            val t = Proto(crashing).fields()
            lines += "thread ${t.firstOrNull { it.number == 2 }?.text.orEmpty()} tid=$tid"
            t.filter { it.number == 4 }.take(maxFrames).forEachIndexed { index, frame ->
                val f = Proto(frame.bytes ?: return@forEachIndexed).fields()
                val pc = f.firstOrNull { it.number == 1 }?.varint ?: 0L
                val function = f.firstOrNull { it.number == 4 }?.text.orEmpty()
                val offset = f.firstOrNull { it.number == 5 }?.varint ?: 0L
                val file = f.firstOrNull { it.number == 6 }?.text.orEmpty().substringAfterLast('/')
                lines += "#%02d pc %08x %s%s".format(index, pc, file, if (function.isNotEmpty()) " ($function+$offset)" else "")
            }
        }
        lines
    }.getOrDefault(emptyList())

    class Field(val number: Int, val varint: Long?, val bytes: ByteArray?) {
        val text: String? get() = bytes?.toString(Charsets.UTF_8)
    }

    private class Proto(private val data: ByteArray) {
        private var at = 0

        fun fields(): List<Field> {
            val out = ArrayList<Field>()
            while (at < data.size) {
                val key = varint()
                val number = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> out += Field(number, varint(), null)
                    1 -> at += 8
                    2 -> {
                        val length = varint().toInt()
                        require(length >= 0 && at + length <= data.size)
                        out += Field(number, null, data.copyOfRange(at, at + length))
                        at += length
                    }
                    5 -> at += 4
                    else -> error("unsupported wire type")
                }
            }
            return out
        }

        private fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = data[at++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift < 64)
            }
        }
    }
}
