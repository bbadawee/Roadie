package io.roadie.carui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
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
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(state.currentStream?.videoId) {
        val stream = state.currentStream ?: return@LaunchedEffect
        val item = MediaItem.Builder()
            .setUri(stream.streamUrl)
            .setMediaId(stream.videoId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(stream.title)
                    .setArtist(stream.channel)
                    .build()
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    RoadieTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(CarColors.Background)
        ) {
            val metrics = CarViewportMetrics(maxWidth.value, maxHeight.value)

            Row(modifier = Modifier.fillMaxSize()) {
                CarSidebar(
                    activeTab = state.activeTab,
                    onSelectTab = viewModel::selectTab,
                    onRefresh = viewModel::loadHome
                )

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val stream = state.currentStream
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
                            onOpenSearchKeyboard = { viewModel.setKeyboardVisible(true) },
                            onOpenVideo = viewModel::openVideo
                        )
                    }

                    if (state.showKeyboard) {
                        Box(
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
                    }

                    if (state.loadingStream) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.65f)),
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

@Composable
private fun CarSidebar(
    activeTab: CarTab,
    onSelectTab: (CarTab) -> Unit,
    onRefresh: () -> Unit
) {
    NavigationRail(
        containerColor = CarColors.Surface,
        contentColor = Color.White,
        modifier = Modifier.width(84.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        NavigationRailItem(
            selected = activeTab == CarTab.HOME,
            onClick = { onSelectTab(CarTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(28.dp)) },
            label = { Text("Home", fontSize = 13.sp) }
        )
        Spacer(Modifier.height(12.dp))
        NavigationRailItem(
            selected = activeTab == CarTab.SEARCH,
            onClick = { onSelectTab(CarTab.SEARCH) },
            icon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(28.dp)) },
            label = { Text("Search", fontSize = 13.sp) }
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CarBrowseContent(
    state: RoadieUiState,
    columns: Int,
    onOpenSearchKeyboard: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit
) {
    val videos = if (state.activeTab == CarTab.HOME) state.homeVideos else state.searchResults

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (state.activeTab == CarTab.SEARCH) {
            Surface(
                color = CarColors.SurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clickable(onClick = onOpenSearchKeyboard)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = CarColors.TextSecondary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = state.searchQuery.ifEmpty { "Tap to search (EN / عربي)…" },
                        color = if (state.searchQuery.isEmpty()) CarColors.TextSecondary else Color.White,
                        fontSize = 18.sp
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        when {
            state.loadingFeed -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CarColors.Accent)
                }
            }
            state.errorMessage != null && videos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.errorMessage, color = CarColors.TextSecondary, fontSize = 18.sp)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
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
    Surface(
        color = CarColors.Surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (video.durationText.isNotEmpty()) {
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 16.sp,
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
                    fontSize = 13.sp,
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
    player: ExoPlayer,
    wideMode: Boolean,
    isParkedMode: Boolean,
    onBack: () -> Unit,
    onSelectRelated: (VideoItem) -> Unit
) {
    val playerSection: @Composable (Modifier) -> Unit = { mod ->
        Column(modifier = mod.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stream.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stream.channel,
                        color = CarColors.TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CarColors.Accent, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Audio-only while driving", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    val queueSection: @Composable (Modifier) -> Unit = { mod ->
        LazyColumn(
            modifier = mod.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Up Next", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            items(stream.relatedVideos, key = { it.id }) { related ->
                Surface(
                    color = CarColors.Surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectRelated(related) }
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = related.thumbnailUrl,
                            contentDescription = related.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(112.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(related.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(related.channel, color = CarColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CarColors.TextSecondary)
                    }
                }
            }
        }
    }

    if (wideMode) {
        Row(modifier = Modifier.fillMaxSize()) {
            playerSection(Modifier.weight(1.7f).fillMaxHeight())
            queueSection(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            playerSection(Modifier.fillMaxWidth().weight(1.2f))
            queueSection(Modifier.fillMaxWidth().weight(1f))
        }
    }
}
