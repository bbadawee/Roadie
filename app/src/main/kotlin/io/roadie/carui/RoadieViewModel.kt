package io.roadie.carui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.roadie.data.BookmarkEntity
import io.roadie.data.RoadieDatabase
import io.roadie.data.SubscriptionEntity
import io.roadie.data.SubscriptionImporter
import io.roadie.data.WatchHistoryEntity
import io.roadie.innertube.DeArrowClient
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

enum class CarTab { HOME, SEARCH, SUBSCRIPTIONS, SAVED, HISTORY }

enum class VideoQualityPreset(
    val label: String,
    val maxWidth: Int?,
    val maxHeight: Int?
) {
    AUTO("Auto", null, null),
    P1080("1080p", 1920, 1080),
    P720("720p", 1280, 720),
    P480("480p", 854, 480),
    P360("360p", 640, 360)
}

data class CategoryChip(val label: String, val query: String?)

val DefaultCarCategories = listOf(
    CategoryChip("For You", null),
    CategoryChip("Night Drive", "night drive music 4k"),
    CategoryChip("Music", "top music live mix"),
    CategoryChip("Podcasts", "podcast interview"),
    CategoryChip("Tech", "car tech review"),
    CategoryChip("عربي", "اغاني عربية")
)

private val SpeedSteps = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)

data class RoadieUiState(
    val activeTab: CarTab = CarTab.HOME,
    val selectedCategory: String = "For You",
    val homeVideos: List<VideoItem> = emptyList(),
    val searchResults: List<VideoItem> = emptyList(),
    val historyVideos: List<VideoItem> = emptyList(),
    val bookmarkedVideos: List<VideoItem> = emptyList(),
    val bookmarkedIds: Set<String> = emptySet(),
    val queue: List<VideoItem> = emptyList(),
    val subscribedChannels: Set<String> = emptySet(),
    val searchQuery: String = "",
    val showKeyboard: Boolean = false,
    val showSettings: Boolean = false,
    val loadingFeed: Boolean = false,
    val loadingStream: Boolean = false,
    val currentStream: ResolvedStream? = null,
    val deArrowTitle: String? = null,
    val resumePositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val videoQuality: VideoQualityPreset = VideoQualityPreset.AUTO,
    val sponsorSegments: List<SponsorSegment> = emptyList(),
    val isFullscreen: Boolean = false,
    val isParkedMode: Boolean = true,
    val isHardwareDrivingRestricted: Boolean = false,
    val audioOnlyMode: Boolean = false,
    val hotspotSaverMode: Boolean = false,
    val deArrowEnabled: Boolean = true,
    val companionServerUrl: String? = null,
    val statusNotice: String? = null,
    val errorMessage: String? = null
)

class RoadieViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val client = InnerTubeClient()
    private val sponsorBlock = SponsorBlockClient()
    private val deArrow = DeArrowClient()
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
        viewModelScope.launch {
            dao.observeBookmarks().collect { entries ->
                _state.update {
                    it.copy(
                        bookmarkedVideos = entries.map(BookmarkEntity::toVideoItem),
                        bookmarkedIds = entries.map { b -> b.videoId }.toSet()
                    )
                }
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

    fun cyclePlaybackSpeed() {
        _state.update { state ->
            val idx = SpeedSteps.indexOf(state.playbackSpeed)
            val next = SpeedSteps[(idx + 1) % SpeedSteps.size]
            state.copy(playbackSpeed = next)
        }
    }

    fun savePlaybackProgress(videoId: String, positionMs: Long) {
        if (positionMs < 1000L) return
        viewModelScope.launch {
            dao.updatePosition(videoId, positionMs, System.currentTimeMillis())
        }
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

    fun clearStatusNotice() {
        _state.update { it.copy(statusNotice = null) }
    }

    fun setSettingsVisible(visible: Boolean) {
        _state.update { it.copy(showSettings = visible) }
    }

    fun toggleAudioOnlyMode() {
        _state.update { it.copy(audioOnlyMode = !it.audioOnlyMode) }
    }

    fun toggleHotspotSaverMode() {
        _state.update {
            val next = !it.hotspotSaverMode
            it.copy(
                hotspotSaverMode = next,
                videoQuality = if (next) VideoQualityPreset.P480 else VideoQualityPreset.AUTO,
                statusNotice = if (next) "Hotspot Saver: capped to 480p" else "Hotspot Saver off"
            )
        }
    }

    fun cycleVideoQuality() {
        _state.update { state ->
            val presets = VideoQualityPreset.entries
            val next = presets[(state.videoQuality.ordinal + 1) % presets.size]
            state.copy(
                videoQuality = next,
                hotspotSaverMode = next == VideoQualityPreset.P480 || next == VideoQualityPreset.P360,
                statusNotice = "Quality: ${next.label}"
            )
        }
    }

    fun toggleBookmark(video: VideoItem) {
        if (video.id.isBlank()) return
        viewModelScope.launch {
            if (_state.value.bookmarkedIds.contains(video.id)) {
                dao.deleteBookmark(video.id)
                _state.update { it.copy(statusNotice = "Removed from Saved") }
            } else {
                dao.upsertBookmark(
                    BookmarkEntity(
                        videoId = video.id,
                        title = video.title,
                        channel = video.channel,
                        durationText = video.durationText,
                        viewCountText = video.viewCountText,
                        thumbnailUrl = video.thumbnailUrl,
                        savedAtMs = System.currentTimeMillis()
                    )
                )
                _state.update { it.copy(statusNotice = "Saved locally") }
            }
        }
    }

    fun toggleCurrentBookmark() {
        val stream = _state.value.currentStream ?: return
        toggleBookmark(
            VideoItem(
                id = stream.videoId,
                title = _state.value.deArrowTitle ?: stream.title,
                channel = stream.channel,
                thumbnailUrl = stream.thumbnailUrl
            )
        )
    }

    fun toggleDeArrow() {
        _state.update { it.copy(deArrowEnabled = !it.deArrowEnabled) }
    }

    fun setCompanionServerUrl(url: String?) {
        _state.update { it.copy(companionServerUrl = url) }
    }

    fun enqueueNext(video: VideoItem) {
        _state.update { state ->
            val filtered = state.queue.filterNot { it.id == video.id }
            state.copy(
                queue = listOf(video) + filtered,
                statusNotice = "Playing next: ${video.title}"
            )
        }
    }

    fun enqueueEnd(video: VideoItem) {
        _state.update { state ->
            if (state.queue.any { it.id == video.id }) return@update state
            state.copy(
                queue = state.queue + video,
                statusNotice = "Added to queue: ${video.title}"
            )
        }
    }

    fun removeFromQueue(videoId: String) {
        _state.update { state ->
            state.copy(queue = state.queue.filterNot { it.id == videoId })
        }
    }

    fun clearQueue() {
        _state.update { it.copy(queue = emptyList()) }
    }

    fun playNextFromQueue() {
        val next = _state.value.queue.firstOrNull() ?: return
        _state.update { it.copy(queue = it.queue.drop(1)) }
        openVideo(next)
    }

    fun importSubscriptions(rawContent: String) {
        val channels = SubscriptionImporter.parseChannelNames(rawContent)
        if (channels.isEmpty()) {
            _state.update { it.copy(errorMessage = "No channels found. Select a YouTube Takeout CSV or NewPipe JSON file.") }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.subscribeAll(channels.map { SubscriptionEntity(channelName = it, subscribedAtMs = now) })
            _state.update { it.copy(statusNotice = "Imported ${channels.size} subscriptions") }
        }
    }

    fun exportSubscriptionsJson(): String =
        SubscriptionImporter.exportToJson(_state.value.subscribedChannels)

    fun queueOrPlayVideoId(videoId: String, autoPlayIfIdle: Boolean = true) {
        if (videoId.isBlank()) return
        viewModelScope.launch {
            val existing = _state.value.homeVideos.firstOrNull { it.id == videoId }
                ?: _state.value.searchResults.firstOrNull { it.id == videoId }
            if (existing != null) {
                if (autoPlayIfIdle && _state.value.currentStream == null) {
                    openVideo(existing)
                } else {
                    enqueueEnd(existing)
                }
                return@launch
            }
            when (val result = client.resolveStream(videoId)) {
                is StreamResult.Success -> {
                    val item = VideoItem(
                        id = result.stream.videoId,
                        title = result.stream.title,
                        channel = result.stream.channel,
                        thumbnailUrl = result.stream.thumbnailUrl
                    )
                    if (autoPlayIfIdle && _state.value.currentStream == null) {
                        openVideo(item)
                    } else {
                        enqueueEnd(item)
                    }
                }
                is StreamResult.Error -> _state.update { it.copy(errorMessage = result.reason) }
            }
        }
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
                    deArrowTitle = null,
                    resumePositionMs = 0L,
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
            if (_state.value.deArrowEnabled) {
                launch {
                    val cleanTitle = deArrow.fetchCleanTitle(video.id)
                    if (cleanTitle != null) {
                        _state.update { state ->
                            if (state.currentStream?.videoId == video.id || state.loadingStream) {
                                state.copy(deArrowTitle = cleanTitle)
                            } else {
                                state
                            }
                        }
                    }
                }
            }
            val savedPos = dao.getLastPosition(video.id) ?: 0L
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
                            watchedAtMs = System.currentTimeMillis(),
                            lastPositionMs = savedPos
                        )
                    )
                    _state.update {
                        it.copy(
                            currentStream = result.stream,
                            resumePositionMs = savedPos,
                            loadingStream = false
                        )
                    }
                }
                is StreamResult.Error -> _state.update {
                    it.copy(loadingStream = false, errorMessage = result.reason)
                }
            }
        }
    }

    fun closeWatch() {
        _state.update {
            it.copy(
                currentStream = null,
                deArrowTitle = null,
                isFullscreen = false,
                sponsorSegments = emptyList(),
                resumePositionMs = 0L
            )
        }
    }
}
