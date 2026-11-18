package xyz.etkn.mobile.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import xyz.etkn.mobile.data.discover.ChipTone
import xyz.etkn.mobile.data.discover.CloudProvider
import xyz.etkn.mobile.data.discover.CloudResource
import xyz.etkn.mobile.data.discover.CloudResources
import xyz.etkn.mobile.data.discover.DiscoverEra
import xyz.etkn.mobile.data.discover.DiscoverFilter
import xyz.etkn.mobile.data.discover.DiscoverItem
import xyz.etkn.mobile.data.discover.DiscoverKind
import xyz.etkn.mobile.data.discover.DiscoverOption
import xyz.etkn.mobile.data.discover.DiscoverSeason
import xyz.etkn.mobile.data.discover.DiscoverSort
import xyz.etkn.mobile.data.discover.DiscoverStatus
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.LocalWindowLayout
import xyz.etkn.mobile.ui.common.Paged
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.common.userMessage
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.CardShape
import xyz.etkn.mobile.ui.components.ErrorState
import xyz.etkn.mobile.ui.components.GlassBackBar
import xyz.etkn.mobile.ui.components.InlineLoading
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.MessageState
import xyz.etkn.mobile.ui.components.SectionHeader
import xyz.etkn.mobile.ui.components.SmallCardShape
import xyz.etkn.mobile.ui.components.formatRating
import xyz.etkn.mobile.ui.components.glass
import xyz.etkn.mobile.ui.components.glassSource
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.components.px
import xyz.etkn.mobile.ui.nav.CloudRoute
import xyz.etkn.mobile.ui.nav.DetailRoute
import xyz.etkn.mobile.ui.nav.DiscoverDetailRoute
import xyz.etkn.mobile.ui.nav.DiscoverPersonRoute
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.theme.Etkn

// ---------- Browse ----------

@Composable
fun DiscoverScreen() {
    val vm = graphViewModel { DiscoverViewModel(it) }
    val kind by vm.kind.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val paged by vm.paged.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val haze = rememberHazeState()
    val grid = rememberLazyGridState()
    val density = LocalDensity.current
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerHeight by remember { mutableStateOf(64.dp) }
    var searching by remember { mutableStateOf(filter.query.isNotEmpty()) }
    var filtering by remember { mutableStateOf(false) }
    val text = rememberTextFieldState(filter.query)
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(kind) { grid.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassSource(haze)) {
            when {
                paged.items.isEmpty() && paged.loading -> LoadingState()
                paged.items.isEmpty() && paged.error != null -> ErrorState(paged.error!!, vm::retry)
                paged.items.isEmpty() && paged.endReached -> MessageState("暂无匹配结果，可调整筛选或搜索词", icon = Icons.Rounded.Search)
                else -> DiscoverGrid(paged, grid, top = status + headerHeight + 12.dp, onLoadMore = vm::loadMore, onRetry = vm::retry) { item ->
                    nav.to(DiscoverDetailRoute(item.id, item.kind.wire, item.title))
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .glass(RoundedCornerShape(26.dp), elevation = 8.dp, state = haze)
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    DiscoverKind.entries.forEachIndexed { index, option ->
                        ToggleButton(
                            checked = option == kind,
                            onCheckedChange = { vm.setKind(option) },
                            shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        ) { Text(option.label) }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    searching = !searching
                    if (!searching && filter.query.isNotEmpty()) {
                        text.setTextAndPlaceCursorAtEnd("")
                        vm.setFilter(filter.copy(query = ""))
                    }
                }) { Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, if (searching) "关闭搜索" else "搜索") }
                IconButton(onClick = { filtering = true }) {
                    BadgedBox(badge = { if (filter.active > 0) Badge { Text(filter.active.toString()) } }) {
                        Icon(Icons.Rounded.Tune, "筛选")
                    }
                }
            }
            if (searching) {
                SearchBarDefaults.InputField(
                    textFieldState = text,
                    searchBarState = rememberSearchBarState(),
                    onSearch = {
                        keyboard?.hide()
                        vm.setFilter(filter.copy(query = it.trim()))
                    },
                    placeholder = { Text("搜索 TMDb 片名") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (filtering) {
        FilterSheet(filter, genres, onDismiss = { filtering = false }) { next ->
            filtering = false
            vm.setFilter(next.copy(query = filter.query))
        }
    }
}

@Composable
private fun DiscoverGrid(
    paged: Paged<DiscoverItem>,
    grid: LazyGridState,
    top: Dp,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (DiscoverItem) -> Unit,
) {
    val shouldLoad by remember(paged) {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            paged.canLoadMore && last >= paged.items.size - 18
        }
    }
    LaunchedEffect(grid, paged) { snapshotFlow { shouldLoad }.collect { if (it) onLoadMore() } }
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Adaptive(if (LocalWindowLayout.current.wide) 140.dp else 108.dp),
        contentPadding = PaddingValues(start = Etkn.space.screen, end = Etkn.space.screen, top = top, bottom = LocalBottomInset.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(paged.items, key = { it.kind.wire + it.id }, contentType = { "discover" }) { item ->
            DiscoverCard(item, onClick = { onOpen(item) })
        }
        if (paged.loading) item(span = { GridItemSpan(maxLineSpan) }) { InlineLoading() }
        if (paged.error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    FilledTonalButton(onClick = onRetry) { Text("加载失败，重试") }
                }
            }
        }
    }
}

/** 2:3 TMDb poster with status ribbon and rating, title and year below (mirrors PosterCard). */
@Composable
fun DiscoverCard(item: DiscoverItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = null) {
    val url = images().tmdb(item.posterPath, "w342")
    Column(
        modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).clip(SmallCardShape).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(CardShape)) {
            Artwork(url, Modifier.fillMaxSize(), item.title, placeholder = Icons.Rounded.Movie)
            item.status?.let { StatusPill(it, Modifier.align(Alignment.TopStart).padding(6.dp)) }
            formatRating(item.vote)?.let { rating ->
                Row(
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(12.dp), tint = Etkn.tokens.rating)
                    Spacer(Modifier.width(2.dp))
                    Text(rating, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
        Text(
            item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        if (item.year.isNotEmpty()) {
            Text(item.year, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp))
        }
    }
}

@Composable
fun StatusPill(status: DiscoverStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        DiscoverStatus.InLibrary -> scheme.primary to scheme.onPrimary
        DiscoverStatus.Subscribed, DiscoverStatus.Wanted -> scheme.tertiary to scheme.onTertiary
        else -> scheme.surfaceContainerHighest to scheme.onSurface
    }
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Text(status.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun FilterSheet(initial: DiscoverFilter, genres: List<DiscoverOption>, onDismiss: () -> Unit, onApply: (DiscoverFilter) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            FilterGroup("排序") {
                DiscoverSort.entries.forEach { sort ->
                    FilterChip(selected = draft.sort == sort, onClick = { draft = draft.copy(sort = sort) }, label = { Text(sort.label) })
                }
            }
            if (genres.isNotEmpty()) {
                FilterGroup("类型") {
                    genres.forEach { genre ->
                        val selected = genre.value in draft.genres
                        FilterChip(
                            selected = selected,
                            onClick = { draft = draft.copy(genres = if (selected) draft.genres - genre.value else draft.genres + genre.value) },
                            label = { Text(genre.label) },
                        )
                    }
                }
            }
            FilterGroup("年代") {
                DiscoverEra.entries.forEach { era ->
                    FilterChip(selected = draft.era == era, onClick = { draft = draft.copy(era = era) }, label = { Text(era.label) })
                }
            }
            FilterGroup("评分") {
                listOf(null, 6, 7, 8).forEach { vote ->
                    FilterChip(
                        selected = draft.minVote == vote,
                        onClick = { draft = draft.copy(minVote = vote) },
                        label = { Text(vote?.let { "$it 分以上" } ?: "不限") },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { draft = draft.copy(hideInLibrary = !draft.hideInLibrary) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("隐藏已入库", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = draft.hideInLibrary, onCheckedChange = { draft = draft.copy(hideInLibrary = it) })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { draft = DiscoverFilter(query = initial.query) }, modifier = Modifier.weight(1f)) { Text("重置") }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text("应用") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// ---------- Detail ----------

@Composable
fun DiscoverDetailScreen(route: DiscoverDetailRoute) {
    val kind = DiscoverKind.entries.firstOrNull { it.wire == route.kind } ?: DiscoverKind.Movie
    val vm = graphViewModel(key = "discover-${route.kind}-${route.id}") { DiscoverDetailViewModel(it, route.id, kind, route.title) }
    val item by vm.item.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    val haze = rememberHazeState()
    var confirm by remember { mutableStateOf<ConfirmSubscribe?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    val missing = extras.seasons.count { it.subscribable }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().glassSource(haze)) {
            item(key = "header", contentType = "header") { DiscoverHeader(item) }
            item(key = "actions", contentType = "actions") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.mediaItemId.isNotEmpty()) {
                        Button(onClick = { nav.to(DetailRoute(item.mediaItemId)) }) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("在片库观看")
                        }
                    }
                    if (item.kind == DiscoverKind.Movie) {
                        val subscribeLabel = if (item.status != null) "再次订阅" else "订阅"
                        val primary = item.mediaItemId.isEmpty()
                        if (primary) {
                            Button(onClick = { confirm = ConfirmSubscribe(null, false) }, enabled = !busy) { Text(subscribeLabel) }
                        } else {
                            FilledTonalButton(onClick = { confirm = ConfirmSubscribe(null, false) }, enabled = !busy) { Text(subscribeLabel) }
                        }
                    } else if (missing > 0) {
                        Button(onClick = { confirm = ConfirmSubscribe(null, true) }, enabled = !busy) { Text("订阅缺失季") }
                    }
                    FilledTonalButton(onClick = { nav.to(CloudRoute(item.id, item.kind.wire, item.title)) }) {
                        Icon(Icons.Rounded.CloudDownload, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("云资源")
                    }
                }
            }
            if (item.tagline.isNotEmpty() || item.overview.isNotEmpty()) {
                item(key = "overview") {
                    Column(Modifier.padding(horizontal = Etkn.space.screen, vertical = 4.dp)) {
                        if (item.tagline.isNotEmpty()) {
                            Text(item.tagline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                        }
                        if (item.overview.isNotEmpty()) ExpandableText(item.overview)
                    }
                }
            }
            if (item.kind == DiscoverKind.Series) {
                item(key = "seasons-head") { SectionHeader("季度", Modifier.padding(top = Etkn.space.section)) }
                if (extras.seasonsLoading) item(key = "seasons-loading") { InlineLoading() }
                items(extras.seasons, key = { "season-${it.number}" }) { season ->
                    SeasonRow(
                        season,
                        busy = busy,
                        onSubscribe = { confirm = ConfirmSubscribe(season, false) },
                        onResources = { nav.to(CloudRoute(item.id, item.kind.wire, item.title, season.number)) },
                    )
                }
            }
            if (extras.cast.isNotEmpty()) {
                item(key = "cast") {
                    Column(Modifier.padding(top = Etkn.space.section)) {
                        SectionHeader("演员")
                        LazyRow(contentPadding = PaddingValues(horizontal = Etkn.space.screen), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(extras.cast.take(40), key = { it.id + it.character }) { member ->
                                CastAvatar(member.name, member.character, member.profilePath) {
                                    if (member.id.isNotEmpty()) nav.to(DiscoverPersonRoute(member.id, member.name))
                                }
                            }
                        }
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
        }
        GlassBackBar(item.title, collapsed, haze, nav::back)
    }

    confirm?.let { request ->
        val target = when {
            request.allMissing -> "${item.title} 的 $missing 个缺失季"
            request.season != null -> "${item.title} · 第 ${request.season.number} 季"
            else -> item.title
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("确认订阅") },
            text = { Text("订阅“$target”？服务端将按订阅配置自动获取资源。") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        val result = if (request.allMissing) vm.subscribeMissing() else vm.subscribe(request.season)
                        message = result.fold({ "订阅已提交" to it }, { "订阅失败" to it.userMessage() })
                    }
                }) { Text("订阅") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } },
        )
    }
    message?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } },
        )
    }
}

private data class ConfirmSubscribe(val season: DiscoverSeason?, val allMissing: Boolean)

@Composable
private fun DiscoverHeader(item: DiscoverItem) {
    val urls = images()
    val tokens = Etkn.tokens
    Box(Modifier.fillMaxWidth()) {
        Artwork(
            urls.tmdb(item.backdropPath, "w1280") ?: urls.tmdb(item.posterPath, "w780"),
            Modifier.fillMaxWidth().then(if (LocalWindowLayout.current.wide) Modifier.height(420.dp) else Modifier.aspectRatio(16f / 10f)),
            item.title,
            placeholder = Icons.Rounded.Movie,
        )
        Box(Modifier.fillMaxWidth().height(140.dp).background(tokens.heroTopScrim))
        Box(Modifier.matchParentSize().background(tokens.detailScrim))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen), verticalAlignment = Alignment.Bottom) {
        Artwork(
            urls.tmdb(item.posterPath, "w342"),
            Modifier.width(108.dp).aspectRatio(2f / 3f).clip(CardShape),
            item.title,
            placeholder = Icons.Rounded.Movie,
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            item.status?.let { StatusPill(it, Modifier.padding(bottom = 6.dp)) }
            Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                item.year.ifEmpty { null },
                item.kind.label,
                formatRating(item.vote)?.let { "★ $it" },
            ).joinToString("  ·  ")
            Text(meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ExpandableText(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(SmallCardShape).clickable { expanded = !expanded },
    )
}

@Composable
private fun SeasonRow(season: DiscoverSeason, busy: Boolean, onSubscribe: () -> Unit, onResources: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            images().tmdb(season.posterPath, "w185"),
            Modifier.width(48.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
            season.name,
            placeholder = Icons.Rounded.Movie,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                if (season.number == 0) "特别篇" else "第 ${season.number} 季" + if (season.name.isNotEmpty()) " · ${season.name}" else "",
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${season.episodes} 集 · ${season.airDate.ifEmpty { "日期待定" }}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            season.status?.let { StatusPill(it, Modifier.padding(top = 4.dp)) }
        }
        IconButton(onClick = onResources) { Icon(Icons.Rounded.CloudDownload, "搜索本季云资源") }
        if (season.subscribable) TextButton(onClick = onSubscribe, enabled = !busy) { Text("订阅") }
    }
}

@Composable
private fun CastAvatar(name: String, role: String, profilePath: String, onClick: () -> Unit) {
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(
            images().tmdb(profilePath, "w185"),
            Modifier.size(76.dp).clip(CircleShape).clickable(onClick = onClick),
            name,
            placeholder = Icons.Rounded.Person,
        )
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        if (role.isNotEmpty()) {
            Text(role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- Person ----------

@Composable
fun DiscoverPersonScreen(route: DiscoverPersonRoute) {
    val vm = graphViewModel(key = "discover-person-${route.id}") { DiscoverPersonViewModel(it, route.id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val grid = rememberLazyGridState()
    val haze = rememberHazeState()
    val collapsed by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 } }
    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            Load.Loading -> LoadingState()
            is Load.Failed -> ErrorState(s.message, vm::load)
            is Load.Ready -> LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(start = Etkn.space.screen, end = Etkn.space.screen, bottom = LocalBottomInset.current + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().glassSource(haze),
            ) {
                item(key = "person", span = { GridItemSpan(maxLineSpan) }) {
                    val person = s.value
                    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 72.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Artwork(
                                images().tmdb(person.profilePath, "w342"),
                                Modifier.width(112.dp).aspectRatio(2f / 3f).clip(CardShape),
                                person.name.ifEmpty { route.name },
                                placeholder = Icons.Rounded.Person,
                            )
                            Text(
                                person.name.ifEmpty { route.name },
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                        if (person.biography.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            ExpandableText(person.biography)
                        }
                        SectionHeader("参演作品", Modifier.padding(top = 16.dp))
                    }
                }
                items(s.value.works, key = { it.kind.wire + it.id }) { work ->
                    DiscoverCard(work, onClick = { nav.to(DiscoverDetailRoute(work.id, work.kind.wire, work.title)) })
                }
            }
        }
        GlassBackBar(route.name, collapsed, haze, nav::back)
    }
}

// ---------- Cloud resources ----------

@Composable
fun CloudScreen(route: CloudRoute) {
    val graph = LocalContext.current.graph
    val kind = DiscoverKind.entries.firstOrNull { it.wire == route.kind } ?: DiscoverKind.Movie
    val season = route.season.takeIf { it >= 0 }
    val vm = graphViewModel(key = "cloud-${route.kind}-${route.id}-${route.season}") {
        val item = graph.discover.get(kind, route.id) ?: DiscoverItem(
            JsonObject(mapOf("id" to JsonPrimitive(route.id), "title" to JsonPrimitive(route.title), "media_type" to JsonPrimitive(kind.mediaType))),
            kind,
        )
        CloudViewModel(it, item, season)
    }
    val results by vm.results.collectAsStateWithLifecycle()
    val submitted by vm.submitted.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val haze = rememberHazeState()
    var source by remember { mutableStateOf<CloudProvider?>(null) }
    var confirm by remember { mutableStateOf<CloudResource?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    val title = route.title + if (season == null) "" else " · 第 $season 季"

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().glassSource(haze),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp,
                bottom = LocalBottomInset.current + 16.dp,
            ),
        ) {
            item(key = "sources") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val total = results.values.sumOf { (it as? Load.Ready)?.value?.size ?: 0 }
                    FilterChip(selected = source == null, onClick = { source = null }, label = { Text("全部 $total") })
                    CloudProvider.entries.forEach { provider ->
                        val count = (results[provider] as? Load.Ready)?.value?.size
                        FilterChip(
                            selected = source == provider,
                            onClick = { source = provider },
                            label = { Text(provider.label + (count?.let { " $it" } ?: "")) },
                        )
                    }
                }
            }
            CloudProvider.entries.filter { source == null || source == it }.forEach { provider ->
                val state = results[provider] ?: Load.Loading
                item(key = "head-${provider.wire}") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = Etkn.space.screen, end = Etkn.space.screen, top = 20.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(provider.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text(
                            when (state) {
                                Load.Loading -> "搜索中…"
                                is Load.Failed -> state.message
                                is Load.Ready -> if (state.value.isEmpty()) "暂无匹配资源" else "${state.value.size} 个版本"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state is Load.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state is Load.Ready) {
                    val bySeason = provider == CloudProvider.SharedPool && kind == DiscoverKind.Series
                    val groups: List<Pair<Int?, List<CloudResource>>> = if (bySeason) {
                        state.value.groupBy { CloudResources.season(it.source) }.entries
                            .sortedWith(compareBy(nullsLast<Int>()) { it.key }).map { it.key to it.value }
                    } else {
                        listOf(null to state.value)
                    }
                    groups.forEach { (number, rows) ->
                        if (bySeason) {
                            item(key = "season-${provider.wire}-$number") {
                                Text(
                                    when (number) { null -> "未标注季"; 0 -> "特别篇"; else -> "第 $number 季" } + " · ${rows.size} 个版本",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = Etkn.space.screen, top = 12.dp, bottom = 2.dp),
                                )
                            }
                        }
                        itemsIndexed(rows, key = { index, _ -> "${provider.wire}-$number-$index" }) { _, row ->
                            ResourceCard(row, submitted = row in submitted) { confirm = row }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 6.dp)
                .glass(RoundedCornerShape(26.dp), elevation = 8.dp, state = haze).height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 16.dp))
        }
    }

    confirm?.let { row ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("确认获取此版本") },
            text = { Text(CloudResources.label(row) + "\n\n将按 ETKN 配置转存或获取并入库；可能消耗服务端资源积分。") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        message = vm.acquire(row).fold(
                            { runId -> "获取任务已提交" to ((if (runId > 0) "任务 #$runId 已创建，" else "获取任务已创建，") + "入库完成或失败时服务端会发送通知。") },
                            { "获取提交失败" to it.userMessage() },
                        )
                    }
                }) { Text("确认获取") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } },
        )
    }
    message?.let { (head, body) ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(head) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } },
        )
    }
}

@Composable
private fun ResourceCard(row: CloudResource, submitted: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen, vertical = 5.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                CloudResources.versionLabel(row).takeIf { it.isNotEmpty() }?.let { TonePill(it, ChipTone.Default) }
                CloudResources.transferLabel(row).takeIf { it.isNotEmpty() }?.let { TonePill(it, if (it == "可转存") ChipTone.Info else ChipTone.Success) }
                if (submitted) TonePill("已提交", ChipTone.Success)
            }
            val chips = CloudResources.chips(row)
            if (chips.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { chips.forEach { TonePill(it.text, it.tone) } }
            }
            val remark = (row.source["remark"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim().orEmpty()
            if (row.provider != CloudProvider.SharedPool && remark.isNotEmpty()) {
                Text(remark, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun TonePill(text: String, tone: ChipTone) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        ChipTone.Success -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ChipTone.Warning -> scheme.primaryContainer to scheme.onPrimaryContainer
        ChipTone.Info -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ChipTone.Default -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
