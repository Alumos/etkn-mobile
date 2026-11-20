package xyz.etkn.mobile.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
    // Pages turn like the leaves of a book (see PageFrame): NavHost itself only keeps both pages
    // composed for the length of the turn and records which way it goes. All six transitions are
    // set, so none of NavHost's defaults (fades, the back gesture's shrink) ever runs.
    val turn = remember { PageTurn() }
    CompositionLocalProvider(LocalPageTurn provides turn) {
        NavHost(
            navController = controller,
            startDestination = TabsRoute,
            modifier = modifier,
            enterTransition = { turn.back = false; EnterTransition.None },
            exitTransition = { turn.back = false; ExitTransition.KeepUntilTransitionsFinished },
            popEnterTransition = { turn.back = true; EnterTransition.None },
            popExitTransition = { turn.back = true; ExitTransition.KeepUntilTransitionsFinished },
            // The back gesture has its own pair, defaulting to a 70 % shrink of the leaving page;
            // it must be the same turn as any other back.
            predictivePopEnterTransition = { turn.back = true; EnterTransition.None },
            predictivePopExitTransition = { turn.back = true; ExitTransition.KeepUntilTransitionsFinished },
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
 * Every destination draws its own opaque page, so a page turning over another never shows through.
 */
private inline fun <reified T : Any> NavGraphBuilder.page(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T> { entry -> PageFrame { content(entry) } }

/** Which way the running page transition goes: set where NavHost picks a transition, read while drawing. */
private class PageTurn {
    var back = false
}

private val LocalPageTurn = staticCompositionLocalOf { PageTurn() }

/**
 * The page turn, the same for every page and both directions. The page on top is a leaf hinged
 * on its right edge: opening lays it down over the current page, going back lifts it off to
 * reveal the page underneath, which brightens as it is uncovered. Back is what the predictive
 * back gesture scrubs, so it runs on a fixed linear timeline the gesture's progress maps onto and
 * the leaf's free edge follows the finger. Only layer properties change (rotation, alpha, one
 * dim rect), so a turn costs no relayout or recomposition per frame.
 */
@Composable
private fun AnimatedContentScope.PageFrame(content: @Composable () -> Unit) {
    val turn = LocalPageTurn.current
    // 1 = lying flat on the stack; 0 = turned off it (where an opening leaf starts, a leaving one ends).
    val flat = transition.animateFloat(transitionSpec = { if (turn.back) BackTurn else OpenTurn }, label = "pageTurn") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    // The leaf is the page entering when opening and the page leaving when going back.
    fun onTop() = (transition.targetState == EnterExitState.Visible) != turn.back
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val lifted = 1f - flat.value
                if (lifted > 0f && onTop()) {
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    cameraDistance = 14f * density
                    rotationY = MAX_TURN_DEGREES * lifted
                    alpha = ((1f - lifted) / FADE_SPAN).coerceIn(0f, 1f)
                } else {
                    rotationY = 0f
                    alpha = 1f
                }
            }
            .drawWithContent {
                drawContent()
                val lifted = 1f - flat.value
                if (lifted > 0f && !onTop()) drawRect(Color.Black, alpha = UNDER_DIM * lifted)
            }
            .background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

private val OpenTurn = tween<Float>(380, easing = FastOutSlowInEasing)
private val BackTurn = tween<Float>(320, easing = LinearEasing)
private const val MAX_TURN_DEGREES = 80f
/** The leaf stays opaque until the last part of its turn, then fades out edge-on. */
private const val FADE_SPAN = 0.35f
/** How dark the page underneath is while fully covered. */
private const val UNDER_DIM = 0.3f
