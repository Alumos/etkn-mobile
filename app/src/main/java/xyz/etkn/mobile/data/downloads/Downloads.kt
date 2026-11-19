package xyz.etkn.mobile.data.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.etkn.mobile.data.api.EtknApi
import xyz.etkn.mobile.data.discover.withValues
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.model.MediaSource
import xyz.etkn.mobile.data.net.EtknJson
import xyz.etkn.mobile.data.net.long
import xyz.etkn.mobile.data.net.obj
import xyz.etkn.mobile.data.net.objects
import xyz.etkn.mobile.data.net.str
import xyz.etkn.mobile.data.session.ActiveSession
import xyz.etkn.mobile.player.PlaybackAddress
import java.io.File

/** One downloaded (or downloading) title of one account. */
@Immutable
data class DownloadEntry(
    val itemId: String,
    val account: String,
    val title: String,
    val subtitle: String,
    val file: String,
    val downloadId: Long,
    /** Minimal item and the server's media source, so the file plays offline with its tracks and chapters. */
    val item: JsonObject,
    val source: JsonObject,
) {
    fun toJson() = buildJsonObject {
        put("itemId", itemId); put("account", account); put("title", title); put("subtitle", subtitle)
        put("file", file); put("downloadId", downloadId); put("item", item); put("source", source)
    }

    /** The item as the player sees it, with the media source pointing at the local file. */
    fun offlineItem(): Item = Item.from(item)
    fun offlineSource(): MediaSource = MediaSource.from(
        source.withValues("DirectStreamUrl" to JsonPrimitive(Uri.fromFile(File(file)).toString()), "Path" to JsonPrimitive(file)),
    )

    companion object {
        fun from(o: JsonObject) = DownloadEntry(
            itemId = o.str("itemId"), account = o.str("account"), title = o.str("title"), subtitle = o.str("subtitle"),
            file = o.str("file"), downloadId = o.long("downloadId"), item = o.obj("item") ?: JsonObject(emptyMap()),
            source = o.obj("source") ?: JsonObject(emptyMap()),
        )
    }
}

@Immutable
data class DownloadProgress(val status: Status, val done: Long, val total: Long) {
    enum class Status { Waiting, Running, Paused, Done, Failed }
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * 离线下载 through the system DownloadManager (notification, resumes on reconnect, follows the
 * server's 302 to the storage host). Files go to the app's own Movies folder, so no storage
 * permission is needed and uninstalling removes them. The stream URL carries the token in its
 * query ([PlaybackAddress.portable]): headers would be re-sent to the redirect target, a query
 * parameter is not. Private accounts never download. The list lives in `downloads.json`, per account.
 */
class Downloads(private val context: Context, private val api: EtknApi) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val store = File(context.filesDir, "downloads.json")
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<DownloadEntry>> = _entries.asStateFlow()

    fun of(session: ActiveSession): List<DownloadEntry> = _entries.value.filter { it.account == account(session) }

    fun find(session: ActiveSession, itemId: String): DownloadEntry? = of(session).firstOrNull { it.itemId == itemId }

    /** Queues one movie / episode / video ([sourceId] = version, default the first). */
    suspend fun enqueue(session: ActiveSession, item: Item, sourceId: String? = null) {
        check(!session.privateAccount) { "私密账号不能下载" }
        find(session, item.id)?.let { return }
        val info = api.playbackInfo(item.id)
        val source = info.sources.firstOrNull { it.id == sourceId } ?: info.sources.firstOrNull() ?: error("暂无可下载版本")
        check(!PlaybackAddress.isDisc(source)) { "原盘不支持下载" }
        val url = PlaybackAddress.portable(source, session.server, session.token, session.userId)
        val title = item.seriesName?.takeIf { item.isEpisode } ?: item.name
        val subtitle = if (item.isEpisode) listOfNotNull(item.episodeLabel, item.name).joinToString(" ") else item.productionYear?.toString().orEmpty()
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ETKN").apply { mkdirs() }
        val file = File(dir, safeName("$title $subtitle".trim()) + "-" + item.id.takeLast(8) + "." + extension(source))
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(listOf(title, subtitle).filter { it.isNotEmpty() }.joinToString(" · "))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true)
        val id = withContext(Dispatchers.IO) { manager.enqueue(request) }
        val entry = DownloadEntry(
            itemId = item.id, account = account(session), title = title, subtitle = subtitle, file = file.path, downloadId = id,
            item = buildJsonObject {
                put("Id", item.id); put("Name", item.name); put("Type", item.type)
                item.seriesName?.let { put("SeriesName", it) }
                item.seriesId?.let { put("SeriesId", it) }
                item.indexNumber?.let { put("IndexNumber", it) }
                item.parentIndexNumber?.let { put("ParentIndexNumber", it) }
                put("RunTimeTicks", item.runTimeTicks)
            },
            source = source.raw,
        )
        save(_entries.value + entry)
    }

    /** Current state of each listed download, by download id. */
    suspend fun progress(entries: List<DownloadEntry>): Map<Long, DownloadProgress> = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext emptyMap()
        val query = DownloadManager.Query().setFilterById(*entries.map { it.downloadId }.toLongArray())
        val out = HashMap<Long, DownloadProgress>()
        runCatching {
            manager.query(query)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val doneCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) {
                    val status = when (c.getInt(statusCol)) {
                        DownloadManager.STATUS_SUCCESSFUL -> DownloadProgress.Status.Done
                        DownloadManager.STATUS_FAILED -> DownloadProgress.Status.Failed
                        DownloadManager.STATUS_PAUSED -> DownloadProgress.Status.Paused
                        DownloadManager.STATUS_RUNNING -> DownloadProgress.Status.Running
                        else -> DownloadProgress.Status.Waiting
                    }
                    out[c.getLong(idCol)] = DownloadProgress(status, c.getLong(doneCol), c.getLong(totalCol))
                }
            }
        }
        // A file that is complete on disk but unknown to the manager (cleared history) still plays.
        entries.filter { it.downloadId !in out && File(it.file).length() > 0 }
            .forEach { out[it.downloadId] = DownloadProgress(DownloadProgress.Status.Done, File(it.file).length(), File(it.file).length()) }
        out
    }

    /** Cancels or deletes one download and its file. */
    suspend fun remove(entry: DownloadEntry) {
        withContext(Dispatchers.IO) {
            runCatching { manager.remove(entry.downloadId) }
            File(entry.file).delete()
        }
        save(_entries.value.filterNot { it.downloadId == entry.downloadId })
    }

    private fun read(): List<DownloadEntry> = runCatching {
        (EtknJson.parseToJsonElement(store.readText()) as JsonObject).objects("downloads").map(DownloadEntry::from)
    }.getOrDefault(emptyList())

    private suspend fun save(list: List<DownloadEntry>) {
        _entries.value = list
        withContext(Dispatchers.IO) {
            val temp = File(store.path + ".tmp")
            temp.writeText(JsonObject(mapOf("downloads" to JsonArray(list.map { it.toJson() }))).toString())
            temp.renameTo(store)
        }
    }

    private fun extension(source: MediaSource): String {
        val fromPath = source.path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return when {
            fromPath in VIDEO_EXTENSIONS -> fromPath
            source.container.lowercase() in VIDEO_EXTENSIONS -> source.container.lowercase()
            else -> "mkv"
        }
    }

    private fun safeName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(80).ifEmpty { "video" }

    private companion object {
        val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "mov", "avi", "ts", "m2ts", "webm", "wmv", "flv", "rmvb")

        fun account(session: ActiveSession) = "${session.server}|${session.userId}"
    }
}
