package io.roadie.carui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.roadie.innertube.VideoItem
import io.roadie.keyboard.CarTouchKeyboard

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun RoadieApp(
    viewModel: RoadieViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var player by remember { mutableStateOf<Player?>(null) }

    DisposableEffect(context) {
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

    RoadieTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(CarColors.Background, CarColors.BackgroundAlt)
                    )
                )
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
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FloatingCarRail(
                        activeTab = state.activeTab,
                        isParkedMode = state.isParkedMode,
                        onSelectTab = viewModel::selectTab,
                        onToggleParkedMode = viewModel::toggleParkedMode,
                        onRefresh = viewModel::loadHome
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AnimatedContent(
                            targetState = state.currentStream,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "watch_transition"
                        ) { stream ->
                            if (stream != null) {
                                CarWatchView(
                                    stream = stream,
                                    player = player,
                                    wideMode = metrics.useWideWatchLayout,
                                    isParkedMode = state.isParkedMode,
                                    onBack = viewModel::closeWatch,
                                    onSelectRelated = viewModel::openVideo
                                )
                            } else {
                                CarBrowseContent(
                                    state = state,
                                    columns = metrics.browseColumns,
                                    onSelectCategory = viewModel::selectCategory,
                                    onOpenSearchKeyboard = { viewModel.setKeyboardVisible(true) },
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
                            visible = state.errorMessage != null && (state.currentStream != null || state.homeVideos.isNotEmpty() || state.searchResults.isNotEmpty()),
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            Surface(
                                color = Color(0xFF3B131B),
                                border = BorderStroke(1.dp, CarColors.Accent.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(state.errorMessage ?: "", color = Color.White, fontSize = 14.sp)
                                    Spacer(Modifier.width(12.dp))
                                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                                    }
                                }
                            }
                        }

                        if (state.loadingStream) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.6f)),
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

@Composable
private fun FloatingCarRail(
    activeTab: CarTab,
    isParkedMode: Boolean,
    onSelectTab: (CarTab) -> Unit,
    onToggleParkedMode: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        color = CarColors.Surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CarColors.SurfaceBorder),
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarColors.AccentSoft)
                    .border(1.dp, CarColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Roadie", tint = CarColors.Accent, modifier = Modifier.size(26.dp))
            }

            Spacer(Modifier.height(22.dp))

            RailItem(
                icon = Icons.Default.Home,
                label = "Home",
                selected = activeTab == CarTab.HOME,
                onClick = { onSelectTab(CarTab.HOME) }
            )
            Spacer(Modifier.height(10.dp))
            RailItem(
                icon = Icons.Default.Search,
                label = "Search",
                selected = activeTab == CarTab.SEARCH,
                onClick = { onSelectTab(CarTab.SEARCH) }
            )

            Spacer(Modifier.weight(1f))

            RailActionButton(
                icon = if (isParkedMode) Icons.Default.LocalParking else Icons.Default.DirectionsCar,
                tint = if (isParkedMode) CarColors.TextPrimary else CarColors.Accent,
                contentDescription = if (isParkedMode) "Parked mode" else "Driving audio-only mode",
                onClick = onToggleParkedMode
            )
            Spacer(Modifier.height(8.dp))
            RailActionButton(
                icon = Icons.Default.Refresh,
                tint = CarColors.TextSecondary,
                contentDescription = "Refresh",
                onClick = onRefresh
            )
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) CarColors.SurfaceElevated else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(1.dp, CarColors.SurfaceBorder) else null,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else CarColors.TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = if (selected) Color.White else CarColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RailActionButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        color = CarColors.SurfaceElevated.copy(alpha = 0.7f),
        shape = CircleShape,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CarBrowseContent(
    state: RoadieUiState,
    columns: Int,
    onSelectCategory: (CategoryChip) -> Unit,
    onOpenSearchKeyboard: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit
) {
    val videos = if (state.activeTab == CarTab.HOME) state.homeVideos else state.searchResults

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.activeTab == CarTab.HOME) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(DefaultCarCategories, key = { it.label }) { chip ->
                    val selected = state.selectedCategory == chip.label
                    Surface(
                        color = if (selected) Color.White else CarColors.Surface,
                        shape = RoundedCornerShape(14.dp),
                        border = if (selected) null else BorderStroke(1.dp, CarColors.SurfaceBorder),
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectCategory(chip) }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chip.label,
                                color = if (selected) Color(0xFF090A0F) else CarColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                color = CarColors.Surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CarColors.SurfaceBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenSearchKeyboard)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = CarColors.TextSecondary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = state.searchQuery.ifEmpty { "Search videos (English / عربي)…" },
                        color = if (state.searchQuery.isEmpty()) CarColors.TextSecondary else Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.loadingFeed -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CarColors.Accent)
                }
            }
            state.errorMessage != null && videos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.errorMessage, color = CarColors.TextSecondary, fontSize = 16.sp)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(videos, key = { it.id }) { video ->
                        CarVideoCard(video = video, onClick = { onOpenVideo(video) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CarVideoCard(video: VideoItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_scale"
    )

    Surface(
        color = CarColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (pressed) Color.White.copy(alpha = 0.5f) else CarColors.SurfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(CarColors.SurfaceElevated)
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
                        color = CarColors.Accent,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (video.durationText.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = video.durationText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = video.title,
                    color = CarColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOf(video.channel, video.viewCountText)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    color = CarColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CarWatchView(
    stream: ResolvedStream,
    player: Player?,
    wideMode: Boolean,
    isParkedMode: Boolean,
    onBack: () -> Unit,
    onSelectRelated: (VideoItem) -> Unit
) {
    val context = LocalContext.current

    val playerSection: @Composable (Modifier) -> Unit = { mod ->
        Column(modifier = mod) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Surface(
                    color = CarColors.Surface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, CarColors.SurfaceBorder),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stream.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stream.channel,
                        color = CarColors.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black)
                    .border(1.dp, CarColors.SurfaceBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isParkedMode) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                            }
                        },
                        update = { it.player = player },
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
                        alpha = 0.28f,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CarColors.Accent, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Audio-only while driving", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    val queueSection: @Composable (Modifier) -> Unit = { mod ->
        Surface(
            color = CarColors.Surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, CarColors.SurfaceBorder),
            modifier = mod
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Up Next", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                items(stream.relatedVideos, key = { it.id }) { related ->
                    Surface(
                        color = CarColors.SurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectRelated(related) }
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(related.thumbnailUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = related.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(104.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CarColors.Background)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(related.title, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(related.channel, color = CarColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    if (wideMode) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            playerSection(Modifier.weight(1.75f).fillMaxHeight())
            queueSection(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            playerSection(Modifier.fillMaxWidth().weight(1.2f))
            queueSection(Modifier.fillMaxWidth().weight(1f))
        }
    }
}
