package xyz.etkn.mobile.ui.screens.person

import androidx.compose.foundation.layout.Box
import dev.chrisbanes.haze.rememberHazeState
import xyz.etkn.mobile.ui.components.GlassBackBar
import xyz.etkn.mobile.ui.components.glassBarClearance
import xyz.etkn.mobile.ui.components.glassSource
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.model.ItemsPage
import xyz.etkn.mobile.data.model.PersonRef
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.InlineLoading
import xyz.etkn.mobile.ui.components.PosterCard
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.components.px
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.nav.PersonRoute
import xyz.etkn.mobile.ui.screens.library.PagedViewModel
import xyz.etkn.mobile.ui.theme.Etkn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

class PersonViewModel(graph: AppGraph, private val id: String) : PagedViewModel() {
    private val api = graph.api
    override val pageSize = 40
    private val _person = MutableStateFlow<Item?>(null)
    val person = _person.asStateFlow()

    init {
        viewModelScope.launch { _person.value = runCatching { api.person(id) }.getOrNull() }
        reset()
        followChanges(graph)
    }

    override suspend fun fetch(start: Int, limit: Int): ItemsPage = api.personItems(id, start, limit)
}

@Composable
fun PersonScreen(route: PersonRoute) {
    val vm = graphViewModel(key = route.id) { PersonViewModel(it, route.id) }
    val person by vm.person.collectAsStateWithLifecycle()
    val paged by vm.paged.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val grid = rememberLazyGridState()
    val shouldLoad by remember(paged) {
        derivedStateOf { paged.canLoadMore && (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= paged.items.size - 12 }
    }
    LaunchedEffect(grid, paged) { snapshotFlow { shouldLoad }.collect { if (it) vm.loadMore() } }

    val haze = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = Etkn.space.screen, end = Etkn.space.screen, top = glassBarClearance(), bottom = LocalBottomInset.current),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().glassSource(haze),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") { PersonHeader(route, person) }
            items(paged.items, key = { it.id }) { PosterCard(it, onClick = { nav.open(it) }, width = null) }
            if (paged.loading) item(span = { GridItemSpan(maxLineSpan) }) { InlineLoading() }
        }
        GlassBackBar(route.name, collapsed = true, haze = haze, onBack = nav::back)
    }
}

@Composable
private fun PersonHeader(route: PersonRoute, person: Item?) {
    var expanded by remember { mutableStateOf(false) }
    val tag = person?.imageTags?.get("Primary")
    val url = images().person(PersonRef(route.id, route.name, "", "", tag), 112.dp.px())
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(url, Modifier.size(112.dp).clip(CircleShape), route.name, placeholder = Icons.Rounded.Person)
            Column(Modifier.padding(start = 16.dp)) {
                Text(route.name, style = MaterialTheme.typography.headlineSmall)
                person?.premiereDate?.take(10)?.let {
                    Text("出生 $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        person?.overview?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp).animateContentSize().clickable { expanded = !expanded },
            )
        }
        Text("参演作品", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
    }
}
