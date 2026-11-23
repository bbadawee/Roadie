package io.roadie.carui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.roadie.data.RoadieDatabase
import io.roadie.data.SubscriptionEntity
import io.roadie.data.WatchHistoryEntity
import io.roadie.innertube.InnerTubeClient
import io.roadie.innertube.ResolvedStream
import io.roadie.innertube.SponsorBlockClient
import io.roadie.innertube.SponsorSegment
import io.roadie.innertube.StreamResult
import io.roadie.innertube.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CarTab { HOME, SEARCH, SUBSCRIPTIONS, HISTORY }

data class CategoryChip(val label: String, val query: String?)

val DefaultCarCategories = listOf(
    CategoryChip("For You", null),
    CategoryChip("Night Drive", "night drive music 4k"),
    CategoryChip("Music", "top music live mix"),
    CategoryChip("Podcasts", "podcast interview"),
    CategoryChip("Tech", "car tech review"),
    CategoryChip("عربي", "اغاني عربية")
)

data class RoadieUiState(
    val activeTab: CarTab = CarTab.HOME,
    val selectedCategory: String = "For You",
    val homeVideos: List<VideoItem> = emptyList(),
    val searchResults: List<VideoItem> = emptyList(),
    val historyVideos: List<VideoItem> = emptyList(),
    val subscribedChannels: Set<String> = emptySet(),
    val searchQuery: String = "",
    val showKeyboard: Boolean = false,
    val loadingFeed: Boolean = false,
    val loadingStream: Boolean = false,
    val currentStream: ResolvedStream? = null,
    val sponsorSegments: List<SponsorSegment> = emptyList(),
    val isFullscreen: Boolean = false,
    val isParkedMode: Boolean = true,
    val isHardwareDrivingRestricted: Boolean = false,
    val errorMessage: String? = null
)

class RoadieViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val client = InnerTubeClient()
    private val sponsorBlock = SponsorBlockClient()
    private val dao = RoadieDatabase.get(application).dao()

    private val _state = MutableStateFlow(RoadieUiState())
    val state: StateFlow<RoadieUiState> = _state.asStateFlow()

    init {
        loadHome()
        viewModelScope.launch {
            dao.observeHistory().collect { entries ->
                _state.update { it.copy(historyVideos = entries.map(WatchHistoryEntity::toVideoItem)) }
            }
        }
        viewModelScope.launch {
            dao.observeSubscriptions().collect { subs ->
                _state.update { it.copy(subscribedChannels = subs.map { s -> s.channelName }.toSet()) }
            }
        }
    }

    fun selectTab(tab: CarTab) {
        _state.update {
            it.copy(
                activeTab = tab,
                currentStream = null,
                isFullscreen = false,
                showKeyboard = tab == CarTab.SEARCH && it.searchResults.isEmpty()
            )
        }
    }

    fun selectCategory(chip: CategoryChip) {
        _state.update { it.copy(selectedCategory = chip.label) }
        if (chip.query == null) {
            loadHome()
        } else {
            viewModelScope.launch {
                _state.update { it.copy(loadingFeed = true, errorMessage = null) }
                val items = client.search(chip.query)
                _state.update {
                    it.copy(
                        homeVideos = items,
                        loadingFeed = false,
                        errorMessage = if (items.isEmpty()) "Could not load ${chip.label}." else null
                    )
                }
            }
        }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setKeyboardVisible(visible: Boolean) {
        _state.update { it.copy(showKeyboard = visible) }
    }

    fun toggleParkedMode() {
        _state.update { state ->
            if (state.isHardwareDrivingRestricted) {
                state.copy(isParkedMode = false)
            } else {
                state.copy(isParkedMode = !state.isParkedMode)
            }
        }
    }

    fun setDrivingRestricted(restricted: Boolean) {
        _state.update {
            it.copy(
                isHardwareDrivingRestricted = restricted,
                isParkedMode = if (restricted) false else true
            )
        }
    }

    fun toggleFullscreen() {
        _state.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun toggleSubscription(channel: String) {
        if (channel.isBlank()) return
        viewModelScope.launch {
            if (_state.value.subscribedChannels.contains(channel)) {
                dao.unsubscribe(channel)
            } else {
                dao.subscribe(SubscriptionEntity(channelName = channel, subscribedAtMs = System.currentTimeMillis()))
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
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
            _state.update {
                it.copy(
                    loadingStream = true,
                    showKeyboard = false,
                    sponsorSegments = emptyList(),
                    errorMessage = null
                )
            }
            launch {
                val segments = sponsorBlock.fetchSegments(video.id)
                _state.update { state ->
                    if (state.currentStream?.videoId == video.id || state.loadingStream) {
                        state.copy(sponsorSegments = segments)
                    } else {
                        state
                    }
                }
            }
            when (val result = client.resolveStream(video.id)) {
                is StreamResult.Success -> {
                    dao.upsertHistory(
                        WatchHistoryEntity(
                            videoId = video.id,
                            title = result.stream.title,
                            channel = result.stream.channel,
                            durationText = video.durationText,
                            viewCountText = video.viewCountText,
                            thumbnailUrl = result.stream.thumbnailUrl.ifBlank { video.thumbnailUrl },
                            watchedAtMs = System.currentTimeMillis()
                        )
                    )
                    _state.update {
                        it.copy(currentStream = result.stream, loadingStream = false)
                    }
                }
                is StreamResult.Error -> _state.update {
                    it.copy(loadingStream = false, errorMessage = result.reason)
                }
            }
        }
    }

    fun closeWatch() {
        _state.update { it.copy(currentStream = null, isFullscreen = false, sponsorSegments = emptyList()) }
    }
}
