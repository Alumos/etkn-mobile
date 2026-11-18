package xyz.etkn.mobile.ui.screens.music

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.music.Lyrics
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.formatClock
import xyz.etkn.mobile.ui.components.glass
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.components.px
import xyz.etkn.mobile.ui.nav.LocalNav

/** Polls the shared player position at [intervalMs] while composed. */
@Composable
private fun rememberPlayerClock(intervalMs: Long = 250): Pair<Long, Long> {
    val player = LocalContext.current.graph.music.player
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0
            delay(intervalMs)
        }
    }
    return position to duration
}

@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val music = LocalContext.current.graph.music
    val state by music.state.collectAsStateWithLifecycle()
    val track = state.current ?: return
    val (position, duration) = rememberPlayerClock(500)
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().glass(shape, elevation = 10.dp).clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(images().music(track, 96), Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), track.name, placeholder = Icons.Rounded.MusicNote)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artistLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = music::toggle) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "暂停" else "播放")
            }
            IconButton(onClick = music::next) { Icon(Icons.Rounded.SkipNext, "下一首") }
        }
        LinearWavyProgressIndicator(
            progress = { if (duration > 0) position.toFloat() / duration else 0f },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp),
        )
    }
}

private enum class Pane { Lyrics, Queue }

@Composable
fun NowPlayingScreen() {
    val nav = LocalNav.current
    val graph = LocalContext.current.graph
    val music = graph.music
    val state by music.state.collectAsStateWithLifecycle()
    val track = state.current
    var pane by remember { mutableStateOf<Pane?>(null) }
    if (track == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val urls = images()
    Box(Modifier.fillMaxSize()) {
        // Ambient backdrop: the cover, heavily blurred and darkened.
        Artwork(urls.music(track, 320), Modifier.fillMaxSize().blur(60.dp), null, background = MaterialTheme.colorScheme.surface)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)))))
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth > maxHeight
            if (wide) {
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f)) { PlayerColumn(track, state.isPlaying, state.buffering, state.shuffle, state.repeat, pane, { pane = it }, nav::back, compact = true) }
                    Box(Modifier.weight(1f).fillMaxSize()) { SidePane(pane ?: Pane.Lyrics, track, state.sourceId, state.queue, state.index) }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    AnimatedContent(pane, label = "pane", modifier = Modifier.weight(1f)) { current ->
                        if (current == null) {
                            PlayerColumn(track, state.isPlaying, state.buffering, state.shuffle, state.repeat, null, { pane = it }, nav::back, compact = false)
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                    Artwork(urls.music(track, 96), Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), null, placeholder = Icons.Rounded.MusicNote)
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(track.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artistLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    IconButton(onClick = { pane = null }) { Icon(Icons.Rounded.Close, "收起") }
                                }
                                Box(Modifier.weight(1f)) { SidePane(current, track, state.sourceId, state.queue, state.index) }
                                MiniTransport(state.isPlaying)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerColumn(
    track: Item,
    playing: Boolean,
    buffering: Boolean,
    shuffle: Boolean,
    repeat: Int,
    pane: Pane?,
    onPane: (Pane?) -> Unit,
    onClose: () -> Unit,
    compact: Boolean,
) {
    val music = LocalContext.current.graph.music
    val (position, duration) = rememberPlayerClock()
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "收起") }
            Spacer(Modifier.weight(1f))
            IconToggleButton(checked = pane == Pane.Lyrics, onCheckedChange = { onPane(if (it) Pane.Lyrics else null) }) { Icon(Icons.Rounded.Lyrics, "歌词") }
            IconToggleButton(checked = pane == Pane.Queue, onCheckedChange = { onPane(if (it) Pane.Queue else null) }) { Icon(Icons.Rounded.QueueMusic, "播放队列") }
        }
        Spacer(Modifier.weight(if (compact) 0.3f else 1f))
        val coverScale by animateDpAsState(if (playing) 0.dp else 18.dp, label = "cover")
        Box(Modifier.fillMaxWidth(if (compact) 0.62f else 1f).widthIn(max = 420.dp).aspectRatio(1f).padding(coverScale)) {
            Artwork(
                images().music(track, 900.dp.px().coerceAtMost(1080)),
                Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
                track.name,
                placeholder = Icons.Rounded.MusicNote,
            )
            if (buffering) LoadingIndicator(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.weight(if (compact) 0.2f else 0.6f))
        Column(Modifier.fillMaxWidth()) {
            Text(track.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(track.artistLine.ifEmpty { null }, track.album).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(16.dp))
        Slider(
            value = if (scrubbing) scrub else if (duration > 0) position.toFloat() / duration else 0f,
            onValueChange = { scrubbing = true; scrub = it },
            onValueChangeFinished = { music.seekTo((scrub * duration).toLong()); scrubbing = false },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock(if (scrubbing) (scrub * duration).toLong() else position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatClock(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val active = MaterialTheme.colorScheme.primary
            val idle = MaterialTheme.colorScheme.onSurfaceVariant
            val shuffleTint by animateColorAsState(if (shuffle) active else idle, label = "shuffle")
            val repeatTint by animateColorAsState(if (repeat != Player.REPEAT_MODE_OFF) active else idle, label = "repeat")
            IconButton(onClick = music::toggleShuffle) { Icon(Icons.Rounded.Shuffle, "随机播放", tint = shuffleTint) }
            IconButton(onClick = music::previous, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(36.dp)) }
            FilledIconButton(
                onClick = music::toggle,
                modifier = Modifier.size(84.dp),
                shape = if (playing) RoundedCornerShape(28.dp) else CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "暂停" else "播放", Modifier.size(44.dp))
            }
            IconButton(onClick = music::next, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(36.dp)) }
            IconButton(onClick = music::cycleRepeat) {
                Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "循环", tint = repeatTint)
            }
        }
        Spacer(Modifier.weight(if (compact) 0.3f else 0.8f))
    }
}

@Composable
private fun MiniTransport(playing: Boolean) {
    val music = LocalContext.current.graph.music
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = music::previous) { Icon(Icons.Rounded.SkipPrevious, "上一首") }
        FilledIconButton(onClick = music::toggle, modifier = Modifier.size(60.dp)) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "暂停" else "播放")
        }
        IconButton(onClick = music::next) { Icon(Icons.Rounded.SkipNext, "下一首") }
    }
}

@Composable
private fun SidePane(pane: Pane, track: Item, sourceId: String?, queue: List<Item>, index: Int) {
    when (pane) {
        Pane.Lyrics -> LyricsPane(track, sourceId)
        Pane.Queue -> QueuePane(queue, index)
    }
}

@Composable
private fun LyricsPane(track: Item, sourceId: String?) {
    val graph = LocalContext.current.graph
    var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
    var loaded by remember(track.id) { mutableStateOf(false) }
    LaunchedEffect(track.id, sourceId) {
        if (sourceId == null) return@LaunchedEffect
        val text = runCatching { graph.api.lyrics(track.id, sourceId) }.getOrDefault("")
        lyrics = if (text.isBlank()) null else Lyrics.parse(text)
        loaded = true
    }
    val current = lyrics
    if (current == null || current.lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!loaded) LoadingIndicator() else Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val (position, _) = rememberPlayerClock(200)
    val active = current.indexAt(position)
    val list = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) list.animateScrollToItem((active - 3).coerceAtLeast(0)) }
    LazyColumn(state = list, contentPadding = PaddingValues(vertical = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(current.lines, key = { i, _ -> i }) { i, line ->
            val highlighted = i == active
            val color by animateColorAsState(
                if (highlighted || !current.synced) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                label = "lyric",
            )
            Text(
                line.text,
                style = if (highlighted) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                color = color,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = current.synced) { graph.music.seekTo(line.timeMs) }.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun QueuePane(queue: List<Item>, index: Int) {
    val music = LocalContext.current.graph.music
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (index - 2).coerceAtLeast(0))
    LazyColumn(state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(queue, key = { i, item -> "$i-${item.id}" }) { i, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { TrackRow(item, onClick = { music.skipTo(i) }, horizontalPadding = 0.dp) }
                if (i != index) IconButton(onClick = { music.removeAt(i) }) { Icon(Icons.Rounded.Close, "移出队列") }
            }
        }
    }
}
