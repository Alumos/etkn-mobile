package xyz.etkn.mobile.data.events

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.JsonObject
import xyz.etkn.mobile.MainActivity
import xyz.etkn.mobile.R
import xyz.etkn.mobile.data.api.EtknApi
import xyz.etkn.mobile.data.net.bool
import xyz.etkn.mobile.data.net.objects
import xyz.etkn.mobile.data.net.str
import xyz.etkn.mobile.data.session.ActiveSession
import java.security.MessageDigest

/** One `IngestUpdates` / `acquire-status` row: a title that arrived, was replaced or got a version. */
data class IngestEvent(val eventId: String, val itemId: String, val name: String, val seriesName: String, val outcome: String) {
    val title: String get() = if (seriesName.isNotEmpty() && seriesName != name) "$seriesName · $name" else name
    val verb: String
        get() = when (outcome) {
            "replaced" -> "已洗版"
            "version_added" -> "新增版本"
            else -> "已入库"
        }

    companion object {
        fun from(o: JsonObject) = IngestEvent(
            eventId = o.str("EventId").ifEmpty { o.str("VersionId") },
            itemId = o.str("Id"),
            name = o.str("Name"),
            seriesName = o.str("SeriesName"),
            outcome = o.str("Outcome"),
        )
    }
}

data class IngestPage(val items: List<IngestEvent>, val cursor: String, val initial: Boolean, val hasMore: Boolean) {
    companion object {
        fun from(o: JsonObject) = IngestPage(o.objects("Items").map(IngestEvent::from), o.str("Cursor"), o.bool("Initial"), o.bool("HasMore"))
    }
}

data class AcquireStatus(val finished: Boolean, val failed: Boolean, val title: String, val error: String, val ingested: List<IngestEvent>) {
    companion object {
        fun from(o: JsonObject) = AcquireStatus(
            finished = o.bool("finished"), failed = o.bool("failed"), title = o.str("title"), error = o.str("error"),
            ingested = o.objects("ingest_items").map(IngestEvent::from),
        )
    }
}

/**
 * 入库通知 and 获取结果, as the TV client does them: `U/IngestUpdates` read from a per-account
 * cursor (the first batch only sets the cursor), and every confirmed cloud acquisition tracked by
 * its `workflow_run_id` for up to 7 days until `acquire-status` says it finished. Each event
 * becomes one system notification that opens the title; events are de-duplicated by `EventId`.
 * Checked while the app is in the foreground ([check]); nothing runs in the background.
 */
class IngestNotices(private val context: Context, private val api: EtknApi) {
    private val prefs = context.getSharedPreferences("ingest_notices", Context.MODE_PRIVATE)

    /** Remembers a submitted acquisition so its outcome is reported. */
    fun track(session: ActiveSession, runId: Long) {
        if (runId <= 0) return
        val key = account(session)
        prefs.edit().putStringSet("$key.runs", runs(key) + "$runId:${System.currentTimeMillis()}").apply()
    }

    suspend fun check(session: ActiveSession) {
        val key = account(session)
        val seen = prefs.getString("$key.seen", "").orEmpty().split('\n').filter { it.isNotEmpty() }.toMutableList()
        val fresh = ArrayList<IngestEvent>()
        var cursor = prefs.getString("$key.cursor", null)
        var pages = 0
        do {
            val page = runCatching { api.ingestUpdates(cursor.orEmpty()) }.getOrNull() ?: break
            // The first answer for an account is history, not news.
            if (cursor != null && !page.initial) fresh += page.items
            cursor = page.cursor
            prefs.edit().putString("$key.cursor", cursor).apply()
        } while (page.hasMore && ++pages < MAX_PAGES)
        val now = System.currentTimeMillis()
        val remaining = HashSet<String>()
        for (entry in runs(key)) {
            val (id, since) = entry.split(':').let { (it.getOrNull(0)?.toLongOrNull() ?: 0L) to (it.getOrNull(1)?.toLongOrNull() ?: 0L) }
            if (id <= 0 || now - since > RUN_TTL_MS) continue
            val status = runCatching { api.acquireStatus(id) }.getOrNull()
            if (status == null) {
                remaining += entry
                continue
            }
            fresh += status.ingested
            if (status.failed && status.finished) notifyFailure(id, status)
            if (!status.finished) remaining += entry
        }
        prefs.edit().putStringSet("$key.runs", remaining).apply()
        for (event in fresh.distinctBy { it.eventId }) {
            if (event.eventId.isEmpty() || event.eventId in seen) continue
            seen += event.eventId
            notifyIngest(event)
        }
        prefs.edit().putString("$key.seen", seen.takeLast(MAX_SEEN).joinToString("\n")).apply()
    }

    private fun runs(key: String): Set<String> = prefs.getStringSet("$key.runs", emptySet()).orEmpty()

    private fun notifyIngest(event: IngestEvent) = post(
        event.eventId.hashCode(),
        title = event.verb,
        text = event.title,
        itemId = event.itemId,
    )

    private fun notifyFailure(runId: Long, status: AcquireStatus) = post(
        runId.toInt(),
        title = "获取失败",
        text = listOf(status.title, status.error).filter { it.isNotEmpty() }.joinToString("：").ifEmpty { "资源获取、整理或入库失败" },
        itemId = null,
    )

    private fun post(id: Int, title: String, text: String, itemId: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "入库通知", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (itemId != null) putExtra(MainActivity.EXTRA_ITEM, itemId) }
        val tap = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id, notification) }
    }

    /** Server and user, hashed: the cursor and seen set belong to one account. */
    private fun account(session: ActiveSession): String =
        MessageDigest.getInstance("SHA-256").digest("${session.server}\u0000${session.userId}".toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    private companion object {
        const val CHANNEL = "ingest"
        const val MAX_PAGES = 5
        const val MAX_SEEN = 300
        const val RUN_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
