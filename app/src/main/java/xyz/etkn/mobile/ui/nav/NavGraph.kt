package xyz.etkn.mobile.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import xyz.etkn.mobile.ui.screens.detail.DetailScreen
import xyz.etkn.mobile.ui.screens.detail.EpisodesScreen
import xyz.etkn.mobile.ui.screens.discover.CloudScreen
import xyz.etkn.mobile.ui.screens.discover.DiscoverDetailScreen
import xyz.etkn.mobile.ui.screens.discover.DiscoverPersonScreen
import xyz.etkn.mobile.ui.screens.discover.DiscoverScreen
import xyz.etkn.mobile.ui.screens.home.HomeScreen
import xyz.etkn.mobile.ui.screens.library.FacetScreen
import xyz.etkn.mobile.ui.screens.library.FavoritesScreen
import xyz.etkn.mobile.ui.screens.library.LibrariesScreen
import xyz.etkn.mobile.ui.screens.library.LibraryScreen
import xyz.etkn.mobile.ui.screens.me.AccountsScreen
import xyz.etkn.mobile.ui.screens.me.DownloadsScreen
import xyz.etkn.mobile.ui.screens.me.MeScreen
import xyz.etkn.mobile.ui.screens.me.SettingsPage
import xyz.etkn.mobile.ui.screens.me.SettingsScreen
import xyz.etkn.mobile.ui.screens.music.MusicHomeScreen
import xyz.etkn.mobile.ui.screens.music.MusicListScreen
import xyz.etkn.mobile.ui.screens.music.NowPlayingScreen
import xyz.etkn.mobile.ui.screens.person.PersonScreen
import xyz.etkn.mobile.ui.screens.search.SearchScreen

/**
 * False while a tab sits in the pager without being the page shown: the neighbours are composed
 * in advance, so a screen must not grab focus (a focused field scrolls the pager to itself) or
 * start work meant for when it is open until this turns true. Always true outside the pager.
 */
val LocalTabActive = compositionLocalOf { true }

/** Every destination inside the shell. */
@Composable
internal fun AppNavHost(controller: NavHostController, tabs: List<TopLevel>, pager: PagerState, verticalTabs: Boolean, modifier: Modifier = Modifier) {
    // Opening a page: theme motion (Material 3 Expressive springs), which keeps its velocity when interrupted.
    val motion = MaterialTheme.motionScheme
    val move = motion.defaultSpatialSpec<IntOffset>()
    val zoom = motion.defaultSpatialSpec<Float>()
    val fade = motion.defaultEffectsSpec<Float>()
    NavHost(
        navController = controller,
        startDestination = TabsRoute,
        modifier = modifier,
        enterTransition = { fadeIn(fade) + slideInHorizontally(move) { it / 12 } },
        exitTransition = { fadeOut(fade) + scaleOut(zoom, targetScale = 0.96f) },
        // Going back is what the predictive back gesture scrubs, so it runs on a fixed timeline
        // the gesture's progress maps onto (springs front-load their motion and jump ahead of the
        // finger). Material's in-app back: the page leaving shrinks to at most 90 % with rounded
        // corners and stays opaque while the finger moves; the page underneath comes back from
        // a slight offset. Letting go plays the rest, and the page leaving fades at the very end.
        popEnterTransition = { slideInHorizontally(backSpec()) { -it / 12 } },
        popExitTransition = { scaleOut(backSpec(), targetScale = 0.9f) + fadeOut(backFade) },
    ) {
        page<TabsRoute> { TabPager(tabs, pager, verticalTabs) }
        // Tabs left out of the dock still open as ordinary pages.
        page<HomeRoute> { HomeScreen() }
        page<LibrariesRoute> { LibrariesScreen() }
        page<MusicRoute> { MusicHomeScreen() }
        page<SearchRoute> { SearchScreen() }
        page<MeRoute> { MeScreen() }
        page<LibraryRoute> { LibraryScreen(it.toRoute()) }
        page<FacetRoute> { FacetScreen(it.toRoute()) }
        page<DetailRoute> { DetailScreen(it.toRoute<DetailRoute>().id) }
        page<EpisodesRoute> { EpisodesScreen(it.toRoute()) }
        page<PersonRoute> { PersonScreen(it.toRoute()) }
        page<FavoritesRoute> { FavoritesScreen() }
        page<MusicListRoute> { MusicListScreen(it.toRoute()) }
        page<NowPlayingRoute> { NowPlayingScreen() }
        page<AccountsRoute> { AccountsScreen() }
        page<DownloadsRoute> { DownloadsScreen() }
        page<DiscoverRoute> { DiscoverScreen() }
        page<DiscoverDetailRoute> { DiscoverDetailScreen(it.toRoute()) }
        page<DiscoverPersonRoute> { DiscoverPersonScreen(it.toRoute()) }
        page<CloudRoute> { CloudScreen(it.toRoute()) }
        page<SettingsRoute> { entry ->
            val page = entry.toRoute<SettingsRoute>().page
            SettingsScreen(SettingsPage.entries.firstOrNull { it.name == page } ?: SettingsPage.Appearance)
        }
    }
}

/**
 * The dock's tabs side by side: switching is a pager scroll, not a navigation, so the screens
 * stay composed (scroll position, loaded data), the neighbour is laid out in advance and a
 * horizontal swipe on a page moves between tabs (bottom dock only). Back returns to 首页 first.
 */
@Composable
private fun TabPager(tabs: List<TopLevel>, pager: PagerState, vertical: Boolean) {
    val scope = rememberCoroutineScope()
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    BackHandler(enabled = pager.currentPage != 0) { scope.launch { pager.animateScrollToPage(0, animationSpec = spring) } }
    val key = { page: Int -> tabs.getOrNull(page)?.name ?: page }
    val content: @Composable PagerScope.(Int) -> Unit = { page -> TabPage(tabs.getOrNull(page), pager.settledPage == page) }
    // Pages run the way the dock does. Beside a side dock they slide vertically and only the dock
    // moves them: a vertical swipe on a page scrolls the page.
    if (vertical) {
        VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, userScrollEnabled = false, key = key, pageContent = content)
    } else {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = key, pageContent = content)
    }
}

@Composable
private fun TabPage(tab: TopLevel?, active: Boolean) {
    // 首页 runs its poster under the side dock and insets the rest itself; other tabs just start after it.
    val inset = LocalStartInset.current
    val ownInset = tab == TopLevel.Home
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(start = if (ownInset) 0.dp else inset)) {
        CompositionLocalProvider(LocalTabActive provides active, LocalStartInset provides if (ownInset) inset else 0.dp) {
            when (tab) {
                TopLevel.Home -> HomeScreen()
                TopLevel.Libraries -> LibrariesScreen()
                TopLevel.Music -> MusicHomeScreen()
                TopLevel.Discover -> DiscoverScreen()
                TopLevel.Search -> SearchScreen()
                TopLevel.Me -> MeScreen()
                null -> Unit
            }
        }
    }
}

/**
 * Every destination draws its own opaque, clipped page. Without it the predictive back gesture
 * shrinks a transparent page over the one underneath and both bleed into each other; the corner
 * radius follows the gesture so the page reads as a card while it is being dragged away.
 */
private inline fun <reified T : Any> NavGraphBuilder.page(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T> { entry -> PageFrame { content(entry) } }

@Composable
private fun AnimatedContentScope.PageFrame(content: @Composable () -> Unit) {
    val corner = transition.animateDp(transitionSpec = { backSpec() }, label = "pageCorner") { if (it == EnterExitState.Visible) 0.dp else 28.dp }
    val background = MaterialTheme.colorScheme.surface
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val radius = corner.value.toPx()
                clip = radius > 0f
                shape = RoundedCornerShape(radius)
            }
            .background(background),
    ) { content() }
}

/** The back timeline (see [AppNavHost]): linear, so the page tracks the gesture's progress. */
private fun <T> backSpec() = tween<T>(BACK_MS, easing = LinearEasing)

/** Opaque while the gesture can still be cancelled; gone by the end of the commit. */
private val backFade = keyframes<Float> {
    durationMillis = BACK_MS
    1f at (BACK_MS * 0.75f).toInt()
    0f at BACK_MS
}

private const val BACK_MS = 320
