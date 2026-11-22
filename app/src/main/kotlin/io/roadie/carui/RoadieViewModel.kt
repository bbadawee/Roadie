package io.roadie.carui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.roadie.innertube.InnerTubeClient
import io.roadie.innertube.ResolvedStream
import io.roadie.innertube.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CarTab { HOME, SEARCH }

data class RoadieUiState(
    val activeTab: CarTab = CarTab.HOME,
    val homeVideos: List<VideoItem> = emptyList(),
    val searchResults: List<VideoItem> = emptyList(),
    val searchQuery: String = "",
    val showKeyboard: Boolean = false,
    val loadingFeed: Boolean = false,
    val loadingStream: Boolean = false,
    val currentStream: ResolvedStream? = null,
    val isParkedMode: Boolean = true,
    val errorMessage: String? = null
)

class RoadieViewModel(
    private val client: InnerTubeClient = InnerTubeClient()
) : ViewModel() {

    private val _state = MutableStateFlow(RoadieUiState())
    val state: StateFlow<RoadieUiState> = _state.asStateFlow()

    init {
        loadHome()
    }

    fun selectTab(tab: CarTab) {
        _state.update { it.copy(activeTab = tab, showKeyboard = tab == CarTab.SEARCH && it.searchResults.isEmpty()) }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setKeyboardVisible(visible: Boolean) {
        _state.update { it.copy(showKeyboard = visible) }
    }

    fun setParkedMode(parked: Boolean) {
        _state.update { it.copy(isParkedMode = parked) }
    }

    fun loadHome() {
        viewModelScope.launch {
            _state.update { it.copy(loadingFeed = true, errorMessage = null) }
            val items = client.fetchHomeFeed()
            _state.update {
                it.copy(
                    homeVideos = items,
                    loadingFeed = false,
                    errorMessage = if (items.isEmpty()) "Could not load feed. Check network connection." else null
                )
            }
        }
    }

    fun submitSearch() {
        val query = _state.value.searchQuery.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingFeed = true, showKeyboard = false, errorMessage = null) }
            val results = client.search(query)
            _state.update {
                it.copy(
                    searchResults = results,
                    loadingFeed = false,
                    errorMessage = if (results.isEmpty()) "No videos found for \"$query\"." else null
                )
            }
        }
    }

    fun openVideo(video: VideoItem) {
        viewModelScope.launch {
            _state.update { it.copy(loadingStream = true, showKeyboard = false, errorMessage = null) }
            val stream = client.resolveStream(video.id)
            _state.update {
                it.copy(
                    currentStream = stream,
                    loadingStream = false,
                    errorMessage = if (stream == null) "Failed to resolve playback stream." else null
                )
            }
        }
    }

    fun closeWatch() {
        _state.update { it.copy(currentStream = null) }
    }
}
