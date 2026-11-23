package io.roadie.carui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.roadie.innertube.ResolvedStream
import io.roadie.innertube.SponsorSegment
import io.roadie.innertube.VideoItem
import io.roadie.keyboard.CarTouchKeyboard
import kotlinx.coroutines.delay
import kotlin.math.abs

private val AvatarPalette = listOf(
    Color(0xFFEF4444),
    Color(0xFF3B82F6),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899)
)

/** Rotary knob & D-pad focus modifier with visible YouTube-style white focus ring. */
private fun Modifier.carFocusable(
    shape: Shape = RoundedCornerShape(10.dp),
    onClick: () -> Unit
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .clip(shape)
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
            ) {
                onClick()
                true
            } else {
                false
            }
        }
        .then(
            if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier
        )
        .clickable(onClick = onClick)
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun RoadieApp(
    viewModel: RoadieViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var player by remember { mutableStateOf<Player?>(null) }
    var sponsorNotice by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        val uxMonitor = CarUxMonitor(context) { restricted ->
            viewModel.setDrivingRestricted(restricted)
        }
        uxMonitor.start()

        val token = SessionToken(
            context,
            ComponentName(context.packageName, "io.roadie.app.RoadiePlaybackService")
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { runCatching { player = future.get() } },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            uxMonitor.stop()
            MediaController.releaseFuture(future)
            player = null
        }
    }

    LaunchedEffect(state.currentStream?.videoId, player) {
        val activePlayer = player ?: return@LaunchedEffect
        val stream = state.currentStream ?: return@LaunchedEffect
        val item = MediaItem.Builder()
            .setUri(stream.streamUrl)
            .setMediaId(stream.videoId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(stream.title)
                    .setArtist(stream.channel)
                    .setArtworkUri(Uri.parse(stream.thumbnailUrl))
                    .build()
            )
            .build()
        activePlayer.setMediaItem(item)
        activePlayer.prepare()
        activePlayer.play()
    }

    // SponsorBlock segment auto-skip loop
    LaunchedEffect(state.currentStream?.videoId, player, state.sponsorSegments) {
        val activePlayer = player ?: return@LaunchedEffect
        val segments: List<SponsorSegment> = state.sponsorSegments
        if (segments.isEmpty() || state.currentStream == null) return@LaunchedEffect
        while (true) {
            if (activePlayer.isPlaying) {
                val pos = activePlayer.currentPosition
                val hit = segments.firstOrNull { pos in it.startMs until (it.endMs - 400L) }
                if (hit != null) {
                    activePlayer.seekTo(hit.endMs)
                    sponsorNotice = "Skipped ${hit.category}"
                }
            }
            delay(500L)
        }
    }

    LaunchedEffect(sponsorNotice) {
        if (sponsorNotice != null) {
            delay(2500L)
            sponsorNotice = null
        }
    }

    RoadieTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(CarColors.Background)
        ) {
            val metrics = CarViewportMetrics(maxWidth.value, maxHeight.value)
            val baseDensity = LocalDensity.current
            val scaledDensity = remember(baseDensity, metrics.contentScale) {
                Density(
                    density = baseDensity.density * metrics.contentScale,
                    fontScale = baseDensity.fontScale
                )
            }

            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                val inFullscreen = state.isFullscreen && state.currentStream != null
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    if (!inFullscreen) {
                        YouTubeTopBar(
                            searchQuery = state.searchQuery,
                            isWatchOpen = state.currentStream != null,
                            isParkedMode = state.isParkedMode,
                            onBackFromWatch = viewModel::closeWatch,
                            onOpenSearch = {
                                viewModel.selectTab(CarTab.SEARCH)
                                viewModel.setKeyboardVisible(true)
                            },
                            onToggleParkedMode = viewModel::toggleParkedMode,
                            onRefresh = viewModel::loadHome
                        )
                    }

                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (!inFullscreen) {
                            YouTubeMiniRail(
                                activeTab = state.activeTab,
                                onSelectTab = viewModel::selectTab
                            )
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            AnimatedContent(
                                targetState = state.currentStream,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "youtube_watch_transition"
                            ) { stream ->
                                if (stream != null) {
                                    val subscribed = state.subscribedChannels.contains(stream.channel)
                                    YouTubeWatchView(
                                        stream = stream,
                                        player = player,
                                        wideMode = metrics.useWideWatchLayout,
                                        isFullscreen = state.isFullscreen,
                                        isParkedMode = state.isParkedMode,
                                        isSubscribed = subscribed,
                                        onToggleSubscribe = { viewModel.toggleSubscription(stream.channel) },
                                        onToggleFullscreen = viewModel::toggleFullscreen,
                                        onSelectRelated = viewModel::openVideo
                                    )
                                } else {
                                    YouTubeBrowseView(
                                        state = state,
                                        columns = metrics.browseColumns,
                                        onSelectCategory = viewModel::selectCategory,
                                        onOpenVideo = viewModel::openVideo
                                    )
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = state.showKeyboard,
                                enter = slideInVertically { it } + fadeIn(),
                                exit = slideOutVertically { it } + fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                            ) {
                                CarTouchKeyboard(
                                    text = state.searchQuery,
                                    onTextChange = viewModel::updateQuery,
                                    onSubmit = viewModel::submitSearch,
                                    onClose = { viewModel.setKeyboardVisible(false) }
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = sponsorNotice != null,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(20.dp)
                            ) {
                                Surface(
                                    color = Color(0xCC212121),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = sponsorNotice ?: "",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = state.errorMessage != null && (state.currentStream != null || state.homeVideos.isNotEmpty() || state.searchResults.isNotEmpty()),
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                            ) {
                                Surface(
                                    color = CarColors.SurfaceElevated,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(state.errorMessage ?: "", color = CarColors.TextPrimary, fontSize = 14.sp)
                                        Spacer(Modifier.width(12.dp))
                                        IconButton(onClick = viewModel::clearError, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = CarColors.TextPrimary)
                                        }
                                    }
                                }
                            }

                            if (state.loadingStream) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = CarColors.Accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeTopBar(
    searchQuery: String,
    isWatchOpen: Boolean,
    isParkedMode: Boolean,
    onBackFromWatch: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleParkedMode: () -> Unit,
    onRefresh: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isWatchOpen) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .carFocusable(CircleShape, onBackFromWatch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CarColors.TextPrimary)
                }
                Spacer(Modifier.width(6.dp))
            }

            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(21.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = "Roadie",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.width(24.dp))

            Surface(
                color = Color(0xFF121212),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFF303030)),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .carFocusable(CircleShape, onOpenSearch)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = searchQuery.ifEmpty { "Search" },
                        color = if (searchQuery.isEmpty()) CarColors.TextSecondary else CarColors.TextPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 18.dp, end = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF222222)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search", tint = CarColors.TextPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            Surface(
                color = Color(0xFF222222),
                shape = CircleShape,
                modifier = Modifier
                    .size(40.dp)
                    .carFocusable(CircleShape, onOpenSearch)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = CarColors.TextPrimary, modifier = Modifier.size(19.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Surface(
                color = if (isParkedMode) Color(0xFF272727) else CarColors.Accent.copy(alpha = 0.2f),
                shape = CircleShape,
                modifier = Modifier
                    .height(36.dp)
                    .carFocusable(CircleShape, onToggleParkedMode)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isParkedMode) Icons.Default.LocalParking else Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = if (isParkedMode) CarColors.TextPrimary else CarColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isParkedMode) "Parked" else "Drive",
                        color = CarColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .carFocusable(CircleShape, onRefresh),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CarColors.TextPrimary, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = Color(0xFF272727), thickness = 1.dp)
    }
}

@Composable
private fun YouTubeMiniRail(
    activeTab: CarTab,
    onSelectTab: (CarTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(CarColors.Background)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MiniRailButton(
            icon = if (activeTab == CarTab.HOME) Icons.Default.Home else Icons.Outlined.Home,
            label = "Home",
            selected = activeTab == CarTab.HOME,
            onClick = { onSelectTab(CarTab.HOME) }
        )
        Spacer(Modifier.height(6.dp))
        MiniRailButton(
            icon = if (activeTab == CarTab.SEARCH) Icons.Default.Search else Icons.Outlined.Search,
            label = "Search",
            selected = activeTab == CarTab.SEARCH,
            onClick = { onSelectTab(CarTab.SEARCH) }
        )
        Spacer(Modifier.height(6.dp))
        MiniRailButton(
            icon = if (activeTab == CarTab.SUBSCRIPTIONS) Icons.Default.Subscriptions else Icons.Outlined.Subscriptions,
            label = "Subs",
            selected = activeTab == CarTab.SUBSCRIPTIONS,
            onClick = { onSelectTab(CarTab.SUBSCRIPTIONS) }
        )
        Spacer(Modifier.height(6.dp))
        MiniRailButton(
            icon = if (activeTab == CarTab.HISTORY) Icons.Default.History else Icons.Outlined.History,
            label = "History",
            selected = activeTab == CarTab.HISTORY,
            onClick = { onSelectTab(CarTab.HISTORY) }
        )
    }
}

@Composable
private fun MiniRailButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .background(if (selected) Color(0xFF272727) else Color.Transparent, RoundedCornerShape(10.dp))
            .carFocusable(RoundedCornerShape(10.dp), onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun YouTubeBrowseView(
    state: RoadieUiState,
    columns: Int,
    onSelectCategory: (CategoryChip) -> Unit,
    onOpenVideo: (VideoItem) -> Unit
) {
    val videos = when (state.activeTab) {
        CarTab.HOME -> state.homeVideos
        CarTab.SEARCH -> state.searchResults
        CarTab.HISTORY -> state.historyVideos
        CarTab.SUBSCRIPTIONS -> state.homeVideos.filter { state.subscribedChannels.contains(it.channel) }
            .ifEmpty { state.historyVideos.filter { state.subscribedChannels.contains(it.channel) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (state.activeTab == CarTab.HOME) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(DefaultCarCategories, key = { it.label }) { chip ->
                    val selected = state.selectedCategory == chip.label
                    Surface(
                        color = if (selected) Color(0xFFF1F1F1) else Color(0xFF272727),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .carFocusable(RoundedCornerShape(8.dp)) { onSelectCategory(chip) }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chip.label,
                                color = if (selected) Color(0xFF0F0F0F) else Color(0xFFF1F1F1),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.loadingFeed -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CarColors.Accent)
                }
            }
            videos.isEmpty() -> {
                val emptyText = when (state.activeTab) {
                    CarTab.HISTORY -> "Your local watch history is empty."
                    CarTab.SUBSCRIPTIONS -> "Subscribe to channels on any Watch page to see them here."
                    else -> state.errorMessage ?: "No videos available."
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emptyText, color = CarColors.TextSecondary, fontSize = 16.sp)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(videos, key = { it.id }) { video ->
                        YouTubeVideoTile(video = video, onClick = { onOpenVideo(video) })
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeVideoTile(video: VideoItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed || focused) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "yt_tile_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier
                )
                .background(CarColors.Surface)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (video.isLive) {
                Surface(
                    color = Color(0xCCFF0000),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (video.durationText.isNotEmpty()) {
                Surface(
                    color = Color(0xCC000000),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = video.durationText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            ChannelAvatar(name = video.channel, size = 34)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = CarColors.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = listOf(video.channel, video.viewCountText)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    color = CarColors.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChannelAvatar(name: String, size: Int) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Y"
    val color = AvatarPalette[abs(name.hashCode()) % AvatarPalette.size]
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size * 0.44f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun YouTubeWatchView(
    stream: ResolvedStream,
    player: Player?,
    wideMode: Boolean,
    isFullscreen: Boolean,
    isParkedMode: Boolean,
    isSubscribed: Boolean,
    onToggleSubscribe: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSelectRelated: (VideoItem) -> Unit
) {
    val context = LocalContext.current

    val playerColumn: @Composable (Modifier) -> Unit = { mod ->
        Column(modifier = mod) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isParkedMode) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                                setFullscreenButtonClickListener { onToggleFullscreen() }
                            }
                        },
                        update = {
                            it.player = player
                            it.setFullscreenButtonClickListener { onToggleFullscreen() }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(stream.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = stream.title,
                        contentScale = ContentScale.Crop,
                        alpha = 0.25f,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CarColors.Accent, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Audio-only while driving", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Surface(
                    color = Color(0x88000000),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(38.dp)
                        .carFocusable(CircleShape, onToggleFullscreen)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (!isFullscreen) {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stream.title,
                    color = CarColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChannelAvatar(name = stream.channel, size = 32)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stream.channel,
                        color = CarColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = if (isSubscribed) Color(0xFF272727) else Color(0xFFF1F1F1),
                        shape = CircleShape,
                        modifier = Modifier
                            .height(34.dp)
                            .carFocusable(CircleShape, onToggleSubscribe)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSubscribed) "Subscribed" else "Subscribe",
                                color = if (isSubscribed) Color.White else Color(0xFF0F0F0F),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    val upNextColumn: @Composable (Modifier) -> Unit = { mod ->
        LazyColumn(
            modifier = mod,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Text(
                    text = "Up next",
                    color = CarColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            items(stream.relatedVideos, key = { it.id }) { related ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .carFocusable(RoundedCornerShape(8.dp)) { onSelectRelated(related) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .width(146.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CarColors.Surface)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(related.thumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = related.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (related.durationText.isNotEmpty()) {
                            Surface(
                                color = Color(0xCC000000),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = related.durationText,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = related.title,
                            color = CarColors.TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(related.channel, color = CarColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
                        if (related.viewCountText.isNotEmpty()) {
                            Text(related.viewCountText, color = CarColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (isFullscreen) {
        playerColumn(Modifier.fillMaxSize())
    } else if (wideMode) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            playerColumn(Modifier.weight(1.75f).fillMaxHeight())
            upNextColumn(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            playerColumn(Modifier.fillMaxWidth().weight(1.2f))
            upNextColumn(Modifier.fillMaxWidth().weight(1f))
        }
    }
}
