package xyz.etkn.mobile.ui.screens.discover

import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.InputChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.SliderState
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import xyz.etkn.mobile.ui.components.GlassSheet
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import xyz.etkn.mobile.data.discover.DiscoverOptions
import xyz.etkn.mobile.data.discover.DiscoverFilter
import xyz.etkn.mobile.data.discover.DiscoverItem
import xyz.etkn.mobile.data.discover.DiscoverKind
import xyz.etkn.mobile.data.discover.DiscoverOption
import xyz.etkn.mobile.data.discover.DiscoverSort
import xyz.etkn.mobile.data.discover.DiscoverStatus
import xyz.etkn.mobile.ui.common.LocalWindowLayout
import xyz.etkn.mobile.ui.common.Paged
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.CardShape
import xyz.etkn.mobile.ui.components.ErrorState
import xyz.etkn.mobile.ui.components.InlineLoading
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.MessageState
import xyz.etkn.mobile.ui.components.SmallCardShape
import xyz.etkn.mobile.ui.components.formatRating
import xyz.etkn.mobile.ui.components.glass
import xyz.etkn.mobile.ui.components.glassSource
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.nav.DiscoverDetailRoute
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.theme.Etkn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
fun DiscoverScreen() {
    val vm = graphViewModel { DiscoverViewModel(it) }
    val kind by vm.kind.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val paged by vm.paged.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
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
        FilterSheet(filter, options, vm::findPeople, onDismiss = { filtering = false }) { next ->
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

private enum class Picker(val title: String) { Countries("地区"), Language("语言"), Studios("工作室"), Keywords("关键词"), Actors("演员"), Directors("导演") }

/**
 * Every filter the Web 影视探索 panel has. Short vocabularies are chips; long ones (地区, 语言,
 * 工作室, 关键词) open a searchable picker; 演员 / 导演 search TMDb people by name.
 */
@Composable
private fun FilterSheet(
    initial: DiscoverFilter,
    options: DiscoverOptions,
    findPeople: suspend (String) -> List<DiscoverOption>,
    onDismiss: () -> Unit,
    onApply: (DiscoverFilter) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    GlassSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            FilterGroup("排序") {
                DiscoverSort.entries.forEach { sort ->
                    FilterChip(selected = draft.sort == sort, onClick = { draft = draft.copy(sort = sort) }, label = { Text(sort.label) })
                }
            }
            if (options.genres.isNotEmpty()) {
                FilterGroup("风格", trailing = {
                    listOf(false to "包含", true to "排除").forEach { (exclude, label) ->
                        FilterChip(selected = draft.excludeGenres == exclude, onClick = { draft = draft.copy(excludeGenres = exclude) }, label = { Text(label) })
                    }
                }) {
                    options.genres.forEach { genre ->
                        val selected = genre.value in draft.genres
                        FilterChip(
                            selected = selected,
                            onClick = { draft = draft.copy(genres = if (selected) draft.genres - genre.value else draft.genres + genre.value) },
                            label = { Text(genre.label) },
                        )
                    }
                }
            }
            if (options.ratings.isNotEmpty()) {
                FilterGroup("分级") {
                    options.ratings.forEach { rating ->
                        val selected = draft.rating == rating.value
                        FilterChip(
                            selected = selected,
                            onClick = { draft = draft.copy(rating = if (selected) null else rating.value) },
                            label = { Text(rating.label.substringAfter(". ")) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PickerRow(Picker.Countries.title, labels(options.countries, draft.countries)) { picker = Picker.Countries }
            PickerRow(Picker.Language.title, labels(options.languages, setOfNotNull(draft.language))) { picker = Picker.Language }
            PickerRow(Picker.Studios.title, draft.studios.toList()) { picker = Picker.Studios }
            PickerRow(Picker.Keywords.title, draft.keywords.toList()) { picker = Picker.Keywords }
            PickerRow(Picker.Actors.title, draft.actors.map { it.label.substringBefore(" · ") }) { picker = Picker.Actors }
            PickerRow(Picker.Directors.title, draft.directors.map { it.label.substringBefore(" · ") }) { picker = Picker.Directors }
            FilterGroup("发行年份") {}
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                YearField("从", draft.yearFrom, Modifier.weight(1f)) { draft = draft.copy(yearFrom = it) }
                YearField("到", draft.yearTo, Modifier.weight(1f)) { draft = draft.copy(yearTo = it) }
            }
            val vote = draft.minVote ?: 0f
            FilterGroup("最低评分 · ${if (vote > 0f) "%.1f".format(vote) else "不限"}") {}
            val voteState = remember { SliderState(vote, 19, 0f..9.5f) }
            voteState.value = vote
            Slider(state = voteState, onValueChange = { draft = draft.copy(minVote = it.takeIf { v -> v > 0f }) })
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
    when (val open = picker) {
        Picker.Countries -> OptionPickerSheet(open.title, options.countries, draft.countries, multiple = true, onDismiss = { picker = null }) { draft = draft.copy(countries = it) }
        Picker.Language -> OptionPickerSheet(open.title, options.languages, setOfNotNull(draft.language), multiple = false, onDismiss = { picker = null }) { draft = draft.copy(language = it.firstOrNull()) }
        Picker.Studios -> OptionPickerSheet(open.title, options.studios, draft.studios, multiple = true, onDismiss = { picker = null }) { draft = draft.copy(studios = it) }
        Picker.Keywords -> OptionPickerSheet(open.title, options.keywords, draft.keywords, multiple = true, onDismiss = { picker = null }) { draft = draft.copy(keywords = it) }
        Picker.Actors -> PeoplePickerSheet(open.title, draft.actors, findPeople, onDismiss = { picker = null }) { draft = draft.copy(actors = it) }
        Picker.Directors -> PeoplePickerSheet(open.title, draft.directors, findPeople, onDismiss = { picker = null }) { draft = draft.copy(directors = it) }
        null -> Unit
    }
}

private fun labels(options: List<DiscoverOption>, values: Set<String>): List<String> =
    values.map { value -> options.firstOrNull { it.value == value }?.label ?: value }

@Composable
private fun FilterGroup(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (trailing != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { trailing() }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** One line per long vocabulary: its name, what is chosen, and a tap to change it. */
@Composable
private fun PickerRow(title: String, chosen: List<String>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(72.dp))
        Text(
            chosen.joinToString("、").ifEmpty { "不限" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (chosen.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun YearField(label: String, value: Int?, modifier: Modifier, onChange: (Int?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(4)
            text = digits
            onChange(digits.toIntOrNull()?.takeIf { digits.length == 4 && it in 1874..2100 })
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Searchable list of one vocabulary; [multiple] = checkboxes, else picking one closes it. */
@Composable
private fun OptionPickerSheet(
    title: String,
    options: List<DiscoverOption>,
    selected: Set<String>,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onChange: (Set<String>) -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    var search by remember { mutableStateOf("") }
    val shown = remember(options, search) { options.filter { search.isBlank() || it.label.contains(search.trim(), ignoreCase = true) } }
    GlassSheet(onDismissRequest = { onChange(chosen); onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (chosen.isNotEmpty()) TextButton(onClick = { chosen = emptySet() }) { Text("清除") }
            }
            OutlinedTextField(
                value = search, onValueChange = { search = it }, singleLine = true,
                placeholder = { Text("搜索") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(shown, key = { it.value }) { option ->
                    val on = option.value in chosen
                    OptionRow(option.label, on) {
                        if (multiple) {
                            chosen = if (on) chosen - option.value else chosen + option.value
                        } else {
                            chosen = if (on) emptySet() else setOf(option.value)
                            onChange(chosen)
                            onDismiss()
                        }
                    }
                }
            }
            if (multiple) {
                Button(onClick = { onChange(chosen); onDismiss() }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("完成") }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Actors / directors: search TMDb people by name, pick several, remove with a tap. */
@Composable
private fun PeoplePickerSheet(
    title: String,
    selected: List<DiscoverOption>,
    findPeople: suspend (String) -> List<DiscoverOption>,
    onDismiss: () -> Unit,
    onChange: (List<DiscoverOption>) -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    var search by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DiscoverOption>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(search) {
        if (search.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        results = findPeople(search)
        searching = false
    }
    GlassSheet(onDismissRequest = { onChange(chosen); onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (chosen.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    chosen.forEach { person ->
                        InputChip(
                            selected = true,
                            onClick = { chosen = chosen - person },
                            label = { Text(person.label.substringBefore(" · ")) },
                            trailingIcon = { Icon(Icons.Rounded.Close, "移除", Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = search, onValueChange = { search = it }, singleLine = true,
                placeholder = { Text("输入姓名") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (searching) LoadingIndicator(Modifier.size(24.dp)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(results, key = { it.value }) { person ->
                    val on = chosen.any { it.value == person.value }
                    OptionRow(person.label, on) {
                        chosen = if (on) chosen.filterNot { it.value == person.value } else chosen + person
                    }
                }
            }
            Button(onClick = { onChange(chosen); onDismiss() }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("完成") }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (checked) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}
