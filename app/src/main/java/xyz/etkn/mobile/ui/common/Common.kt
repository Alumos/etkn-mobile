package xyz.etkn.mobile.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.ui.nav.LocalBottomInset

@Immutable
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "加载失败，请重试"

/** ViewModel scoped to the current NavBackStackEntry, constructed with the app graph. */
@Composable
inline fun <reified VM : ViewModel> graphViewModel(key: String? = null, crossinline create: (AppGraph) -> VM): VM {
    val graph = LocalContext.current.graph
    return viewModel(key = key) { create(graph) }
}

/** Content padding that clears the status bar on top and the dock / mini player at the bottom. */
@Composable
fun screenPadding(top: Dp = 8.dp, horizontal: Dp = 0.dp): PaddingValues {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return PaddingValues(start = horizontal, end = horizontal, top = status + top, bottom = LocalBottomInset.current)
}

/**
 * Incremental paging state shared by grids: StartIndex advances by the number of rows the
 * server actually returned and stops on an empty page or when TotalRecordCount is reached.
 */
@Immutable
data class Paged<T>(
    val items: List<T> = emptyList(),
    val total: Int = -1,
    val loading: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
) {
    val canLoadMore: Boolean get() = !loading && !endReached && error == null
}
