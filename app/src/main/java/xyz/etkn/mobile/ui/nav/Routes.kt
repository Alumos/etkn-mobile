package xyz.etkn.mobile.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object LibrariesRoute
@Serializable data object MusicRoute
@Serializable data object SearchRoute
@Serializable data object MeRoute

@Serializable data class LibraryRoute(val id: String, val name: String, val collectionType: String? = null)
@Serializable data class DetailRoute(val id: String)
@Serializable data class PersonRoute(val id: String, val name: String)
@Serializable data object FavoritesRoute
@Serializable data class MusicListRoute(
    val parentId: String,
    val tab: String,
    val key: String = "",
    val title: String,
    val tracksOnly: Boolean = false,
)
@Serializable data object NowPlayingRoute
@Serializable data object AccountsRoute

enum class TopLevel(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home(HomeRoute, "首页", Icons.Outlined.Home, Icons.Rounded.Home),
    Libraries(LibrariesRoute, "媒体库", Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
    Music(MusicRoute, "音乐", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    Search(SearchRoute, "搜索", Icons.Outlined.Search, Icons.Rounded.Search),
    Me(MeRoute, "我的", Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle),
}
