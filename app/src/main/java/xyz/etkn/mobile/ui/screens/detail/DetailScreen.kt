package xyz.etkn.mobile.ui.screens.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.model.MediaSource
import xyz.etkn.mobile.data.model.resolutionLabel
import xyz.etkn.mobile.data.update.formatBytes
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.ui.actions.LocalItemMenu
import xyz.etkn.mobile.ui.actions.PopupChoice
import xyz.etkn.mobile.ui.actions.rememberItemPress
import xyz.etkn.mobile.ui.actions.rememberPopupAnchor
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.LocalWindowLayout
import xyz.etkn.mobile.ui.common.WhileStarted
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.common.rememberArtworkSeed
import xyz.etkn.mobile.ui.common.userMessage
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.ArtworkBackButton
import xyz.etkn.mobile.ui.components.ArtworkIconButton
import xyz.etkn.mobile.ui.components.ErrorState
import xyz.etkn.mobile.ui.components.GlassBackBar
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.PersonAvatar
import xyz.etkn.mobile.ui.components.PosterCard
import xyz.etkn.mobile.ui.components.RatingScore
import xyz.etkn.mobile.ui.components.SectionHeader
import xyz.etkn.mobile.ui.components.formatClock
import xyz.etkn.mobile.ui.components.formatDuration
import xyz.etkn.mobile.ui.components.formatRating
import xyz.etkn.mobile.ui.components.glassSource
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.components.px
import xyz.etkn.mobile.ui.components.shelf
import xyz.etkn.mobile.ui.nav.AppNav
import xyz.etkn.mobile.ui.nav.DownloadsRoute
import xyz.etkn.mobile.ui.nav.EpisodesRoute
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.nav.PersonRoute
import xyz.etkn.mobile.ui.nav.TopLevel
import xyz.etkn.mobile.ui.theme.ArtworkTheme
import xyz.etkn.mobile.ui.theme.Etkn
import xyz.etkn.mobile.ui.theme.HeroTitleStyle

@Composable
fun DetailScreen(id: String) {
    val vm = graphViewModel(key = id) { DetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { if (deleted) nav.back() }
    var resumed by remember { mutableStateOf(false) }
    // Coming back from the player: refresh progress / watched state.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (resumed) vm.load() else resumed = true }
    WhileStarted(vm) { vm.followServer() }

    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Failed -> Box(Modifier.fillMaxSize()) {
            ErrorState(s.message, vm::load)
            ArtworkBackButton(nav::back, Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(8.dp))
        }
        is Load.Ready -> DetailContent(s.value, vm, nav)
    }
}

/**
 * The page takes its colours from the artwork ([ArtworkTheme]); on phones the header, meta lines
 * and actions are centred under the logo, on wide windows they stay left-aligned.
 */
@Composable
private fun DetailContent(data: DetailData, vm: DetailViewModel, nav: AppNav) {
    val item = data.item
    val backdrop = images().backdrop(item, 64) ?: images().poster(item, 64)
    val seed = rememberArtworkSeed(backdrop)
    ArtworkTheme(seed) {
        val episodes by vm.episodes.state.collectAsStateWithLifecycle()
        val list = rememberLazyListState()
        val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
        val haze = rememberHazeState()
        val centered = !LocalWindowLayout.current.wide
        val seriesId = if (item.isSeries) item.id else item.seriesId
        val press = rememberItemPress(item)

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().glassSource(haze)) {
                item(key = "header", contentType = "header") { DetailHeader(data, centered) }
                item(key = "actions", contentType = "actions") { ActionRow(data, centered, vm, nav) }
                if (item.mediaSources.size > 1) {
                    item(key = "versions") { VersionRow(item.mediaSources, data.selectedSource, vm::selectSource) }
                }
                item(key = "info", contentType = "info") { InfoBlock(item, centered) }
                if (seriesId != null && (item.isSeries || item.isSeason || item.isEpisode)) {
                    item(key = "episodes", contentType = "episodes") {
                        EpisodeShelf(
                            state = episodes,
                            seasons = if (item.isEpisode) emptyList() else data.seasons,
                            currentId = item.id.takeIf { item.isEpisode },
                            list = vm.episodes,
                            onSeason = { vm.selectSeason(seriesId, it) },
                            onAll = { nav.to(EpisodesRoute(seriesId, episodes.seasonId, item.seriesName ?: item.name, seed)) },
                        )
                    }
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
                shelf("collections", "所属合集", data.collections) { PosterCard(it, onClick = { nav.open(it) }) }
                shelf("similar", "相似推荐", data.similar) { PosterCard(it, onClick = { nav.open(it) }) }
                item(key = "bottom") { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
            }
            GlassBackBar(item.seriesName?.takeIf { item.isEpisode || item.isSeason } ?: item.name, collapsed, haze, nav::back) { onGlass ->
                ArtworkIconButton(Icons.Rounded.Search, "搜索", { nav.top(TopLevel.Search) }, onGlass = onGlass)
                if (press.enabled) ArtworkIconButton(Icons.Rounded.MoreVert, "更多", press::open, press.modifier, onGlass)
            }
        }
    }
}

@Composable
private fun DetailHeader(data: DetailData, centered: Boolean) {
    val item = data.item
    val urls = images()
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
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
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Etkn.space.screen).padding(bottom = 4.dp),
                horizontalAlignment = align,
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
                        alignment = if (centered) Alignment.Center else Alignment.CenterStart,
                    )
                } else {
                    Text(
                        if (item.isEpisode) listOfNotNull(item.episodeCode, item.name).joinToString("  ") else item.name,
                        style = (if (item.isEpisode) MaterialTheme.typography.headlineMedium else HeroTitleStyle)
                            .copy(shadow = Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 12f)),
                        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                MetaLines(item, techSource(data), align)
            }
        }
    }
}

/** The file the tech line describes: the chosen version, or for a series the episode up next. */
private fun techSource(data: DetailData): MediaSource? = with(data) {
    if (item.isSeries || item.isSeason) target?.mediaSources?.firstOrNull() else item.mediaSources.getOrNull(selectedSource)
}

/** Scores by source; year, rating badge and status; then runtime and file; then genres. */
@Composable
private fun MetaLines(item: Item, source: MediaSource?, align: Alignment.Horizontal) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ratings = item.ratings
    Column(horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (ratings.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp, align),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ratings.forEach { RatingScore(it) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            formatRating(item.bestRating)?.takeIf { ratings.isEmpty() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = Etkn.tokens.rating)
                    Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 2.dp))
                }
            }
            item.productionYear?.let { Text(it.toString(), style = MaterialTheme.typography.labelLarge) }
            item.officialRating?.let { RatingBadge(it) }
            item.statusLabel?.takeIf { item.isSeries }?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        }
        val tech = techLine(item, source)
        if (tech.isNotEmpty()) {
            Text(tech.joinToString("  "), style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.genres.isNotEmpty()) {
            Text(item.genres.take(4).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 1)
        }
    }
}

@Composable
private fun RatingBadge(text: String) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

private fun techLine(item: Item, source: MediaSource?): List<String> {
    val out = ArrayList<String>(8)
    item.runtimeMs.takeIf { it > 0 && !item.isSeries }?.let { out += formatDuration(it) }
    source ?: return out
    source.video?.let { v ->
        resolutionLabel(v.width, v.height)?.let(out::add)
        when {
            v.videoRangeType.contains("DOVI", true) -> out += "杜比视界"
            v.videoRangeType.contains("HDR10Plus", true) -> out += "HDR10+"
            v.videoRange.equals("HDR", true) || v.videoRangeType.contains("HDR", true) -> out += "HDR"
        }
        if (v.codec.isNotEmpty()) out += v.codec.uppercase()
        if (v.frameRate > 0f) out += "${Math.round(v.frameRate)}FPS"
    }
    source.audio.firstOrNull { it.isDefault }?.let { a ->
        val codec = when (a.codec.lowercase()) {
            "truehd" -> "TrueHD"; "eac3" -> "DD+"; "ac3" -> "DD"; "dts" -> "DTS"; else -> a.codec.uppercase()
        }
        out += if (a.channels > 2) "$codec ${a.channels - 1}.1" else codec
    }
    if (source.size > 0) out += formatBytes(source.size)
    return out
}

@Composable
private fun ActionRow(data: DetailData, centered: Boolean, vm: DetailViewModel, nav: AppNav) {
    val item = data.item
    val target = if (item.isSeries || item.isSeason) data.target else item
    val source = item.mediaSources.getOrNull(data.selectedSource)?.id
    val resumable = target != null && target.positionMs > 0 && !target.userData.played
    val label = when {
        target == null -> "暂无可播放内容"
        (item.isSeries || item.isSeason) && target.episodeLabel != null -> (if (resumable) "继续播放" else "播放") + target.episodeLabel
        resumable -> "继续播放"
        else -> "播放"
    }
    fun play(resume: Boolean) = target?.let { nav.play(it, resume, sourceId = if (it.id == item.id) source else null) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen).padding(top = 14.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        PlayButton(
            label = label,
            time = target?.positionMs?.takeIf { resumable }?.let(::formatClock),
            progress = if (resumable) target.progress else 0f,
            enabled = target != null,
            onClick = { play(resume = true) },
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // 继续播放 resumes at the saved position; this one starts the same title at 00:00.
            if (resumable) {
                FilledTonalButton(onClick = { play(resume = false) }, modifier = Modifier.height(SmallAction), contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Replay, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("从头播放", style = MaterialTheme.typography.labelLarge)
                }
            }
            FilledTonalIconToggleButton(checked = item.userData.isFavorite, onCheckedChange = { vm.toggleFavorite() }, modifier = Modifier.size(SmallAction)) {
                Icon(if (item.userData.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏", Modifier.size(20.dp))
            }
            FilledTonalIconToggleButton(checked = item.userData.played, onCheckedChange = { vm.togglePlayed() }, modifier = Modifier.size(SmallAction)) {
                Icon(Icons.Rounded.Done, "已观看", Modifier.size(20.dp))
            }
            if (item.trailers.isNotEmpty()) TrailerButton(item.trailers)
            if (item.isMovie || item.isEpisode || item.type == "Video") DownloadButton(item, source)
        }
    }
}

private val SmallAction = 40.dp

/** Queues this title for offline viewing, or opens 下载 when it is already there. Not for private accounts. */
@Composable
private fun DownloadButton(item: Item, sourceId: String?) {
    val graph = LocalContext.current.graph
    val nav = LocalNav.current
    val menu = LocalItemMenu.current
    val scope = rememberCoroutineScope()
    val session = graph.sessions.current ?: return
    if (session.privateAccount) return
    val entries by graph.downloads.entries.collectAsStateWithLifecycle()
    val saved = remember(entries, item.id) { graph.downloads.find(session, item.id) != null }
    FilledTonalIconButton(
        onClick = {
            if (saved) nav.to(DownloadsRoute)
            else scope.launch {
                menu?.notice = runCatching { graph.downloads.enqueue(session, item, sourceId) }.fold({ "已加入下载" }, { it.userMessage() })
            }
        },
        modifier = Modifier.size(SmallAction),
    ) { Icon(if (saved) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (saved) "已下载" else "下载", Modifier.size(20.dp)) }
}

/** Opens the trailer in the app that handles its link (YouTube, a browser); several → pick one. */
@Composable
private fun TrailerButton(trailers: List<String>) {
    val uri = LocalUriHandler.current
    val anchor = rememberPopupAnchor()
    fun open(url: String) = runCatching { uri.openUri(url) }
    FilledTonalIconButton(
        onClick = {
            if (trailers.size == 1) open(trailers.first())
            else anchor.open(trailers.mapIndexed { index, url -> PopupChoice("预告片 ${index + 1}", Icons.Rounded.Theaters) { open(url) } })
        },
        modifier = Modifier.size(SmallAction).then(anchor.modifier),
    ) { Icon(Icons.Rounded.Theaters, "预告片", Modifier.size(20.dp)) }
}

/**
 * Full-width play button whose fill is the watch progress: the watched part in the primary
 * colour, the rest a step lighter, with the resume time after the label.
 */
@Composable
private fun PlayButton(label: String, time: String?, progress: Float, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val watched = scheme.primary
    val rest = lerp(scheme.primary, scheme.surface, 0.3f)
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = scheme.onPrimary, disabledContainerColor = Color.Transparent),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = Modifier.fillMaxWidth().height(height).clip(ButtonDefaults.shape).drawBehind {
            if (!enabled) return@drawBehind drawRect(scheme.onSurface.copy(alpha = 0.12f))
            drawRect(if (progress > 0f) rest else watched)
            if (progress > 0f) drawRect(watched, size = Size(size.width * progress, size.height))
        },
    ) {
        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        time?.let {
            Text(
                it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 10.dp), color = scheme.onPrimary.copy(alpha = 0.78f),
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

/** Tagline and overview (tap to expand). */
@Composable
private fun InfoBlock(item: Item, centered: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen).padding(top = 18.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        item.taglines.firstOrNull()?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, textAlign = align, modifier = Modifier.padding(bottom = 6.dp))
        }
        item.overview?.let { overview ->
            Text(
                overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded },
            )
        }
    }
}
