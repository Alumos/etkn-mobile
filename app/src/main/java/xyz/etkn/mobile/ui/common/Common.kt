package xyz.etkn.mobile.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.graph

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

/** Runs [block] while the screen is at least started (foreground), cancelling it in between. */
@Composable
fun WhileStarted(key: Any?, block: suspend CoroutineScope.() -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block) }
}
