package xyz.etkn.mobile.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.api.EtknApi
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.model.MediaSource
import xyz.etkn.mobile.data.model.MediaStream
import xyz.etkn.mobile.data.model.TICKS_PER_MS
import xyz.etkn.mobile.data.session.ActiveSession
import xyz.etkn.mobile.data.settings.Settings
import xyz.etkn.mobile.data.settings.SkipMode
import java.util.Locale

@Immutable
data class TrackOption(val groupIndex: Int, val trackIndex: Int, val label: String, val selected: Boolean)

@Immutable
data class SkipSegment(val kind: Kind, val startMs: Long, val endMs: Long) {
    enum class Kind { Intro, Credits }
}

@Immutable
data class VideoUi(
    val title: String = "",
    val subtitle: String = "",
    val item: Item? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val audio: List<TrackOption> = emptyList(),
    val text: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = true,
    val sources: List<MediaSource> = emptyList(),
    val sourceIndex: Int = 0,
    val next: Item? = null,
    val segments: List<SkipSegment> = emptyList(),
    val speed: Float = 1f,
    val skipMode: SkipMode = SkipMode.Button,
    val finished: Boolean = false,
)

/**
 * Owns one ExoPlayer for the lifetime of the player screen and talks to ETKN:
 * PlaybackInfo → direct stream → Playing / Progress (every 10 s and on state changes) / Stopped.
 * Reports run on the application scope so the final Stopped survives the activity.
 */
class VideoSession(
    private val context: Context,
    private val graph: AppGraph,
    private val scope: CoroutineScope,
) {
    private val api = graph.api
    val player: ExoPlayer
    private val settings: Settings
    private val session: ActiveSession = graph.sessions.require()

    private val _ui = MutableStateFlow(VideoUi())
    val ui: StateFlow<VideoUi> = _ui.asStateFlow()

    private var item: Item? = null
    private var source: MediaSource? = null
    private var playSession = ""
    private var started = false
    private var stopped = true
    private var completedBySkip = false
    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var autoSkippedIntro = false

    init {
        settings = kotlinx.coroutines.runBlocking { graph.settings.flow.first() }
        player = PlayerFactory.video(context, graph.http.media, graph.identity.userAgent, settings.decoder)
        player.setPlaybackSpeed(settings.defaultSpeed)
        _ui.update { it.copy(speed = settings.defaultSpeed, skipMode = settings.skipMode) }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        _ui.update { it.copy(loading = false, error = null) }
                        if (!started) {
                            started = true
                            report("")
                        }
                    }
                    Player.STATE_BUFFERING -> _ui.update { it.copy(loading = true) }
                    Player.STATE_ENDED -> onEnded()
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (started) report("Progress")
            }

            override fun onTracksChanged(tracks: Tracks) = publishTracks(tracks)

            override fun onPlayerError(error: PlaybackException) {
                _ui.update { it.copy(loading = false, error = "播放失败（${error.errorCodeName}）") }
            }
        })
    }

    // ---------- Loading ----------

    fun start(itemId: String, type: String?, seriesId: String?, seasonId: String?, startMs: Long, resume: Boolean, sourceId: String?) {
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                var targetId = itemId
                var position = startMs
                if ((type == "Series" || type == "Season") && seriesId != null) {
                    val target = api.playbackTarget(seriesId, if (type == "Season") seasonId ?: itemId else null)
                        ?: api.episodePage(seriesId, if (type == "Season") seasonId ?: itemId else null, start = 0, limit = 1).items.firstOrNull()
                        ?: throw IllegalStateException("没有可播放的剧集")
                    targetId = target.id
                    position = if (resume && !target.userData.played) target.positionMs else 0
                }
                load(targetId, position, sourceId)
            } catch (error: Exception) {
                _ui.update { it.copy(loading = false, error = error.message ?: "无法开始播放") }
            }
        }
    }

    private suspend fun load(itemId: String, startMs: Long, sourceId: String?) {
        stopReporting()
        _ui.update { it.copy(loading = true, error = null, next = null, segments = emptyList()) }
        val detail = runCatching { api.item(itemId, EtknApi.DETAIL_FIELDS) }.getOrNull() ?: api.item(itemId)
        val info = api.playbackInfo(itemId)
        if (info.sources.isEmpty()) throw IllegalStateException("暂无可播放版本")
        val index = sourceId?.let { id -> info.sources.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        item = detail
        playSession = info.playSessionId
        _ui.update {
            it.copy(
                item = detail,
                title = if (detail.isEpisode) detail.seriesName ?: detail.name else detail.name,
                subtitle = if (detail.isEpisode) listOfNotNull(detail.episodeCode, detail.name).joinToString("  ") else detail.productionYear?.toString().orEmpty(),
                sources = info.sources,
                sourceIndex = index,
            )
        }
        playSource(info.sources[index], startMs)
        if (detail.isEpisode) loadNext(detail.id)
    }

    private fun playSource(chosen: MediaSource, startMs: Long) {
        val current = item ?: return
        source = chosen
        started = false
        stopped = false
        completedBySkip = false
        autoSkippedIntro = startMs > 0
        val url = PlaybackAddress.resolve(chosen, session.server, session.token, session.userId)
        val subtitles = chosen.subtitles.filter { it.isExternal && it.deliveryUrl != null }.mapNotNull { stream ->
            val address = PlaybackAddress.subtitle(stream.deliveryUrl!!, session.server, session.token) ?: return@mapNotNull null
            val mime = subtitleMime(stream.codec) ?: return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(address))
                .setMimeType(mime)
                .setLanguage(stream.language.ifEmpty { null })
                .setLabel(stream.label)
                .setId("ext-${stream.index}")
                .setSelectionFlags(if (stream.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val media = MediaItem.Builder()
            .setUri(url)
            .setMediaId(current.id)
            .setSubtitleConfigurations(subtitles)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(_ui.value.title).setSubtitle(_ui.value.subtitle).build())
            .build()
        player.setMediaItem(media, startMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = true
        _ui.update { it.copy(segments = segments(chosen.chapters.ifEmpty { current.chapters }, chosen.runTimeTicks / TICKS_PER_MS)) }
        startProgressLoop()
    }

    fun selectSource(index: Int) {
        val sources = _ui.value.sources
        val chosen = sources.getOrNull(index) ?: return
        val position = player.currentPosition
        stopReporting()
        _ui.update { it.copy(sourceIndex = index, loading = true) }
        playSource(chosen, position)
    }

    private fun loadNext(itemId: String) {
        scope.launch {
            val next = runCatching { api.nextEpisode(itemId) }.getOrNull()
            if (item?.id == itemId) _ui.update { it.copy(next = next) }
        }
    }

    fun playNext(completed: Boolean = false) {
        val next = _ui.value.next ?: return
        completedBySkip = completed
        stopReporting()
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                load(next.id, if (next.userData.played) 0 else next.positionMs, null)
            } catch (error: Exception) {
                _ui.update { it.copy(loading = false, error = error.message ?: "无法播放下一集") }
            }
        }
    }

    fun playEpisode(episode: Item) {
        if (episode.id == item?.id) return
        stopReporting()
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                load(episode.id, if (episode.userData.played) 0 else episode.positionMs, null)
            } catch (error: Exception) {
                _ui.update { it.copy(loading = false, error = error.message ?: "无法播放") }
            }
        }
    }

    private fun onEnded() {
        completedBySkip = true
        if (settings.autoPlayNext && _ui.value.next != null) playNext(completed = true)
        else {
            stopReporting()
            _ui.update { it.copy(finished = true) }
        }
    }

    // ---------- Skip segments ----------

    private fun segments(chapters: List<xyz.etkn.mobile.data.model.Chapter>, durationMs: Long): List<SkipSegment> {
        val out = ArrayList<SkipSegment>(2)
        val introStart = chapters.firstOrNull { it.markerType == "IntroStart" }?.startTicks?.div(TICKS_PER_MS)
        val introEnd = chapters.firstOrNull { it.markerType == "IntroEnd" }?.startTicks?.div(TICKS_PER_MS)
        if (introEnd != null && introEnd > (introStart ?: 0)) out += SkipSegment(SkipSegment.Kind.Intro, introStart ?: 0, introEnd)
        val credits = chapters.firstOrNull { it.markerType == "CreditsStart" }?.startTicks?.div(TICKS_PER_MS)
        if (credits != null && credits > 0) out += SkipSegment(SkipSegment.Kind.Credits, credits, if (durationMs > 0) durationMs else Long.MAX_VALUE)
        return out
    }

    /** Segment active at [positionMs] (pure; safe to call during composition). */
    fun segmentAt(positionMs: Long): SkipSegment? {
        val ui = _ui.value
        if (ui.skipMode == SkipMode.Off) return null
        return ui.segments.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs - 1_000 }
    }

    /** Auto mode skips the intro once per item (not when resuming past its start). */
    fun autoSkip(segment: SkipSegment): Boolean {
        if (_ui.value.skipMode != SkipMode.Auto || segment.kind != SkipSegment.Kind.Intro || autoSkippedIntro) return false
        autoSkippedIntro = true
        player.seekTo(segment.endMs)
        return true
    }

    fun skip(segment: SkipSegment) {
        when (segment.kind) {
            SkipSegment.Kind.Intro -> player.seekTo(segment.endMs)
            SkipSegment.Kind.Credits -> if (_ui.value.next != null) playNext(completed = true) else player.seekTo(player.duration.coerceAtLeast(0))
        }
    }

    // ---------- Tracks ----------

    private fun publishTracks(tracks: Tracks) {
        val src = source
        val audioStreams = src?.audio.orEmpty()
        val textStreams = src?.subtitles?.filterNot { it.isExternal }.orEmpty()
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        var audioOrdinal = 0
        var textOrdinal = 0
        tracks.groups.forEachIndexed { g, group ->
            for (t in 0 until group.length) {
                if (!group.isTrackSupported(t, true)) continue
                val format = group.getTrackFormat(t)
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> audio += TrackOption(g, t, audioLabel(format, audioStreams.getOrNull(audioOrdinal++)), group.isTrackSelected(t))
                    C.TRACK_TYPE_TEXT -> {
                        val external = format.id?.contains("ext-") == true
                        val stream = if (external) null else textStreams.getOrNull(textOrdinal++)
                        text += TrackOption(g, t, textLabel(format, stream), group.isTrackSelected(t))
                    }
                }
            }
        }
        val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        _ui.update { it.copy(audio = audio, text = text, textEnabled = !textDisabled && text.any { o -> o.selected }) }
    }

    fun selectTrack(option: TrackOption) {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .setTrackTypeDisabled(group.type, false)
            .build()
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _ui.update { it.copy(speed = speed) }
    }

    // ---------- Reporting ----------

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (started && player.isPlaying) report("Progress")
            }
        }
    }

    private fun stopReporting() {
        progressJob?.cancel()
        if (source != null && !stopped && started) report("Stopped")
        stopped = true
    }

    private fun report(event: String) {
        val current = item ?: return
        val src = source ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0)
        val durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
        val payload = buildJsonObject {
            put("ItemId", current.id)
            put("UserId", session.userId)
            put("MediaSourceId", src.id)
            put("PlaySessionId", playSession)
            put("PositionTicks", positionMs * TICKS_PER_MS)
            put("RunTimeTicks", durationMs?.times(TICKS_PER_MS) ?: src.runTimeTicks)
            put("IsPaused", !player.isPlaying)
            put("CanSeek", true)
            put("PlayMethod", "DirectStream")
            put("Client", AppGraph.CLIENT_NAME)
            put("TranscodeSessionId", "")
            if (event == "Stopped" && completedBySkip) put("PlayedToCompletion", true)
        }
        // Application scope: the Stopped report must outlive the player screen.
        graph.scope.launch { runCatching { api.report(event, payload, session) } }
    }

    fun release() {
        loadJob?.cancel()
        stopReporting()
        player.release()
    }

    // ---------- Labels ----------

    private fun audioLabel(format: Format, stream: MediaStream?): String {
        stream?.previewLabel?.takeIf { it.isNotBlank() }?.let { return it }
        val parts = listOfNotNull(
            format.label ?: format.language?.let { Locale.forLanguageTag(it).displayLanguage.ifEmpty { it } },
            codecName(format.sampleMimeType),
            format.channelCount.takeIf { it > 0 }?.let { if (it > 2) "${it - 1}.1" else if (it == 2) "立体声" else "单声道" },
        )
        return parts.joinToString(" · ").ifEmpty { "音轨" }
    }

    private fun textLabel(format: Format, stream: MediaStream?): String {
        stream?.let { s -> s.title.ifEmpty { s.displayTitle }.takeIf { it.isNotBlank() }?.let { return it } }
        return format.label ?: format.language?.let { Locale.forLanguageTag(it).displayLanguage.ifEmpty { it } } ?: "字幕"
    }

    private fun codecName(mime: String?): String? = when (mime) {
        MimeTypes.AUDIO_AC3 -> "DD"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "DD+"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        else -> null
    }

    private fun subtitleMime(codec: String): String? = when (codec.lowercase()) {
        "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
        "vtt", "webvtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "ttml" -> MimeTypes.APPLICATION_TTML
        else -> null
    }
}
