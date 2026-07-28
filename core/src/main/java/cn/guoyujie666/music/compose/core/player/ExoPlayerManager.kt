package cn.guoyujie666.music.compose.core.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

import cn.guoyujie666.music.compose.core.setting.SettingsManager
import kotlinx.coroutines.runBlocking

@Singleton
class ExoPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    private val cacheDir = File(context.cacheDir, "media_cache").also { it.mkdirs() }
    private val cache by lazy {
        val sizeMB = runCatching { runBlocking { settingsManager.getSettings().cacheSize } }.getOrNull()?.toLongOrNull() ?: 500L
        SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(sizeMB * 1024 * 1024), StandaloneDatabaseProvider(context))
    }

    // Eagerly initialize cache factory (needed by ExoPlayer Builder below)
    private val cacheDataSourceFactory: CacheDataSource.Factory by lazy {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf(
                "Accept" to "audio/*, application/octet-stream, */*",
                "Accept-Encoding" to "identity"
            ))
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(ProgressiveMediaSource.Factory(cacheDataSourceFactory))
        .build()
        .apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
        }

    var onPlayerError: ((String) -> Unit)? = null
    var onDurationReady: ((Long) -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    /** Manual skip to next via notification/system controls */
    var onSkipToNext: (() -> Unit)? = null
    /** Manual skip to previous */
    var onSkipToPrevious: (() -> Unit)? = null

    private var serviceStarted = false
    private var lastDuration = 0L
    /** Guard to suppress onMediaItemTransition during playlist rebuilds */
    private var isUpdatingPlaylist = false

    private var controllerFuture: ListenableFuture<MediaController>? = null

    /**
     * Start the playback service by connecting a MediaController.
     * This triggers Media3's internal notification infrastructure —
     * DefaultMediaNotificationProvider posts the foreground notification,
     * and the system registers the MediaSession for media controls (Xiaomi Miao Play, etc.)
     */
    fun startPlaybackService() {
        if (serviceStarted) return
        serviceStarted = true
        // Start the service explicitly first (required for foreground service on O+)
        val serviceClass = Class.forName("cn.guoyujie666.music.compose.core.player.MusicPlaybackService")
        val intent = Intent(context, serviceClass)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        // Then connect via MediaController — this tells Media3 to post the notification
        val sessionToken = SessionToken(context, ComponentName(context, serviceClass))
        controllerFuture = MediaController.Builder(context, sessionToken)
            .buildAsync()
        controllerFuture?.addListener({
            Log.d("ExoPlayerManager", "MediaController connected")
        }, MoreExecutors.directExecutor())
    }

    fun stopPlaybackService() {
        serviceStarted = false
        try {
            controllerFuture?.let { MediaController.releaseFuture(it) }
            controllerFuture = null
            val intent = Intent(context, Class.forName("cn.guoyujie666.music.compose.core.player.MusicPlaybackService"))
            context.stopService(intent)
        } catch (_: Exception) {}
    }

    /** Cached artwork bytes for the current song (set via setArtworkData) */
    @Volatile
    private var currentArtworkData: ByteArray? = null

    /**
     * Play a URL. Maintains a 3-item playlist [__prev__, __curr__, __next__] so that
     * ExoPlayer natively reports next/prev commands as available. The ForwardingPlayer
     * in MusicPlaybackService intercepts seekToNextMediaItem/seekToPreviousMediaItem
     * before ExoPlayer transitions to the placeholder items — no audio glitch.
     */
    fun playUrl(url: String, title: String = "", artist: String = "", albumArtUrl: String? = null, durationMs: Long = 0L) {
        lastDuration = 0L
        isUpdatingPlaylist = true
        try {
            val metadata = buildMediaMetadata(title, artist, albumArtUrl, durationMs)
            val prevItem = MediaItem.Builder()
                .setUri(url).setMediaId("__prev__").setMediaMetadata(metadata).build()
            val currItem = MediaItem.Builder()
                .setUri(url).setMediaId("__curr__").setMediaMetadata(metadata).build()
            val nextItem = MediaItem.Builder()
                .setUri(url).setMediaId("__next__").setMediaMetadata(metadata).build()
            exoPlayer.setMediaItems(listOf(prevItem, currItem, nextItem), 1, 0)
            exoPlayer.prepare()
            exoPlayer.play()
            startPlaybackService()
        } finally {
            isUpdatingPlaylist = false
        }
    }

    fun setMediaMetadata(title: String, artist: String, albumArtUrl: String?) {
        isUpdatingPlaylist = true
        try {
            val currentItem = exoPlayer.currentMediaItem ?: return
            val metadata = buildMediaMetadata(
                title, artist, albumArtUrl,
                exoPlayer.duration.coerceAtLeast(0L)
            )
            exoPlayer.replaceMediaItem(
                exoPlayer.currentMediaItemIndex,
                currentItem.buildUpon().setMediaMetadata(metadata).build()
            )
        } finally {
            isUpdatingPlaylist = false
        }
    }

    /**
     * Set artwork bitmap data (JPEG/PNG bytes) for the current song.
     * This is picked up by Media3's compat layer and mapped to
     * METADATA_KEY_ALBUM_ART — required for Xiaomi Miao Play / Super Island.
     * Call this after the cover image is downloaded.
     */
    fun setArtworkData(artworkBytes: ByteArray?) {
        currentArtworkData = artworkBytes
        // Re-apply metadata with the new artwork data
        isUpdatingPlaylist = true
        try {
            val currentItem = exoPlayer.currentMediaItem ?: return
            val oldMeta = currentItem.mediaMetadata
            val newMeta = oldMeta.buildUpon()
                .setArtworkData(artworkBytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()
            exoPlayer.replaceMediaItem(
                exoPlayer.currentMediaItemIndex,
                currentItem.buildUpon().setMediaMetadata(newMeta).build()
            )
        } finally {
            isUpdatingPlaylist = false
        }
    }

    /**
     * Build MediaMetadata with all fields that Media3's compat layer needs:
     * - title/artist → METADATA_KEY_TITLE / METADATA_KEY_ARTIST
     * - artworkUri → METADATA_KEY_ART_URI (for apps that prefer URI)
     * - artworkData → METADATA_KEY_ALBUM_ART (Bitmap, required by Xiaomi)
     * - durationMs → METADATA_KEY_DURATION (set via extras since setDurationMs not available in 1.10)
     */
    private fun buildMediaMetadata(
        title: String,
        artist: String,
        albumArtUrl: String?,
        durationMs: Long = 0L
    ): androidx.media3.common.MediaMetadata {
        val builder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title.ifEmpty { "LX Music" })
            .setArtist(artist.ifEmpty { null })
            .setIsBrowsable(false)
            .setIsPlayable(true)
        if (albumArtUrl != null) {
            builder.setArtworkUri(android.net.Uri.parse(albumArtUrl))
        }
        // Attach cached artwork bytes if available (Xiaomi reads ALBUM_ART bitmap)
        val artData = currentArtworkData
        if (artData != null) {
            builder.setArtworkData(artData, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        // Duration in extras — Media3 compat maps this for PlaybackState
        if (durationMs > 0) {
            builder.setExtras(android.os.Bundle().apply { putLong("android.media.metadata.DURATION", durationMs) })
        }
        return builder.build()
    }

    fun play() { exoPlayer.play() }
    fun pause() { exoPlayer.pause() }
    fun stop() { exoPlayer.stop(); stopPlaybackService() }
    fun seekTo(ms: Long) { exoPlayer.seekTo(ms) }
    fun setVolume(vol: Float) { exoPlayer.volume = vol }
    fun setPlaybackSpeed(rate: Float) { exoPlayer.setPlaybackSpeed(rate) }
    val isPlaying: Boolean get() = exoPlayer.isPlaying
    val duration: Long get() {
        val d = exoPlayer.duration
        if (d > 0) return d
        // Some streams don't report duration to ExoPlayer's MediaSource directly.
        // contentDuration may be available from the container metadata.
        val cd = exoPlayer.contentDuration
        return if (cd != androidx.media3.common.C.TIME_UNSET) cd else 0L
    }
    val currentPosition: Long get() = exoPlayer.currentPosition

    private fun checkAndNotifyDuration() {
        val d = exoPlayer.duration.coerceAtLeast(exoPlayer.contentDuration)
        if (d > 0 && d != lastDuration) {
            lastDuration = d
            onDurationReady?.invoke(d)
        }
    }

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) checkAndNotifyDuration()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) checkAndNotifyDuration()
            }
            /**
             * Detect skip-to-next/prev via the 3-item playlist placeholders.
             * ExoPlayer transitions to __next__ (index 2) or __prev__ (index 0) when
             * the user presses the system next/prev button. We immediately seek back
             * to __curr__ (index 1) to avoid playing placeholder audio, then route to
             * the appropriate skip handler which calls playUrl() to load the real song.
             */
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isUpdatingPlaylist) return
                when (mediaItem?.mediaId) {
                    "__curr__" -> return  // Playlist rebuild or seek-back; ignore
                    "__next__" -> {
                        exoPlayer.seekToDefaultPosition(1) // Seek back to current, suppress placeholder
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            onCompletion?.invoke()         // Natural end → playNext(isAuto=true)
                        } else {
                            onSkipToNext?.invoke()        // Manual skip → playNext(isAuto=false)
                        }
                    }
                    "__prev__" -> {
                        exoPlayer.seekToDefaultPosition(1) // Seek back to current
                        onSkipToPrevious?.invoke()
                    }
                }
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                    checkAndNotifyDuration()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                val detail = buildString {
                    append(error.localizedMessage ?: "Playback error")
                    append(" (code=${error.errorCode}")
                    if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) append(" network-fail")
                    else if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) append(" timeout")
                    else if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) append(" bad-http")
                    else if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) append(" unsupported-format")
                    else if (error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE) append(" bad-content-type")
                    append(")")
                }
                Log.e("ExoPlayer", "Playback error: $detail", error)
                onPlayerError?.invoke(detail)
            }
        })
    }

    fun release() { stopPlaybackService(); exoPlayer.release() }
}
