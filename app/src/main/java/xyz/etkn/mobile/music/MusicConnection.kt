package xyz.etkn.mobile.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.api.ImageUrls
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.model.TICKS_PER_MS
import xyz.etkn.mobile.data.session.ActiveSession
import xyz.etkn.mobile.data.session.SessionState
import xyz.etkn.mobile.player.PlaybackAddress
import xyz.etkn.mobile.player.PlayerFactory
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class MusicState(
    val current: Item? = null,
    val queue: List<Item> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
    val sourceId: String? = null,
)

/**
 * In-process music engine shared by the UI and [MusicService]. Tracks are queued as
 * `etkn-music://<itemId>`; the stream address is resolved lazily on ExoPlayer's loader thread
 * via PlaybackInfo, so a 200-track album costs one request per track actually played.
 */
class MusicConnection(private val context: Context, private val graph: AppGraph) {
    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()
    private val _library = MutableStateFlow<String?>(null)
    val libraryId: StateFlow<String?> = _library.asStateFlow()

    private data class Resolved(val url: String, val playSession: String, val sourceId: String, val runTimeTicks: Long)

    private val resolved = ConcurrentHashMap<String, Resolved>()
    private val items = ConcurrentHashMap<String, Item>()
    private var reportedId: String? = null
    private var startedId: String? = null
    private var progressJob: Job? = null
    @Volatile private var session: ActiveSession? = null

    private var _player: ExoPlayer? = null
    val player: ExoPlayer
        get() = _player ?: create().also { _player = it }

    init {
        graph.scope.launch {
            graph.sessions.state.collect { s ->
                val next = (s as? SessionState.SignedIn)?.session
                if (next?.key != session?.key) {
                    stop()
                    resolved.clear()
                    _library.value = null
                }
                session = next
            }
        }
    }

    fun selectLibrary(id: String) {
        _library.value = id
    }

    private fun create(): ExoPlayer {
        val upstream = PlayerFactory.dataSource(graph.http.media, graph.identity.userAgent)
        val factory = ResolvingDataSource.Factory(upstream) { spec -> resolve(spec) }
        val player = PlayerFactory.music(context, factory)
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publish()

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                finishReport(completed = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) startReport()
                if (state == Player.STATE_ENDED) finishReport(completed = true)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (startedId != null) report("Progress", completed = false)
            }

            override fun onPlayerError(error: PlaybackException) {
                finishReport(completed = false)
                _state.value = _state.value.copy(error = "无法播放这首歌（${error.errorCodeName}）")
            }
        })
        return player
    }

    /** Runs on the loader thread; blocking here is expected by ResolvingDataSource. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != SCHEME) return spec
        val id = spec.uri.lastPathSegment ?: throw IllegalStateException("无效曲目")
        val active = session ?: throw IllegalStateException("未登录")
        val entry = resolved[id] ?: runBlocking {
            val info = graph.api.playbackInfo(id, audio = true)
            val source = info.sources.firstOrNull() ?: throw IllegalStateException("这首歌暂无可播放版本")
            val url = PlaybackAddress.music(source, active.server, active.token, active.userId, info.playSessionId)
            Resolved(url, info.playSessionId, source.id, source.runTimeTicks)
        }.also { resolved[id] = it }
        if (_state.value.current?.id == id) _state.value = _state.value.copy(sourceId = entry.sourceId)
        return spec.withUri(Uri.parse(entry.url))
    }

    fun play(tracks: List<Item>, index: Int, contextKey: String?) {
        if (tracks.isEmpty()) return
        tracks.forEach { items[it.id] = it }
        context.startService(Intent(context, MusicService::class.java))
        val urls = graph.sessions.current?.let(graph::images)
        val p = player
        finishReport(completed = false)
        p.setMediaItems(tracks.map { toMediaItem(it, urls) }, index.coerceIn(0, tracks.lastIndex), 0)
        p.prepare()
        p.play()
    }

    fun append(tracks: List<Item>) {
        val urls = graph.sessions.current?.let(graph::images)
        tracks.forEach { items[it.id] = it }
        _player?.addMediaItems(tracks.map { toMediaItem(it, urls) })
    }

    fun toggle() { _player?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun pause() { _player?.pause() }
    fun next() { _player?.seekToNextMediaItem() }
    fun previous() { _player?.let { if (it.currentPosition > 3_000) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seekTo(ms: Long) { _player?.seekTo(ms) }
    fun skipTo(index: Int) { _player?.seekTo(index, 0) }
    fun toggleShuffle() { _player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        _player?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun removeAt(index: Int) { _player?.removeMediaItem(index) }

    fun stop() {
        finishReport(completed = false)
        _player?.let {
            it.stop()
            it.clearMediaItems()
        }
        publish()
    }

    private fun publish() {
        val p = _player ?: run { _state.value = MusicState(); return }
        val queue = (0 until p.mediaItemCount).mapNotNull { items[p.getMediaItemAt(it).mediaId] }
        val index = p.currentMediaItemIndex
        val current = p.currentMediaItem?.mediaId?.let(items::get)
        _state.value = _state.value.copy(
            current = current,
            queue = queue,
            index = if (current == null) -1 else index,
            isPlaying = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            shuffle = p.shuffleModeEnabled,
            repeat = p.repeatMode,
            error = if (p.playerError == null) null else _state.value.error,
            sourceId = current?.id?.let { resolved[it]?.sourceId },
        )
    }

    // ---------- Reporting (TV contract: Playing → Progress → Stopped with PlayedToCompletion) ----------

    private fun startReport() {
        val id = _player?.currentMediaItem?.mediaId ?: return
        if (startedId == id) return
        startedId = id
        reportedId = id
        report("", completed = false)
        progressJob?.cancel()
        progressJob = graph.scope.launch {
            while (isActive) {
                delay(10_000)
                if (_player?.isPlaying == true) report("Progress", completed = false)
            }
        }
    }

    private fun finishReport(completed: Boolean) {
        if (reportedId == null) return
        report("Stopped", completed)
        progressJob?.cancel()
        reportedId = null
        startedId = null
    }

    private fun report(event: String, completed: Boolean) {
        val id = reportedId ?: return
        val active = session ?: return
        val entry = resolved[id] ?: return
        val p = _player ?: return
        val positionMs = if (p.currentMediaItem?.mediaId == id) p.currentPosition else entry.runTimeTicks / TICKS_PER_MS
        val payload = buildJsonObject {
            put("ItemId", id)
            put("UserId", active.userId)
            put("MediaSourceId", entry.sourceId)
            put("PlaySessionId", entry.playSession)
            put("PositionTicks", positionMs.coerceAtLeast(0) * TICKS_PER_MS)
            put("IsPaused", !p.isPlaying)
            put("PlayedToCompletion", completed)
        }
        graph.scope.launch { runCatching { graph.api.report(event, payload, active) } }
    }

    private fun toMediaItem(item: Item, urls: ImageUrls?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(item.name)
            .setArtist(item.artistLine.ifEmpty { null })
            .setAlbumTitle(item.album)
            .setArtworkUri(urls?.music(item, 512)?.let(Uri::parse))
            .setDurationMs(item.runtimeMs.takeIf { it > 0 })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(Bundle().apply { putLong("runtime", item.runTimeTicks) })
            .build()
        return MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(Uri.Builder().scheme(SCHEME).authority("track").appendPath(item.id).build())
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        const val SCHEME = "etkn-music"
    }
}
