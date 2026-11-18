package xyz.etkn.mobile.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VideoSettings
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.settings.SkipMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.ui.graphics.toArgb
import xyz.etkn.mobile.player.danmaku.DanmakuView
import xyz.etkn.mobile.ui.screens.me.DanmakuControls
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.CardShape
import xyz.etkn.mobile.ui.components.formatClock
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.components.px
import kotlin.math.abs

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
private val ResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "适应",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "裁切填充",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "拉伸",
)

private enum class Sheet { None, Audio, Subtitle, Speed, Episodes, Versions, Danmaku }

@Composable
fun PlayerScreen(session: VideoSession, inPip: Boolean, onBack: () -> Unit, onPip: () -> Unit) {
    val ui by session.ui.collectAsStateWithLifecycle()
    val danmakuStyle by session.danmakuStyle.collectAsStateWithLifecycle()
    val danmakuCues by session.danmakuCues.collectAsStateWithLifecycle()
    val themeArgb = MaterialTheme.colorScheme.primary.toArgb()
    val player = session.player
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var resize by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var gesture by remember { mutableStateOf<GestureFeedback?>(null) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(ui.finished) { if (ui.finished) onBack() }
    // Poll the clock only as fast as the UI needs it (4 Hz).
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0
            buffered = player.bufferedPosition
            delay(250)
        }
    }
    // Auto-hide controls after 4 s of inactivity while playing.
    LaunchedEffect(controls, lastInteraction, playing, sheet) {
        if (controls && playing && sheet == Sheet.None) {
            delay(4_000)
            controls = false
        }
    }
    val segment = remember(position, ui.segments, ui.skipMode) { session.segmentAt(position) }
    LaunchedEffect(segment) { segment?.let(session::autoSkip) }
    // Double-tap seek feedback clears itself; drag feedback is cleared on release.
    LaunchedEffect(gesture) {
        val shown = gesture
        if (shown is GestureFeedback.Seek) {
            delay(800)
            if (gesture === shown) gesture = null
        }
    }
    val touch = { lastInteraction = System.currentTimeMillis() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    this.player = player
                }
            },
            update = { view -> view.resizeMode = ResizeModes[resize].first },
            modifier = Modifier.fillMaxSize(),
        )
        if (ui.danmakuOn && !inPip) {
            AndroidView(
                factory = { ctx -> DanmakuView(ctx, player) },
                update = { view ->
                    view.apply(danmakuStyle, themeArgb)
                    view.setCues(danmakuCues)
                },
                onRelease = { it.close() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!inPip) {
            GestureLayer(
                enabled = !locked,
                session = session,
                onToggle = { controls = !controls; touch() },
                onFeedback = { gesture = it },
            )
        }

        if (ui.loading && ui.error == null) {
            LoadingIndicator(Modifier.align(Alignment.Center).size(72.dp), color = Color.White)
        }
        ui.error?.let { message ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onBack) { Text("返回") }
            }
        }
        gesture?.let { feedback -> GestureBadge(feedback, Modifier.align(Alignment.Center)) }
        ui.notice?.let { message ->
            LaunchedEffect(message) {
                delay(3_500)
                session.clearNotice(message)
            }
            Text(
                message,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 64.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (!inPip) {
            AnimatedVisibility(controls && !locked, enter = fadeIn(), exit = fadeOut()) {
                Controls(
                    ui = ui, playing = playing, position = position, duration = duration, buffered = buffered,
                    onBack = onBack, onPip = onPip,
                    onPlayPause = { if (player.isPlaying) player.pause() else player.play(); touch() },
                    onSeek = { player.seekTo(it); touch() },
                    onSeekBy = { player.seekTo((player.currentPosition + it).coerceAtLeast(0)); touch() },
                    onSheet = { sheet = it; touch() },
                    onResize = { resize = (resize + 1) % ResizeModes.size; touch() },
                    resizeLabel = ResizeModes[resize].second,
                    onLock = { locked = true; controls = false },
                    onNext = { session.playNext() },
                )
            }
            if (locked) {
                FilledIconButton(
                    onClick = { locked = false; controls = true },
                    modifier = Modifier.align(Alignment.CenterStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
                ) { Icon(Icons.Rounded.Lock, "解锁") }
            }
            segment?.takeUnless { ui.skipMode == SkipMode.Auto && it.kind == SkipSegment.Kind.Intro }?.let { s ->
                FilledTonalButton(
                    onClick = { session.skip(s) },
                    modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(end = 24.dp, bottom = if (controls) 120.dp else 32.dp),
                ) {
                    Icon(if (s.kind == SkipSegment.Kind.Intro) Icons.Rounded.FastForward else Icons.Rounded.SkipNext, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.kind == SkipSegment.Kind.Intro) "跳过片头" else if (ui.next != null) "下一集" else "跳过片尾")
                }
            }
        }
    }

    if (sheet != Sheet.None) {
        PlayerSheet(sheet, ui, session, onDismiss = { sheet = Sheet.None })
    }
}

// ---------- Gestures ----------

private sealed interface GestureFeedback {
    data class Seek(val targetMs: Long, val deltaMs: Long) : GestureFeedback
    data class Level(val icon: ImageVector, val fraction: Float) : GestureFeedback
    data class Text(val text: String) : GestureFeedback
}

@Composable
private fun GestureLayer(
    enabled: Boolean,
    session: VideoSession,
    onToggle: () -> Unit,
    onFeedback: (GestureFeedback?) -> Unit,
) {
    val context = LocalContext.current
    val player = session.player
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    Box(
        Modifier.fillMaxSize()
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = { onToggle() },
                    onDoubleTap = { offset ->
                        if (!enabled) return@detectTapGestures
                        val delta = when {
                            offset.x < size.width * 0.35f -> -10_000L
                            offset.x > size.width * 0.65f -> 10_000L
                            else -> 0L
                        }
                        if (delta == 0L) {
                            if (player.isPlaying) player.pause() else player.play()
                        } else {
                            val target = (player.currentPosition + delta).coerceAtLeast(0)
                            player.seekTo(target)
                            onFeedback(GestureFeedback.Seek(target, delta))
                        }
                    },
                    // Consumes the long press so releasing it does not count as a tap.
                    onLongPress = { },
                    onPress = {
                        var boosted = false
                        var previous = 1f
                        val boost = scope.launch {
                            delay(450)
                            if (enabled && player.isPlaying) {
                                boosted = true
                                previous = player.playbackParameters.speed
                                player.setPlaybackSpeed(2f)
                                onFeedback(GestureFeedback.Text("2× 倍速"))
                            }
                        }
                        tryAwaitRelease()
                        boost.cancel()
                        if (boosted) {
                            player.setPlaybackSpeed(previous)
                            onFeedback(null)
                        }
                    },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var mode = 0 // 1 seek, 2 brightness, 3 volume
                var startPosition = 0L
                var seekTarget = 0L
                var level = 0f
                var accumulated = androidx.compose.ui.geometry.Offset.Zero
                detectDragGestures(
                    onDragStart = { start ->
                        mode = 0
                        accumulated = androidx.compose.ui.geometry.Offset.Zero
                        startPosition = player.currentPosition
                        seekTarget = startPosition
                        level = if (start.x < size.width / 2) {
                            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                        } else {
                            audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        }
                    },
                    onDragEnd = {
                        if (mode == 1) player.seekTo(seekTarget)
                        onFeedback(null)
                    },
                    onDragCancel = { onFeedback(null) },
                    onDrag = { change, drag ->
                        change.consume()
                        accumulated += drag
                        if (mode == 0) {
                            if (abs(accumulated.x) < 24f && abs(accumulated.y) < 24f) return@detectDragGestures
                            mode = if (abs(accumulated.x) > abs(accumulated.y)) 1 else if (change.position.x < size.width / 2) 2 else 3
                        }
                        when (mode) {
                            1 -> {
                                val duration = player.duration.takeIf { it > 0 } ?: return@detectDragGestures
                                val span = minOf(duration, 10 * 60_000L)
                                val delta = (accumulated.x / size.width * span).toLong()
                                seekTarget = (startPosition + delta).coerceIn(0, duration)
                                onFeedback(GestureFeedback.Seek(seekTarget, seekTarget - startPosition))
                            }
                            2 -> {
                                level = (level - drag.y / (size.height * 0.8f)).coerceIn(0.01f, 1f)
                                activity?.window?.let { w ->
                                    val attrs = w.attributes
                                    attrs.screenBrightness = level
                                    w.attributes = attrs
                                }
                                onFeedback(GestureFeedback.Level(Icons.Rounded.Brightness6, level))
                            }
                            3 -> {
                                level = (level - drag.y / (size.height * 0.8f)).coerceIn(0f, 1f)
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max).toInt(), 0)
                                onFeedback(GestureFeedback.Level(Icons.AutoMirrored.Rounded.VolumeUp, level))
                            }
                        }
                    },
                )
            },
    )
}

@Composable
private fun GestureBadge(feedback: GestureFeedback, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (feedback) {
            is GestureFeedback.Seek -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatClock(feedback.targetMs), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val sign = if (feedback.deltaMs >= 0) "+" else "-"
                Text("$sign${formatClock(abs(feedback.deltaMs))}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge)
            }
            is GestureFeedback.Level -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(feedback.icon, null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                LinearProgressIndicator(progress = { feedback.fraction }, modifier = Modifier.width(140.dp).height(6.dp), drawStopIndicator = {})
            }
            is GestureFeedback.Text -> Text(feedback.text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------- Controls ----------

@Composable
private fun Controls(
    ui: VideoUi,
    playing: Boolean,
    position: Long,
    duration: Long,
    buffered: Long,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSheet: (Sheet) -> Unit,
    onResize: () -> Unit,
    resizeLabel: String,
    onLock: () -> Unit,
    onNext: () -> Unit,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
        )
        // Top bar
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White) }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(ui.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ui.subtitle.isNotEmpty()) {
                    Text(ui.subtitle, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (ui.sources.size > 1) PlayerAction(Icons.Rounded.VideoSettings, "版本") { onSheet(Sheet.Versions) }
            PlayerAction(Icons.Rounded.PictureInPictureAlt, "画中画", onPip)
        }
        // Center transport
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSeekBy(-10_000) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Replay10, "后退 10 秒", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(76.dp),
                shape = if (playing) RoundedCornerShape(24.dp) else CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "暂停" else "播放", Modifier.size(44.dp))
            }
            IconButton(onClick = { onSeekBy(10_000) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Forward10, "快进 10 秒", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        // Bottom bar
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(if (scrubbing) (scrub * duration).toLong() else position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = if (scrubbing) scrub else if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = { scrubbing = true; scrub = it },
                    onValueChangeFinished = { onSeek((scrub * duration).toLong()); scrubbing = false },
                    colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(formatClock(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAction(Icons.Rounded.LockOpen, "锁定", onLock)
                Spacer(Modifier.weight(1f))
                PlayerTextAction(Icons.Rounded.Audiotrack, "音轨") { onSheet(Sheet.Audio) }
                PlayerTextAction(Icons.Rounded.ClosedCaption, "字幕") { onSheet(Sheet.Subtitle) }
                if (ui.danmakuSupported) {
                    PlayerTextAction(if (ui.danmakuOn) Icons.Rounded.ChatBubble else Icons.Rounded.ChatBubbleOutline, "弹幕") { onSheet(Sheet.Danmaku) }
                }
                PlayerTextAction(Icons.Rounded.Speed, if (ui.speed == 1f) "倍速" else "${ui.speed}×") { onSheet(Sheet.Speed) }
                PlayerTextAction(Icons.Rounded.AspectRatio, resizeLabel, onResize)
                if (ui.item?.isEpisode == true) PlayerTextAction(Icons.AutoMirrored.Rounded.PlaylistPlay, "选集") { onSheet(Sheet.Episodes) }
                if (ui.next != null) PlayerTextAction(Icons.Rounded.SkipNext, "下一集", onNext)
            }
        }
    }
}

@Composable
private fun PlayerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label, tint = Color.White) }
}

@Composable
private fun PlayerTextAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------- Sheets ----------

@Composable
private fun PlayerSheet(sheet: Sheet, ui: VideoUi, session: VideoSession, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        when (sheet) {
            Sheet.Audio -> OptionList("音轨", ui.audio.map { it.label to it.selected }) { i -> session.selectTrack(ui.audio[i]); onDismiss() }
            Sheet.Subtitle -> OptionList(
                "字幕",
                listOf("关闭" to !ui.textEnabled) + ui.text.map { it.label to (ui.textEnabled && it.selected) },
            ) { i -> if (i == 0) session.disableSubtitles() else session.selectTrack(ui.text[i - 1]); onDismiss() }
            Sheet.Speed -> OptionList("播放速度", Speeds.map { "${it}×" to (it == ui.speed) }) { i -> session.setSpeed(Speeds[i]); onDismiss() }
            Sheet.Versions -> OptionList("版本", ui.sources.mapIndexed { i, s -> s.name.ifEmpty { s.slotLabel } to (i == ui.sourceIndex) }) { i ->
                session.selectSource(i); onDismiss()
            }
            Sheet.Episodes -> EpisodeSheet(ui, session, onDismiss)
            Sheet.Danmaku -> DanmakuSheet(session)
            Sheet.None -> Unit
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DanmakuSheet(session: VideoSession) {
    val style by session.danmakuStyle.collectAsStateWithLifecycle()
    Text("弹幕", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        DanmakuControls(style, session::setDanmakuStyle)
    }
}

@Composable
private fun OptionList(title: String, options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    if (options.isEmpty()) {
        Text("没有可选项", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn {
        items(options.size) { index ->
            val (label, selected) = options[index]
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EpisodeSheet(ui: VideoUi, session: VideoSession, onDismiss: () -> Unit) {
    val current = ui.item ?: return
    val seriesId = current.seriesId ?: return
    val graph = LocalContext.current.graph
    var episodes by remember(current.seasonId) { mutableStateOf<List<Item>?>(null) }
    LaunchedEffect(current.seasonId) {
        episodes = runCatching { graph.api.episodePage(seriesId, current.seasonId, start = 0, limit = 200).items }.getOrDefault(emptyList())
    }
    Text(current.seasonName ?: "选集", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    val list = episodes
    if (list == null) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    val urls = images()
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.id }) { episode ->
            val selected = episode.id == current.id
            Row(
                Modifier.fillMaxWidth().clip(CardShape)
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { session.playEpisode(episode); onDismiss() }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    urls.primary(episode, 240.dp.px()),
                    Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
                    episode.name, placeholder = Icons.Rounded.Movie,
                )
                Column(Modifier.weight(1f).padding(start = 12.dp).fillMaxHeight()) {
                    Text(
                        listOfNotNull(episode.indexNumber?.let { "$it." }, episode.name).joinToString(" "),
                        style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    episode.overview?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (episode.userData.played) Icon(Icons.Rounded.Check, "已观看", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
