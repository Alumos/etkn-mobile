package xyz.etkn.mobile.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.CardShape
import xyz.etkn.mobile.ui.components.ErrorState
import xyz.etkn.mobile.ui.components.GlassBackBar
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.SectionHeader
import xyz.etkn.mobile.ui.components.glassSource
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.nav.DiscoverDetailRoute
import xyz.etkn.mobile.ui.nav.DiscoverPersonRoute
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.theme.Etkn

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
