package xyz.etkn.mobile.ui.screens.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import xyz.etkn.mobile.ui.components.ArtworkBackButton
import xyz.etkn.mobile.ui.components.GlassBackBar
import xyz.etkn.mobile.ui.components.glassSource
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.model.MediaSource
import xyz.etkn.mobile.data.model.resolutionLabel
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.CardShape
import xyz.etkn.mobile.ui.components.ErrorState
import xyz.etkn.mobile.ui.components.InlineLoading
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.PersonAvatar
import xyz.etkn.mobile.ui.components.Pill
import xyz.etkn.mobile.ui.components.PosterCard
import xyz.etkn.mobile.ui.components.SectionHeader
import xyz.etkn.mobile.ui.components.formatClock
import xyz.etkn.mobile.ui.components.formatDuration
import xyz.etkn.mobile.ui.components.formatRating
import xyz.etkn.mobile.ui.components.glass
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.components.px
import xyz.etkn.mobile.ui.components.shelf
import xyz.etkn.mobile.ui.nav.AppNav
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.nav.PersonRoute
import xyz.etkn.mobile.ui.theme.Etkn
import xyz.etkn.mobile.ui.theme.HeroTitleStyle

@Composable
fun DetailScreen(id: String) {
    val vm = graphViewModel(key = id) { DetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var resumed by remember { mutableStateOf(false) }
    // Coming back from the player: refresh progress / watched state.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (resumed) vm.load() else resumed = true }

    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Failed -> Box(Modifier.fillMaxSize()) {
            ErrorState(s.message, vm::load)
            ArtworkBackButton(nav::back, Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(8.dp))
        }
        is Load.Ready -> DetailContent(s.value, episodes, vm, nav)
    }
}

@Composable
private fun DetailContent(data: DetailData, episodes: EpisodesState, vm: DetailViewModel, nav: AppNav) {
    val item = data.item
    val list = rememberLazyListState()
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    val haze = rememberHazeState()
    val seriesId = if (item.isSeries) item.id else item.seriesId

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().glassSource(haze)) {
            item(key = "header", contentType = "header") { DetailHeader(item) }
            item(key = "actions", contentType = "actions") { ActionRow(data, vm, nav) }
            if (item.mediaSources.size > 1) {
                item(key = "versions") { VersionRow(item.mediaSources, data.selectedSource, vm::selectSource) }
            }
            item(key = "info", contentType = "info") { InfoBlock(item, item.mediaSources.getOrNull(data.selectedSource)) }
            if (seriesId != null && (item.isSeries || item.isSeason || item.isEpisode)) {
                if (data.seasons.size > 1 && !item.isEpisode) {
                    item(key = "seasons") { SeasonRow(data.seasons, episodes.seasonId) { vm.selectSeason(seriesId, it) } }
                }
                episodeList(episodes, item, seriesId, vm, nav)
            }
            if (item.people.isNotEmpty()) {
                item(key = "people") {
                    Column(Modifier.padding(top = Etkn.space.section)) {
                        SectionHeader("演职员")
                        LazyRow(contentPadding = PaddingValues(horizontal = Etkn.space.screen), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(item.people.take(40), key = { it.id + it.role }) { person ->
                                PersonAvatar(person, onClick = { nav.to(PersonRoute(person.id, person.name)) })
                            }
                        }
                    }
                }
            }
            shelf("similar", "相似推荐", data.similar) { PosterCard(it, onClick = { nav.open(it) }) }
            item(key = "bottom") { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
        }
        GlassBackBar(item.seriesName?.takeIf { item.isEpisode || item.isSeason } ?: item.name, collapsed, haze, nav::back)
    }
}

@Composable
private fun DetailHeader(item: Item) {
    val urls = images()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Phones: a tall poster-like header; tablets: a wide band so the details stay on screen.
        val height = if (maxWidth >= 600.dp) (maxWidth * 0.42f).coerceIn(340.dp, 520.dp) else (maxWidth * 1.08f).coerceIn(360.dp, 560.dp)
        val widthPx = maxWidth.px()
        Box(Modifier.fillMaxWidth().height(height)) {
            Artwork(
                urls.backdrop(item, widthPx) ?: urls.landscape(item, widthPx) ?: urls.poster(item, widthPx),
                Modifier.fillMaxSize(),
                item.name,
                placeholder = Icons.Rounded.Movie,
            )
            Box(Modifier.fillMaxWidth().height(140.dp).background(Etkn.tokens.heroTopScrim))
            Box(Modifier.fillMaxSize().background(Etkn.tokens.detailScrim))
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = Etkn.space.screen).padding(bottom = 8.dp),
            ) {
                if (item.isEpisode || item.isSeason) {
                    item.seriesName?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }
                val logo = if (item.isEpisode) null else urls.logo(item, 720)
                if (logo != null) {
                    Artwork(
                        logo, Modifier.fillMaxWidth(0.66f).heightIn(max = 100.dp).height(88.dp), item.name,
                        contentScale = ContentScale.Fit, background = Color.Transparent,
                    )
                } else {
                    Text(
                        if (item.isEpisode) listOfNotNull(item.episodeCode, item.name).joinToString("  ") else item.name,
                        style = (if (item.isEpisode) MaterialTheme.typography.headlineMedium else HeroTitleStyle)
                            .copy(shadow = Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 12f)),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                MetaLine(item)
            }
        }
    }
}

@Composable
private fun MetaLine(item: Item) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        formatRating(item.bestRating)?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = Etkn.tokens.rating)
                Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 2.dp))
            }
        }
        val parts = listOfNotNull(
            item.productionYear?.toString(),
            item.officialRating,
            item.runtimeMs.takeIf { it > 0 && !item.isSeries }?.let(::formatDuration),
            item.status?.takeIf { item.isSeries }?.let { if (it == "Continuing") "连载中" else if (it == "Ended") "已完结" else it },
        )
        if (parts.isNotEmpty()) {
            Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ActionRow(data: DetailData, vm: DetailViewModel, nav: AppNav) {
    val item = data.item
    val target = if (item.isSeries || item.isSeason) data.target else item
    val source = item.mediaSources.getOrNull(data.selectedSource)?.id
    val resumable = target != null && target.positionMs > 0 && !target.userData.played
    val label = when {
        target == null -> "暂无可播放内容"
        (item.isSeries || item.isSeason) && target.episodeCode != null ->
            (if (resumable) "继续 " else "播放 ") + target.episodeCode
        resumable -> "继续播放 ${formatClock(target.positionMs)}"
        else -> "播放"
    }
    Column(Modifier.padding(horizontal = Etkn.space.screen).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { target?.let { nav.play(it, resume = true, sourceId = if (target.id == item.id) source else null) } },
                enabled = target != null,
                modifier = Modifier.weight(1f).height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (resumable) {
                FilledTonalIconButton(
                    onClick = { target?.let { nav.play(it, resume = false, sourceId = if (target.id == item.id) source else null) } },
                    modifier = Modifier.size(ButtonDefaults.MediumContainerHeight),
                ) { Icon(Icons.Rounded.Replay, "从头播放") }
            }
            FilledTonalIconToggleButton(
                checked = item.userData.isFavorite,
                onCheckedChange = { vm.toggleFavorite() },
                modifier = Modifier.size(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(if (item.userData.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏")
            }
            FilledTonalIconToggleButton(
                checked = item.userData.played,
                onCheckedChange = { vm.togglePlayed() },
                modifier = Modifier.size(ButtonDefaults.MediumContainerHeight),
            ) { Icon(Icons.Rounded.Done, "已观看") }
        }
        if (resumable && target != null && target.progress > 0f) {
            LinearProgressIndicator(
                progress = { target.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun VersionRow(sources: List<MediaSource>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Etkn.space.screen),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        items(sources.size) { index ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(sources[index].slotLabel.ifEmpty { "版本 ${index + 1}" }, maxLines = 1) },
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
private fun InfoBlock(item: Item, source: MediaSource?) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen).padding(top = 16.dp)) {
        val chips = remember(source) { techChips(source) }
        if (chips.isNotEmpty() || item.genres.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { Pill(it, container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer) }
                item.genres.take(4).forEach { Pill(it) }
            }
            Spacer(Modifier.height(14.dp))
        }
        item.taglines.firstOrNull()?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
        }
        item.overview?.let { overview ->
            Text(
                overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize().clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded },
            )
        }
    }
}

private fun techChips(source: MediaSource?): List<String> {
    source ?: return emptyList()
    val out = ArrayList<String>(4)
    source.video?.let { v ->
        resolutionLabel(v.width, v.height)?.let(out::add)
        when {
            v.videoRangeType.contains("DOVI", true) -> out += "杜比视界"
            v.videoRangeType.contains("HDR10Plus", true) -> out += "HDR10+"
            v.videoRange.equals("HDR", true) || v.videoRangeType.contains("HDR", true) -> out += "HDR"
        }
        if (v.codec.isNotEmpty()) out += v.codec.uppercase()
    }
    source.audio.firstOrNull { it.isDefault }?.let { a ->
        val codec = when (a.codec.lowercase()) {
            "truehd" -> "TrueHD"; "eac3" -> "DD+"; "ac3" -> "DD"; "dts" -> "DTS"; else -> a.codec.uppercase()
        }
        out += if (a.channels > 2) "$codec ${a.channels - 1}.1" else codec
    }
    return out
}

@Composable
private fun SeasonRow(seasons: List<Item>, selected: String?, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Etkn.space.screen),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = Etkn.space.section),
    ) {
        items(seasons, key = { it.id }) { season ->
            ToggleButton(checked = season.id == selected, onCheckedChange = { onSelect(season.id) }) {
                Text(season.name, maxLines = 1)
            }
        }
    }
}

private fun LazyListScope.episodeList(episodes: EpisodesState, item: Item, seriesId: String, vm: DetailViewModel, nav: AppNav) {
    item(key = "episodes-head") {
        SectionHeader(
            if (item.isEpisode) "本季剧集" else "剧集" + if (episodes.total > 0) " · ${episodes.total}" else "",
            Modifier.padding(top = Etkn.space.section - 8.dp),
        )
    }
    items(episodes.items, key = { "ep-" + it.id }, contentType = { "episode" }) { episode ->
        EpisodeRow(
            episode,
            current = episode.id == item.id,
            onPlay = { nav.play(episode) },
            onOpen = { nav.open(episode) },
            onTogglePlayed = { vm.toggleEpisodePlayed(episode) },
        )
    }
    when {
        episodes.loading -> item(key = "ep-loading") { InlineLoading() }
        episodes.error != null -> item(key = "ep-error") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                FilledTonalButton(onClick = { vm.selectSeason(seriesId, episodes.seasonId) }) { Text("剧集加载失败，重试") }
            }
        }
        episodes.hasMore -> item(key = "ep-more") {
            LaunchedEffect(episodes.items.size) { vm.moreEpisodes(seriesId) }
            InlineLoading()
        }
    }
}

@Composable
private fun EpisodeRow(episode: Item, current: Boolean, onPlay: () -> Unit, onOpen: () -> Unit, onTogglePlayed: () -> Unit) {
    val url = images().primary(episode, 320.dp.px()) ?: images().landscape(episode, 320.dp.px())
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onOpen)
            .background(if (current) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = Etkn.space.screen, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(CardShape).clickable(onClick = onPlay)) {
            Artwork(url, Modifier.fillMaxSize(), episode.name, placeholder = Icons.Rounded.Movie)
            Icon(
                Icons.Rounded.PlayArrow, "播放",
                Modifier.align(Alignment.Center).size(34.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).padding(6.dp),
                tint = Color.White,
            )
            if (episode.progress > 0f && !episode.userData.played) {
                LinearProgressIndicator(
                    progress = { episode.progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp).height(3.dp),
                    trackColor = Etkn.tokens.onImageTrack,
                    drawStopIndicator = {},
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                listOfNotNull(episode.indexNumber?.let { "$it." }, episode.name).joinToString(" "),
                style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                episode.runtimeMs.takeIf { it > 0 }?.let(::formatDuration),
                episode.premiereDate?.take(10),
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            episode.overview?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onTogglePlayed) {
            Icon(
                if (episode.userData.played) Icons.Rounded.CheckCircle else Icons.Rounded.Done,
                if (episode.userData.played) "标为未看" else "标为已看",
                tint = if (episode.userData.played) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
