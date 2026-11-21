package xyz.etkn.mobile.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.model.ItemsPage
import xyz.etkn.mobile.data.model.MusicHome
import xyz.etkn.mobile.data.model.Playlist
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.userMessage
import xyz.etkn.mobile.ui.screens.library.PagedViewModel

enum class MusicTab(val wire: String, val label: String) {
    Recommended("recommended", "推荐"),
    Albums("albums", "专辑"),
    Artists("artists", "歌手"),
    Songs("songs", "歌曲"),
    Playlists("playlists", "歌单"),
    Folders("folders", "文件夹"),
}

class MusicLibrariesViewModel(private val graph: AppGraph) : ViewModel() {
    private val _libraries = MutableStateFlow<Load<List<Item>>>(Load.Loading)
    val libraries: StateFlow<Load<List<Item>>> = _libraries.asStateFlow()

    init { load() }

    fun load() {
        _libraries.value = Load.Loading
        viewModelScope.launch {
            _libraries.value = runCatching { graph.api.libraries().filter { it.collectionType == "music" } }
                .fold({ list ->
                    if (graph.music.libraryId.value == null || list.none { it.id == graph.music.libraryId.value }) {
                        list.firstOrNull()?.let { graph.music.selectLibrary(it.id) }
                    }
                    Load.Ready(list)
                }, { Load.Failed(it.userMessage()) })
        }
    }
}

class MusicRecommendedViewModel(private val graph: AppGraph, private val parentId: String) : ViewModel() {
    private val _state = MutableStateFlow<Load<MusicHome>>(Load.Loading)
    val state: StateFlow<Load<MusicHome>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = runCatching { graph.api.musicHome(parentId, 12) }.fold({ Load.Ready(it) }, { Load.Failed(it.userMessage()) })
        }
    }
}

class MusicPlaylistsViewModel(private val graph: AppGraph, private val parentId: String) : ViewModel() {
    private val _state = MutableStateFlow<Load<List<Playlist>>>(Load.Loading)
    val state: StateFlow<Load<List<Playlist>>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = runCatching { graph.api.playlists(parentId) }.fold({ Load.Ready(it) }, { Load.Failed(it.userMessage()) })
        }
    }

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { runCatching { graph.api.createPlaylist(name) }; load() }
    }

    fun delete(playlist: Playlist) {
        viewModelScope.launch { runCatching { graph.api.deletePlaylist(playlist.key) }; load() }
    }
}

/** Paged `U/Music` listing for a tab / group key. Folder entries and artist bio come with page 0. */
class MusicListViewModel(
    private val graph: AppGraph,
    private val parentId: String,
    private val tab: String,
    private val key: String,
    private val tracksOnly: Boolean,
) : PagedViewModel() {
    private val _folders = MutableStateFlow<List<Item>>(emptyList())
    val folders: StateFlow<List<Item>> = _folders.asStateFlow()
    private val _overview = MutableStateFlow<String?>(null)
    val overview: StateFlow<String?> = _overview.asStateFlow()
    override val pageSize = if (tab == "albums" && key.isNotEmpty()) 200 else 60

    init { reset() }

    override suspend fun fetch(start: Int, limit: Int): ItemsPage {
        val page = graph.api.musicList(parentId, tab, key, start, limit, tracksOnly)
        if (start == 0) {
            _folders.value = page.folders
            _overview.value = page.artistOverview
        }
        return ItemsPage(page.items, page.total)
    }

    val tracks: List<Item> get() = paged.value.items.filter { it.isAudio && !it.isMusicGroup }

    fun play(track: Item?) {
        val list = tracks
        if (list.isEmpty()) return
        val index = track?.let { t -> list.indexOfFirst { it.id == t.id } }?.coerceAtLeast(0) ?: 0
        graph.music.play(list, index, "$tab:$key")
    }

    fun shuffle() {
        val list = tracks.shuffled()
        if (list.isNotEmpty()) graph.music.play(list, 0, "$tab:$key")
    }
}
