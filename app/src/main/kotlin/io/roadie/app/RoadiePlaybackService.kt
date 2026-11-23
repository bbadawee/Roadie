package io.roadie.app

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.roadie.data.RoadieDatabase
import io.roadie.innertube.InnerTubeClient
import io.roadie.innertube.StreamResult
import io.roadie.innertube.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Media3 MediaLibraryService enabling:
 * - Background audio & Bluetooth steering-wheel controls
 * - Android Auto car-screen folder browsing (Home, Subscriptions, History), search, and audio playback
 */
class RoadiePlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val innerTube = InnerTubeClient()

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Roadie")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId == "root") {
                val folders = listOf(
                    folderItem("home", "Home"),
                    folderItem("subscriptions", "Subscriptions"),
                    folderItem("history", "History")
                )
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(folders), params))
            }

            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val dao = RoadieDatabase.get(applicationContext).dao()
                val items = when (parentId) {
                    "history" -> runCatching {
                        dao.observeHistory().first().map { it.toVideoItem().toMediaItem() }
                    }.getOrDefault(emptyList())
                    "subscriptions" -> runCatching {
                        val subs = dao.observeSubscriptions().first().map { it.channelName }.toSet()
                        val hist = dao.observeHistory().first().map { it.toVideoItem() }
                        hist.filter { subs.contains(it.channel) }.map { it.toMediaItem() }
                    }.getOrDefault(emptyList())
                    else -> runCatching { innerTube.fetchHomeFeed() }
                        .getOrDefault(emptyList())
                        .map { it.toMediaItem() }
                }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val future = SettableFuture.create<LibraryResult<Void>>()
            serviceScope.launch {
                val results = runCatching { innerTube.search(query) }.getOrDefault(emptyList())
                session.notifySearchResultChanged(browser, query, results.size, params)
                future.set(LibraryResult.ofVoid())
            }
            return future
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val items = runCatching { innerTube.search(query) }
                    .getOrDefault(emptyList())
                    .map { it.toMediaItem() }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch {
                val resolved = mediaItems.map { item ->
                    if (item.localConfiguration != null) {
                        item
                    } else {
                        when (val res = innerTube.resolveStream(item.mediaId)) {
                            is StreamResult.Success -> {
                                val stream = res.stream
                                val bestAudioOrStream = stream.audioUrl ?: stream.streamUrl
                                item.buildUpon()
                                    .setUri(bestAudioOrStream)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(stream.title)
                                            .setArtist(stream.channel)
                                            .setArtworkUri(Uri.parse(stream.thumbnailUrl))
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .build()
                                    )
                                    .build()
                            }
                            is StreamResult.Error -> item
                        }
                    }
                }.toMutableList()
                future.set(resolved)
            }
            return future
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)")
            .setAllowCrossProtocolRedirects(true)

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, libraryCallback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun folderItem(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    private fun VideoItem.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(channel)
                    .setArtworkUri(Uri.parse(thumbnailUrl))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                    .build()
            )
            .build()
}
