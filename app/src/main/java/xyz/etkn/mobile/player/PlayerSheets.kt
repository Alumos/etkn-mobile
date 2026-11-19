package xyz.etkn.mobile.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.CardShape
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.components.px
import xyz.etkn.mobile.ui.screens.me.DanmakuControls

@Composable
internal fun PlayerSheet(sheet: Sheet, ui: VideoUi, session: VideoSession, onDismiss: () -> Unit) {
    PlayerSidePanel(onDismiss) {
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
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp), state = rememberLazyListState(options.indexOfFirst { it.second }.coerceAtLeast(0))) {
        items(options.size) { index ->
            val (label, selected) = options[index]
            Row(
                Modifier.fillMaxWidth().clip(CardShape)
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onSelect(index) }.padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
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
    val listState = rememberLazyListState(list.indexOfFirst { it.id == current.id }.coerceAtLeast(0))
    LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
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
