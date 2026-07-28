package cn.guoyujie666.music.compose.core.player

import android.content.Context
import android.content.Intent
import cn.guoyujie666.music.compose.core.event.EventBus
import dagger.hilt.android.qualifiers.ApplicationContext
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.model.MusicToggleModes
import cn.guoyujie666.music.compose.core.model.PlayInfo
import cn.guoyujie666.music.compose.core.model.PlayMusicInfo
import cn.guoyujie666.music.compose.core.model.PlaybackProgress
import cn.guoyujie666.music.compose.core.model.PlayerStatus
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import cn.guoyujie666.music.compose.core.music.AggregateSearch
import cn.guoyujie666.music.compose.core.music.SourceRegistry
import cn.guoyujie666.music.compose.core.music.UserApiSource
import cn.guoyujie666.music.compose.core.setting.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.random.Random

/**
 * Core player controller — orchestrates music playback.
 *
 * Ported from src/core/player/player.ts and src/plugins/player/.
 * Replaces react-native-track-player with Media3 ExoPlayer.
 *
 * Key design decisions:
 * - State managed via MutableStateFlow (replaces mutable singleton + events)
 * - 2-track queue pattern adapted for Media3's playlist model
 * - Cross-source matching and dislike filtering preserved
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: EventBus,
    private val settingsManager: SettingsManager,
    private val exoPlayerManager: ExoPlayerManager,
    private val aggregateSearch: AggregateSearch,
    private val sourceRegistry: SourceRegistry,
    private val lrcParser: cn.guoyujie666.music.compose.core.lyric.LrcParser
) {
    private fun showError(msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    }

    private fun updateNotification(title: String, artist: String, albumArtUrl: String?) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val baseBuilder = androidx.core.app.NotificationCompat.Builder(context, "lx_music_playback")
                .setContentTitle(title.ifEmpty { "LX Music" })
                .setContentText(artist.ifEmpty { "Playing..." })
                .setSmallIcon(context.applicationInfo.icon)
                .setOngoing(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            // Post immediately with title/artist
            nm.notify(1, baseBuilder.build())
            // Download and apply cover in background
            if (albumArtUrl != null) {
                Thread {
                    try {
                        val url = java.net.URL(albumArtUrl)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 10000; conn.readTimeout = 10000
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
                        conn.instanceFollowRedirects = true
                        val bitmap = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                        conn.disconnect()
                        if (bitmap != null) {
                            val builder = androidx.core.app.NotificationCompat.Builder(context, "lx_music_playback")
                                .setContentTitle(title.ifEmpty { "LX Music" })
                                .setContentText(artist.ifEmpty { "Playing..." })
                                .setSmallIcon(context.applicationInfo.icon)
                                .setLargeIcon(bitmap)
                                .setOngoing(true)
                                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                            nm.notify(1, builder.build())
                        }
                    } catch (_: Exception) {}
                }.start()
            }
        } catch (_: Exception) {}
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // === Player Status ===
    private val _status = MutableStateFlow(PlayerStatus())
    val status: StateFlow<PlayerStatus> = _status.asStateFlow()

    // === Playback State ===
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMusicInfo = MutableStateFlow<PlayMusicInfo?>(null)
    val currentMusicInfo: StateFlow<PlayMusicInfo?> = _currentMusicInfo.asStateFlow()

    private val _playInfo = MutableStateFlow(PlayInfo())
    val playInfo: StateFlow<PlayInfo> = _playInfo.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _playbackRate = MutableStateFlow(1f)
    val playbackRate: StateFlow<Float> = _playbackRate.asStateFlow()

    // === Lyric & Cover ===
    private val _lyricLines = MutableStateFlow<List<cn.guoyujie666.music.compose.core.lyric.LyricLine>>(emptyList())
    val lyricLines: StateFlow<List<cn.guoyujie666.music.compose.core.lyric.LyricLine>> = _lyricLines.asStateFlow()

    private val _albumArt = MutableStateFlow<String?>(null)
    val albumArt: StateFlow<String?> = _albumArt.asStateFlow()

    private val _comments = MutableStateFlow<Pair<List<cn.guoyujie666.music.compose.core.music.CommentItem>, List<cn.guoyujie666.music.compose.core.music.CommentItem>>?>(null)
    val comments: StateFlow<Pair<List<cn.guoyujie666.music.compose.core.music.CommentItem>, List<cn.guoyujie666.music.compose.core.music.CommentItem>>?> = _comments.asStateFlow()

    fun fetchComments() {
        val info = _currentMusicInfo.value ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val s = settingsManager.getSettings()
                val apiSourceId = s.apiSource
                val source = if (apiSourceId.startsWith("user_api_")) sourceRegistry.getSource(apiSourceId)
                    else sourceRegistry.getSource(info.musicInfo.source) ?: aggregateSearch.getSource(info.musicInfo.source)
                val emptyComments: List<cn.guoyujie666.music.compose.core.music.CommentItem> = emptyList()
                if (source?.supportsComments == true) {
                    val hot: List<cn.guoyujie666.music.compose.core.music.CommentItem> = source.getHotComments(info.musicInfo, 1, 20).getOrElse { cn.guoyujie666.music.compose.core.music.CommentResult() }.list
                    val nw: List<cn.guoyujie666.music.compose.core.music.CommentItem> = source.getNewComments(info.musicInfo, 1, 20).getOrElse { cn.guoyujie666.music.compose.core.music.CommentResult() }.list
                    _comments.value = Pair<List<cn.guoyujie666.music.compose.core.music.CommentItem>, List<cn.guoyujie666.music.compose.core.music.CommentItem>>(hot, nw)
                } else {
                    _comments.value = Pair<List<cn.guoyujie666.music.compose.core.music.CommentItem>, List<cn.guoyujie666.music.compose.core.music.CommentItem>>(emptyComments, emptyComments)
                }
            } catch (_: Exception) { _comments.value = Pair(emptyList(), emptyList()) }
        }
    }

    // === Lists ===
    private val playedList = mutableListOf<PlayMusicInfo>()          // History
    private val tempPlayList = mutableListOf<PlayMusicInfo>()        // "Play later" queue
    private val defaultList = mutableListOf<MusicInfo>()             // 试听列表 — accumulates tapped songs
    private var listId: String? = null                               // Current playlist
    private var musicList: List<MusicInfo> = emptyList()             // Current list contents
    private var randomNextMusicInfo: MusicInfo? = null               // Pre-cached random next

    // === Settings-derived state ===
    private val _togglePlayMethod = MutableStateFlow(MusicToggleModes.LIST_LOOP)
    val togglePlayMethod: StateFlow<String> = _togglePlayMethod.asStateFlow()

    fun cyclePlayMode() {
        _togglePlayMethod.value = MusicToggleModes.next(_togglePlayMethod.value)
        scope.launch {
            settingsManager.updateSettings(settingsManager.getSettings().copy(togglePlayMethod = _togglePlayMethod.value))
        }
    }
    private var isSavePlayTime: Boolean = false
    private var isAutoCleanPlayedList: Boolean = false
    var bluetoothLyricEnabled: Boolean = false
    private var lastBluetoothTitle: String = ""
    var desktopLyricEnabled: Boolean = false
    private var lastDesktopLyric: String = ""
    private var lastDesktopTranslation: String = ""
    private var desktopLyricServiceStarted: Boolean = false

    fun sendDesktopLyricStyle() {
        if (!desktopLyricEnabled || !desktopLyricServiceStarted) return
        val settings = runCatching { kotlinx.coroutines.runBlocking { settingsManager.getSettings() } }.getOrNull() ?: return
        val intent = Intent("cn.guoyujie666.music.compose.DESKTOP_LYRIC_STYLE")
        intent.setPackage(context.packageName)
        intent.putExtra("font_size", settings.desktopLyricFontSize.toFloat())
        intent.putExtra("opacity", settings.desktopLyricOpacity)
        intent.putExtra("unplayed_color", settings.desktopLyricUnplayColor)
        intent.putExtra("played_color", settings.desktopLyricPlayedColor)
        intent.putExtra("shadow_color", settings.desktopLyricShadowColor)
        intent.putExtra("is_locked", settings.desktopLyricIsLock)
        intent.putExtra("is_single_line", settings.desktopLyricIsSingleLine)
        intent.putExtra("max_lines", settings.desktopLyricMaxLineNum)
        intent.putExtra("width", settings.desktopLyricWidth)
        intent.putExtra("text_align_x", settings.desktopLyricTextPositionX)
        intent.putExtra("text_align_y", settings.desktopLyricTextPositionY)
        context.startService(intent)
    }

    fun ensureDesktopLyricService() {
        android.util.Log.d("DesktopLyric", "ensureDesktopLyricService enabled=$desktopLyricEnabled started=$desktopLyricServiceStarted")
        if (!desktopLyricEnabled || desktopLyricServiceStarted) return
        // Check overlay permission first
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(context)) {
                showError("请先授予悬浮窗权限")
                desktopLyricEnabled = false
                return
            }
        }
        val intent = Intent("cn.guoyujie666.music.compose.DESKTOP_LYRIC_SHOW")
        intent.setPackage(context.packageName)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        desktopLyricServiceStarted = true
        sendDesktopLyricStyle()
    }

    private fun stopDesktopLyricService() {
        if (!desktopLyricServiceStarted) return
        val intent = Intent("cn.guoyujie666.music.compose.DESKTOP_LYRIC_HIDE")
        intent.setPackage(context.packageName)
        context.startService(intent)
        desktopLyricServiceStarted = false
    }

    private fun updateDesktopLyric(lyric: String, translation: String?, isPlaying: Boolean) {
        if (!desktopLyricEnabled || !desktopLyricServiceStarted) return
        if (lyric == lastDesktopLyric && translation == lastDesktopTranslation) return
        lastDesktopLyric = lyric
        lastDesktopTranslation = translation ?: ""
        val intent = Intent("cn.guoyujie666.music.compose.DESKTOP_LYRIC_UPDATE")
        intent.setPackage(context.packageName)
        intent.putExtra("lyric", lyric)
        intent.putExtra("translation", translation)
        intent.putExtra("is_playing", isPlaying)
        context.startService(intent)
    }

    // === Progress tracking ===
    private var progressJob: Job? = null
    // Auto-skip timer for unplayable songs (matches RN's delayNextTimeout = 5000ms)
    private var autoSkipJob: Job? = null

    // Play state persistence — mirrors RN's SavedPlayInfo + initPlayInfo flow
    private val playStatePrefs = context.getSharedPreferences("lx_play_state", android.content.Context.MODE_PRIVATE)

    init {
        // Load settings synchronously so play mode is ready before any playback
        val settings = runCatching { kotlinx.coroutines.runBlocking { settingsManager.getSettings() } }.getOrDefault(cn.guoyujie666.music.compose.core.model.AppSetting())
        _togglePlayMethod.value = settings.togglePlayMethod
        isSavePlayTime = settings.isSavePlayTime
        isAutoCleanPlayedList = settings.isAutoCleanPlayedList
        bluetoothLyricEnabled = settings.isShowBluetoothLyric
        desktopLyricEnabled = settings.desktopLyricEnable
        _volume.value = settings.volume
        _playbackRate.value = settings.playbackRate

        scope.launch {
            // Always restore saved play state on startup (sets the song in UI)
            loadDefaultList()
            restorePlayState()

            if (_currentMusicInfo.value != null) {
                // Restore UI state (lyrics + pic) without auto-playing
                if (settings.startupAutoPlay) {
                    play()
                } else {
                    // Fetch lyrics and album art to restore full UI state
                    launch(Dispatchers.IO) {
                        val info = _currentMusicInfo.value?.musicInfo ?: return@launch
                        val src = sourceRegistry.getSource(info.source) ?: aggregateSearch.getSource(info.source)
                        try {
                            val lyric = src?.getLyric(info)?.getOrNull()
                            if (lyric != null && lyric.lyric.isNotBlank()) {
                                _lyricLines.value = lrcParser.fromLyricInfo(lyric).lines
                            }
                        } catch (_: Exception) {}
                        try {
                            val pic = src?.getPic(info)?.getOrNull()
                            if (!pic.isNullOrEmpty()) _albumArt.value = pic
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        exoPlayerManager.onCompletion = {
            scope.launch { playNext(isAuto = true) }
        }
        exoPlayerManager.onSkipToNext = {
            scope.launch { playNext(isAuto = false) }
        }
        exoPlayerManager.onSkipToPrevious = {
            scope.launch { playPrev(isAuto = false) }
        }
        exoPlayerManager.onPlayerError = { error ->
            _isPlaying.value = false
            showError("播放出错: $error")
            scheduleAutoSkip()
        }
        exoPlayerManager.onDurationReady = { dur ->
            _progress.update { it.copy(maxPlayTime = dur, maxPlayTimeStr = formatTime(dur)) }
        }
    }

    /**
     * Save play state. Called on every song switch (ALWAYS saves listId/index).
     * Playback position (time) is only saved when isSavePlayTime is enabled.
     */
    private var lastSaveTime = 0L
    private var lastProgressSave = 0L

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    private fun savePlayState() {
        try {
            val info = _currentMusicInfo.value ?: return
            val now = System.currentTimeMillis()
            if (now - lastSaveTime < 1500) return  // throttle
            lastSaveTime = now
            val playInfo = _playInfo.value
            val prog = _progress.value
            val savedListId = playInfo.playerListId ?: info.listId
            val editor = playStatePrefs.edit()
                .putString("listId", savedListId)
                .putInt("index", playInfo.playerPlayIndex)
                .putLong("time", if (isSavePlayTime) prog.nowPlayTime else 0L)
                .putLong("maxTime", prog.maxPlayTime)
            // Save current music info
            try {
                val musicJson = json.encodeToString(cn.guoyujie666.music.compose.core.model.MusicInfo.serializer(), info.musicInfo)
                editor.putString("lastMusic", musicJson)
            } catch (_: Exception) {}
            // Save album art URL
            val pic = _albumArt.value
            if (pic != null) editor.putString("lastPic", pic)
            // Save current music list so playNext/playPrev works after app restart
            if (savedListId != "default" && musicList.isNotEmpty()) {
                try {
                    val listJson = json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(cn.guoyujie666.music.compose.core.model.MusicInfo.serializer()),
                        musicList
                    )
                    editor.putString("musicList", listJson)
                } catch (_: Exception) {}
            }
            editor.apply()
        } catch (_: Exception) {}
    }

    /**
     * Restore play state from storage. Always called on init.
     * Does NOT auto-play — that's controlled by startupAutoPlay.
     */
    private fun restorePlayState() {
        try {
            val savedListId = playStatePrefs.getString("listId", null) ?: "default"

            // Restore last played music info (use saved listId, not hardcoded "default")
            val musicJson = playStatePrefs.getString("lastMusic", null)
            if (musicJson != null) {
                try {
                    val info = json.decodeFromString(cn.guoyujie666.music.compose.core.model.MusicInfo.serializer(), musicJson)
                    _currentMusicInfo.value = PlayMusicInfo(musicInfo = info, listId = savedListId)
                } catch (_: Exception) {}
            }

            // Restore album art
            val savedPic = playStatePrefs.getString("lastPic", null)
            if (savedPic != null) _albumArt.value = savedPic
            val savedIndex = playStatePrefs.getInt("index", -1).takeIf { it >= 0 } ?: 0
            val savedTime = playStatePrefs.getLong("time", 0L)
            val savedMaxTime = playStatePrefs.getLong("maxTime", 0L)

            _progress.value = PlaybackProgress(
                nowPlayTime = if (isSavePlayTime) savedTime else 0L,
                maxPlayTime = savedMaxTime,
                progress = if (savedMaxTime > 0 && isSavePlayTime) savedTime.toFloat() / savedMaxTime else 0f,
                nowPlayTimeStr = formatTime(if (isSavePlayTime) savedTime else 0L),
                maxPlayTimeStr = formatTime(savedMaxTime)
            )

            this.listId = savedListId
            _playInfo.value = _playInfo.value.copy(playerListId = savedListId, playerPlayIndex = savedIndex)
            if (isSavePlayTime && savedTime > 0) {
                pendingSeekMs = savedTime
                pendingSeekSongId = musicJson?.let {
                    try { json.decodeFromString(cn.guoyujie666.music.compose.core.model.MusicInfo.serializer(), it).id }
                    catch (_: Exception) { null }
                }
            }

            // Restore music list so playNext/playPrev works after app restart
            if (savedListId == "default") {
                this.musicList = defaultList.toList()
            } else {
                val savedMusicListJson = playStatePrefs.getString("musicList", null)
                if (savedMusicListJson != null) {
                    try {
                        val list = json.decodeFromString(
                            kotlinx.serialization.builtins.ListSerializer(cn.guoyujie666.music.compose.core.model.MusicInfo.serializer()),
                            savedMusicListJson
                        )
                        this.musicList = list
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    /** Saved playback position for seek after URL loads (only applied to matching song) */
    var pendingSeekMs: Long = 0L
    private var pendingSeekSongId: String? = null

    // === Initialization ===

    suspend fun initialize() {
        _status.update { it.copy(isIniting = true) }
        try {
            // Future: setup Media3 ExoPlayer instance here
            _status.update { it.copy(
                isInitialized = true,
                isRegisteredService = true,
                isIniting = false
            ) }
        } catch (e: Exception) {
            _status.update { it.copy(isIniting = false) }
            throw e
        }
    }

    // === Core Playback Control ===

    /** Get current default list for display/persistence */
    private val _defaultListVersion = MutableStateFlow(0L)
    val defaultListVersion: StateFlow<Long> = _defaultListVersion.asStateFlow()

    fun getDefaultList(): List<MusicInfo> = defaultList.toList()

    /** Remove songs from the default list (试听列表) by IDs */
    fun removeFromDefaultList(ids: Set<String>) {
        defaultList.removeAll { it.id in ids }
        saveDefaultList()
        _defaultListVersion.value++
    }

    /** Add songs to default list (试听列表) without playing — used for import */
    fun addToDefaultList(songs: List<MusicInfo>) {
        val existingIds = defaultList.map { it.id }.toSet()
        val toAdd = songs.filter { it.id !in existingIds }
        if (toAdd.isNotEmpty()) {
            defaultList.addAll(0, toAdd)
            saveDefaultList()
            _defaultListVersion.value++
        }
    }

    /** Replace the default list with a sorted/de-duplicated version */
    fun replaceDefaultList(songs: List<MusicInfo>) {
        defaultList.clear()
        defaultList.addAll(songs)
        saveDefaultList()
        _defaultListVersion.value++
    }

    /**
     * Add song to default list (试听列表) at position 0 and play it.
     * Used from search results — each tap accumulates in the default list.
     */
    fun playFromDefault(musicInfo: MusicInfo) {
        defaultList.removeAll { it.id == musicInfo.id }
        defaultList.add(0, musicInfo)
        saveDefaultList()
        _defaultListVersion.value++
        playList("default", defaultList.toList(), 0)
    }

    /** Play a song within a specific list context (next/prev navigates that list) */
    fun playInList(listId: String, list: List<MusicInfo>, index: Int) {
        this.listId = listId
        this.musicList = list.toList()
        playList(listId, this.musicList, index)
    }

    private fun saveDefaultList() {
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val jsonStr = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(cn.guoyujie666.music.compose.core.model.MusicInfo.serializer()),
                defaultList.toList()
            )
            playStatePrefs.edit().putString("default_list", jsonStr).apply()
        } catch (_: Exception) {}
    }

    private fun loadDefaultList() {
        try {
            val jsonStr = playStatePrefs.getString("default_list", null) ?: return
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val list = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(cn.guoyujie666.music.compose.core.model.MusicInfo.serializer()),
                jsonStr
            )
            defaultList.clear()
            defaultList.addAll(list)
        } catch (_: Exception) {}
    }

    /**
     * Play a list starting from a specific index.
     */
    fun playList(listId: String, list: List<MusicInfo>, index: Int = 0) {
        val prevListId = this.listId
        this.listId = listId
        this.musicList = list

        if (index < 0 || index >= list.size) return

        // Always save play state on song switch (RN saves listId/index unconditionally)
        savePlayState()

        val musicInfo = list[index]
        setPlayMusicInfo(listId, musicInfo)

        if (isAutoCleanPlayedList || prevListId != listId) {
            clearPlayedList()
        }
        clearTempPlayList()
        resetRandomNextMusicInfo()

        handlePlay()
    }

    /**
     * Play a specific song from the current list by its ID.
     */
    fun playListById(listId: String, musicId: String) {
        val list = musicList
        val index = list.indexOfFirst { it.id == musicId }
        if (index >= 0) {
            playList(listId, list, index)
        }
    }

    /**
     * Play/pause toggle.
     */
    fun togglePlay() {
        if (_isPlaying.value) pause() else play()
    }

    fun play() {
        val info = _currentMusicInfo.value ?: return
        // If player is "empty" (no URL loaded yet), fetch the URL first
        // This handles the startup restore flow: state is set, play() triggers URL fetch
        if (!exoPlayerManager.exoPlayer.isPlaying && exoPlayerManager.exoPlayer.duration <= 0) {
            scope.launch { handlePlay() }
            return
        }
        exoPlayerManager.play()
        _isPlaying.value = true
        startProgressTracking()
    }

    fun pause() {
        exoPlayerManager.pause()
        _isPlaying.value = false
        stopProgressTracking()
    }

    fun stop() {
        cancelAutoSkip()
        stopDesktopLyricService()
        pause()
        clearPlayInfo()
        // Persist cleared state so it doesn't restore on restart
        clearSavedPlayState()
    }

    private fun clearSavedPlayState() {
        try {
            playStatePrefs.edit()
                .remove("lastMusic")
                .remove("lastPic")
                .remove("musicList")
                .remove("listId")
                .remove("index")
                .remove("time")
                .remove("maxTime")
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * Seek to a specific position in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        exoPlayerManager.seekTo(positionMs)
        // Update pending seek to the user-chosen position instead of saved position
        pendingSeekMs = positionMs
        pendingSeekSongId = null
        val maxTime = _progress.value.maxPlayTime
        _progress.update { it.copy(
            nowPlayTime = positionMs,
            progress = if (maxTime > 0) positionMs.toFloat() / maxTime else 0f,
            nowPlayTimeStr = formatTime(positionMs)
        )}
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        exoPlayerManager.setVolume(clamped)
        scope.launch {
            settingsManager.updateSettings(settingsManager.getSettings().copy(volume = clamped))
        }
    }

    fun setPlaybackRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2f)
        _playbackRate.value = clamped
        exoPlayerManager.setPlaybackSpeed(clamped)
        scope.launch {
            settingsManager.updateSettings(settingsManager.getSettings().copy(playbackRate = clamped))
        }
    }

    // === Play Next / Previous ===

    /**
     * Play the next song.
     * Implementation mirrors playNext() from src/core/player/player.ts.
     */
    fun playNext(isAuto: Boolean = true) {
        // 1. Check temp play list first
        if (tempPlayList.isNotEmpty()) {
            val next = tempPlayList.removeFirst()
            setPlayMusicInfo(next.listId, next.musicInfo)
            handlePlay()
            return
        }

        val current = _currentMusicInfo.value ?: run { stop(); return }
        val list = musicList

        if (list.isEmpty()) { stop(); return }

        // 2. Check played list going forward
        val nextFromPlayed = getNextFromPlayedList(current, isNext = true)
        if (nextFromPlayed != null) {
            setPlayMusicInfo(nextFromPlayed.listId, nextFromPlayed.musicInfo)
            handlePlay()
            return
        }

        // 4. Filter and determine next
        val (filteredList, _, playerIndex) = filterList(list, isNext = true)
        // RN: filterList consumed played entries; emulate by clearing played for this list
        playedList.removeAll { it.listId == (listId ?: "default") && !it.isTempPlay }
        if (filteredList.isEmpty()) { stop(); return }

        // 5. Determine next index based on toggle mode
        val effectiveMode = if (isAuto) {
            _togglePlayMethod.value
        } else {
            when (_togglePlayMethod.value) {
                MusicToggleModes.LIST,
                MusicToggleModes.SINGLE_LOOP,
                MusicToggleModes.NONE -> MusicToggleModes.LIST_LOOP
                else -> _togglePlayMethod.value
            }
        }
        val nextIndex = when (effectiveMode) {
            MusicToggleModes.LIST_LOOP -> {
                val next = playerIndex + 1
                if (next >= filteredList.size) 0 else next
            }
            MusicToggleModes.RANDOM -> {
                Random.nextInt(filteredList.size)
            }
            MusicToggleModes.LIST, MusicToggleModes.NONE -> {
                val next = playerIndex + 1
                if (next >= filteredList.size) -1 else next
            }
            MusicToggleModes.SINGLE_LOOP -> playerIndex
            else -> playerIndex + 1
        }

        if (nextIndex < 0) { stop(); return }

        val musicInfo = filteredList[nextIndex]
        setPlayMusicInfo(listId ?: "", musicInfo)
        handlePlay()
    }

    /**
     * Play the previous song.
     * Ported from playPrev() in src/core/player/player.ts.
     */
    fun playPrev(isAuto: Boolean = false) {
        val current = _currentMusicInfo.value ?: run { stop(); return }
        val list = musicList
        if (list.isEmpty()) { stop(); return }

        // 1. Check played list going backward
        val prevFromPlayed = getNextFromPlayedList(current, isNext = false)
        if (prevFromPlayed != null) {
            setPlayMusicInfo(prevFromPlayed.listId, prevFromPlayed.musicInfo)
            handlePlay()
            return
        }

        // 2. Filter and determine previous
        val (filteredList, _, playerIndex) = filterList(list, isNext = false)
        playedList.removeAll { it.listId == (listId ?: "default") && !it.isTempPlay }
        if (filteredList.isEmpty()) { stop(); return }

        // 3. Apply mode logic
        val isTempPlay = current.isTempPlay
        val prevIndex = if (!isTempPlay) {
            var effectiveMode = _togglePlayMethod.value
            if (!isAuto) {
                when (effectiveMode) {
                    MusicToggleModes.LIST,
                    MusicToggleModes.SINGLE_LOOP,
                    MusicToggleModes.NONE -> effectiveMode = MusicToggleModes.LIST_LOOP
                }
            }
            when (effectiveMode) {
                MusicToggleModes.RANDOM -> {
                    Random.nextInt(filteredList.size)
                }
                MusicToggleModes.LIST_LOOP,
                MusicToggleModes.LIST -> {
                    if (playerIndex == 0) filteredList.size - 1 else playerIndex - 1
                }
                MusicToggleModes.SINGLE_LOOP -> playerIndex
                else -> -1
            }
        } else {
            // For temp play items, wrap around to end
            if (playerIndex == 0) filteredList.size - 1 else playerIndex - 1
        }

        if (prevIndex < 0) { stop(); return }

        val musicInfo = filteredList[prevIndex]
        setPlayMusicInfo(listId ?: "", musicInfo)
        handlePlay()
    }

    // === Temp Play List (Play Later) ===

    fun addToTempPlayList(musicInfo: MusicInfo, listId: String? = this.listId) {
        tempPlayList.add(PlayMusicInfo(musicInfo = musicInfo, listId = listId ?: "", isTempPlay = true))
    }

    fun clearTempPlayList() {
        tempPlayList.clear()
    }

    // === Played List ===

    fun clearPlayedList() {
        playedList.clear()
    }

    // === Dislike Current Song ===

    fun dislikeCurrentMusic() {
        val current = _currentMusicInfo.value ?: return
        // Add to dislike list (future: delegate to DislikeListManager)
        playNext(isAuto = true)
    }

    // === Private Helpers ===

    private fun handlePlay() {
        val playMusicInfo = _currentMusicInfo.value ?: return
        addToPlayedList(playMusicInfo)
        cancelAutoSkip()
        // Stop playback immediately so the old song doesn't keep playing during URL fetch
        exoPlayerManager.pause()
        // Stop old progress polling
        progressJob?.cancel()
        progressJob = null
        lastProgressSave = 0L
        lastSaveTime = 0L
        // Only reset position for new songs; keep saved position for resume
        val isRestore = pendingSeekMs > 0 && (pendingSeekSongId == null || pendingSeekSongId == playMusicInfo.musicInfo.id)
        if (!isRestore) {
            _progress.update { it.copy(nowPlayTime = 0L, progress = 0f, nowPlayTimeStr = "00:00") }
        }
        // Save state on every song change (RN saves listId/index unconditionally)
        savePlayState()
        scope.launch {
            try {
                val s = settingsManager.getSettings()
                val musicInfo = playMusicInfo.musicInfo
                val apiSourceId = s.apiSource

                // Find the right music source:
                // 1. If user selected a user_api source → use it directly
                // 2. Otherwise, try built-in source for this song's source
                // 3. Built-in sources need a user_api script — auto-detect if available
                val selectedSource = if (apiSourceId.startsWith("user_api_")) {
                    sourceRegistry.getSource(apiSourceId)
                } else {
                    sourceRegistry.getSource(musicInfo.source)
                        ?: aggregateSearch.getSource(musicInfo.source)
                }

                // Check music URL cache first
                var url = getCachedUrl(musicInfo.id, s.playQuality)
                // Try the selected source if no cache
                if (url.isEmpty()) url = selectedSource?.getMusicUrl(musicInfo, s.playQuality)?.getOrNull() ?: ""

                // If built-in source failed (returns "Custom source required"), auto-try user_api
                if (url.isEmpty() && selectedSource != null && !apiSourceId.startsWith("user_api_")) {
                    val userApiSource = sourceRegistry.getAllSources()
                        .firstOrNull { it is UserApiSource }
                    if (userApiSource != null) {
                        url = userApiSource.getMusicUrl(musicInfo, s.playQuality).getOrNull() ?: ""
                    }
                }

                // Toggle source: if all failed, search other sources for the same song
                var toggledCandidate: MusicInfo? = null
                if (url.isEmpty()) {
                    val toggleResult = tryToggleSource(musicInfo, s.playQuality, selectedSource?.sourceId)
                    if (toggleResult != null) {
                        url = toggleResult.url
                        toggledCandidate = toggleResult.candidate
                    }
                }

                if (url.isNotEmpty()) {
                    // Cache the music URL
                    saveCachedUrl(musicInfo.id, s.playQuality, url)
                    // Clear previous artwork before loading new one
                    exoPlayerManager.setArtworkData(null)
                    // Fetch cover BEFORE playing — must run on IO thread for HTTP calls
                    var pic: String? = withContext(Dispatchers.IO) {
                        var p: String? = loadCachedPic(musicInfo.id)
                        android.util.Log.d("PlayerController", "Pic fetch: cached=${p != null}, source=${musicInfo.source}, songId=${musicInfo.meta.songId}")
                        if (p.isNullOrEmpty()) {
                            try { p = selectedSource?.getPic(musicInfo)?.getOrNull() } catch (e: Exception) {
                                android.util.Log.e("PlayerController", "Pic primary error: ${e.message}", e)
                            }
                            if (p.isNullOrEmpty()) {
                                for (bs in aggregateSearch.allSources) {
                                    if (bs.sourceId != musicInfo.source) continue
                                    try { val p2 = bs.getPic(musicInfo).getOrNull(); if (!p2.isNullOrEmpty()) { p = p2; break } } catch (_: Exception) {}
                                }
                            }
                            if (!p.isNullOrEmpty()) saveCachedPic(musicInfo.id, p)
                        }
                        p
                    }
                    if (!pic.isNullOrEmpty()) _albumArt.value = pic

                    // Download cover bitmap and push to MediaSession as artworkData
                    // (Xiaomi Miao Play reads METADATA_KEY_ALBUM_ART which requires byte[])
                    val artworkBytes: ByteArray? = if (!pic.isNullOrEmpty()) {
                        withContext(Dispatchers.IO) { downloadArtworkBytes(pic, musicInfo.id) }
                    } else null
                    if (artworkBytes != null) {
                        exoPlayerManager.setArtworkData(artworkBytes)
                    }

                    val durMs = parseIntervalMs(musicInfo.interval)
                    exoPlayerManager.playUrl(url, musicInfo.name, musicInfo.singer, pic, durMs)
                    cancelAutoSkip()
                    ensureDesktopLyricService()
                    _isPlaying.value = true
                    // Pre-seed duration from API interval (fallback if ExoPlayer can't detect it)
                    val seedDur = parseIntervalMs(musicInfo.interval)
                    if (seedDur > 0 && _progress.value.maxPlayTime <= 0) {
                        _progress.update { it.copy(maxPlayTime = seedDur, maxPlayTimeStr = formatTime(seedDur)) }
                    }
                    // Seek to saved position (only for the restored song, not subsequent ones)
                    if (pendingSeekMs > 0) {
                        if (pendingSeekSongId == null || pendingSeekSongId == musicInfo.id) {
                            exoPlayerManager.seekTo(pendingSeekMs)
                            _progress.update { it.copy(nowPlayTime = pendingSeekMs, nowPlayTimeStr = formatTime(pendingSeekMs)) }
                        }
                        pendingSeekMs = 0L
                        pendingSeekSongId = null
                    }
                    startProgressTracking()
                    savePlayState()

                    // Fetch lyrics — use toggled source if auto-switched, else original
                    val src = if (toggledCandidate != null) {
                        aggregateSearch.getSource(toggledCandidate.source) ?: selectedSource
                    } else selectedSource
                    val info = musicInfo
                    val lyricMusicInfo = toggledCandidate ?: musicInfo
                    launch(Dispatchers.IO) {
                        android.util.Log.d("LyricFallback", "START lyric fetch for ${lyricMusicInfo.source}/${lyricMusicInfo.id} (toggled=${toggledCandidate != null})")
                        // Lyric: check cache first, then API (use toggled candidate's ID for proper caching)
                        var lyric: cn.guoyujie666.music.compose.core.model.LyricInfo? = loadCachedLyric(lyricMusicInfo.id)
                        android.util.Log.d("LyricFallback", "cached lyric: ${if (lyric == null) "null" else "len=${lyric.lyric.length} lxlyric=${lyric.lxlyric?.length ?: 0}"}")
                        // Re-fetch if cached lyric is missing lxlyric (may have been cached before lx support was added)
                        if (lyric != null && lyric.lxlyric.isNullOrBlank() && lyric.lyric.isNotBlank()) {
                            android.util.Log.d("PlayerController", "Cached lyric missing lxlyric, re-fetching ${info.source}")
                            lyric = null
                        }
                        val triedSources = mutableSetOf<String>()
                        if (lyric == null || lyric.lyric.isBlank()) {
                            try { lyric = src?.getLyric(lyricMusicInfo)?.getOrNull() } catch (_: Exception) {}
                            android.util.Log.d("LyricFallback", "after src getLyric: lyric=${lyric != null} src=${src != null}")
                            if (src != null) triedSources.add(src.sourceId)
                            android.util.Log.d("LyricFallback", "after triedSources add: lyric=${lyric?.lyric?.take(50)} isBlank=${lyric?.lyric?.isBlank()}")
                            if (lyric == null || lyric.lyric.isBlank()) {
                                android.util.Log.d("LyricFallback", "entering fallback loop, allSources.size=${aggregateSearch.allSources.size}")
                                for (bs in aggregateSearch.allSources) {
                                    if (bs.sourceId in triedSources) continue
                                    try {
                                        val l = bs.getLyric(lyricMusicInfo).getOrNull()
                                        android.util.Log.d("LyricFallback", "tried ${bs.sourceId} for ${lyricMusicInfo.source}: ${if (l != null) "got len=${l.lyric.length} lxlyric=${l.lxlyric?.length ?: 0}" else "null"}")
                                        if (l != null && l.lyric.isNotBlank()) { lyric = l; break }
                                    } catch (e: Exception) {
                                        android.util.Log.e("LyricFallback", "error ${bs.sourceId}: ${e.message}")
                                    }
                                }
                            }
                            // Save to cache
                            if (lyric != null && lyric.lyric.isNotBlank()) saveCachedLyric(lyricMusicInfo.id, lyric)
                        }
                        if (lyric != null && lyric.lyric.isNotBlank()) {
                            android.util.Log.d("LyricFallback", "lxlyric present: ${lyric.lxlyric != null} len=${lyric.lxlyric?.length ?: 0} sample=${lyric.lxlyric?.take(100)}")
                            val parsed = lrcParser.fromLyricInfo(lyric)
                            android.util.Log.d("LyricFallback", "parsed lines=${parsed.lines.size} first=${parsed.lines.firstOrNull()?.text?.take(50)} hasWords=${parsed.lines.firstOrNull()?.words != null}")
                            _lyricLines.value = parsed.lines
                        }

                        // Pic already fetched before playUrl; lyrics update only
                    }
                } else {
                    _isPlaying.value = false
                    val hasUserApi = sourceRegistry.getAllSources()
                        .any { it is UserApiSource }
                    if (hasUserApi) {
                        showError("音源脚本解析失败或超时，请检查音源脚本")
                    } else {
                        showError("请先在设置→基本→管理自定义音源中导入并启用音源脚本")
                    }
                    scheduleAutoSkip()
                }
            } catch (e: Exception) {
                _isPlaying.value = false
                showError("播放失败: ${e.message}")
                scheduleAutoSkip()
            }
        }
    }

    private fun setPlayMusicInfo(listId: String, musicInfo: MusicInfo) {
        _currentMusicInfo.value = PlayMusicInfo(
            musicInfo = musicInfo,
            listId = listId,
            isTempPlay = false
        )
        // Set play index within current list
        val idx = musicList.indexOfFirst { it.id == musicInfo.id }.takeIf { it >= 0 } ?: 0
        _playInfo.value = _playInfo.value.copy(
            playerPlayIndex = idx,
            playerListId = listId
        )
    }

    private fun clearPlayInfo() {
        _currentMusicInfo.value = null
        _playInfo.value = PlayInfo()
        _lyricLines.value = emptyList()
        _albumArt.value = null
        _progress.value = PlaybackProgress()
    }

    private fun addToPlayedList(item: PlayMusicInfo) {
        playedList.add(item)
    }

    /**
     * Find the next/previous song from the played list.
     * Scans played list entries to find valid songs that still exist in current list.
     */
    private fun getNextFromPlayedList(
        current: PlayMusicInfo,
        isNext: Boolean
    ): PlayMusicInfo? {
        if (playedList.isEmpty()) return null

        val currentId = current.musicInfo.id
        val currentIndex = playedList.indexOfFirst { it.musicInfo.id == currentId }
        if (currentIndex < 0) return null

        val direction = if (isNext) 1 else -1
        var i = currentIndex + direction
        while (i >= 0 && i < playedList.size) {
            val entry = playedList[i]
            if (musicList.any { it.id == entry.musicInfo.id }) {
                return entry
            }
            i += direction
        }
        return null
    }

    /**
     * Filter the current list: remove disliked, played, and incomplete download items.
     * Returns (filteredList, canPlayList, playerIndex).
     * Ported from filterList() in src/core/player/utils.ts.
     */
    private fun filterList(
        list: List<MusicInfo>,
        isNext: Boolean
    ): Triple<List<MusicInfo>, List<MusicInfo>, Int> {
        val current = _currentMusicInfo.value
        val currentMusicId = current?.musicInfo?.id

        // Get played song IDs from playedList
        val playedIds = playedList
            .filter { it.listId == listId && !it.isTempPlay }
            .map { it.musicInfo.id }
            .toSet()

        val filteredList = mutableListOf<MusicInfo>()
        val canPlayList = mutableListOf<MusicInfo>()
        var isDislike = false
        var playerIndex = -1

        for (item in list) {
            val isCurrentSong = item.id == currentMusicId

            // Check dislike (placeholder — full impl checks DislikeInfo)
            val isDisliked = false // Future: dislikeManager.isDisliked(item)

            if (isCurrentSong && isDisliked) {
                isDislike = true
                continue
            }

            canPlayList.add(item)

            if (isDisliked && !isCurrentSong) continue
            if (playedIds.contains(item.id) && !isCurrentSong) continue

            if (isCurrentSong) {
                playerIndex = filteredList.size
            }
            filteredList.add(item)
        }

        // Handle disliked current song edge case
        if (isDislike) {
            if (filteredList.size <= 1) {
                filteredList.clear()
                if (currentMusicId != null) {
                    playerIndex = canPlayList.indexOfFirst { it.id == currentMusicId }
                }
                canPlayList.removeAll { it.id == currentMusicId }
            } else {
                filteredList.removeAll { it.id == currentMusicId }
                if (isNext) playerIndex = max(0, playerIndex - 1)
            }
        }

        // Fallback: if filtered is empty but canPlayList has entries, use it
        if (filteredList.isEmpty() && canPlayList.isNotEmpty() && playedIds.isNotEmpty()) {
            clearPlayedList()
            return filterList(canPlayList, isNext)
        }

        return Triple(filteredList, canPlayList, playerIndex)
    }

    private fun resetRandomNextMusicInfo() {
        randomNextMusicInfo = null
    }

    // === Progress Tracking ===

    /**
     * Start polling ExoPlayer for real playback position.
     * Updates ~2 times per second.
     */
    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            // Don't reset position here — the first poll picks up the real position.
            // (handlePlay already cleared position, or seekTo set it for resume.)
            while (_isPlaying.value) {
                val pos = exoPlayerManager.currentPosition
                val dur = exoPlayerManager.duration
                // Prefer ExoPlayer's detected duration; fall back to API interval if player can't detect it
                val effectiveDur = if (dur > 0) dur else _progress.value.maxPlayTime
                if (effectiveDur > 0) {
                    _progress.update {
                        it.copy(
                            nowPlayTime = pos,
                            maxPlayTime = effectiveDur,
                            progress = pos.toFloat() / effectiveDur,
                            nowPlayTimeStr = formatTime(pos),
                            maxPlayTimeStr = formatTime(effectiveDur)
                        )
                    }
                } else {
                    // Duration completely unknown — still show elapsed time ticking
                    _progress.update {
                        it.copy(nowPlayTime = pos, nowPlayTimeStr = formatTime(pos))
                    }
                }
                // Save progress periodically (every 0.5s), always save position
                if (pos > 0 && pos - lastProgressSave > 500) {
                    lastProgressSave = pos
                    savePlayState()
                }
                // Bluetooth lyric: push current lyric line to notification
                if (bluetoothLyricEnabled) {
                    val lines = _lyricLines.value
                    val line = lines.lastOrNull { it.timeMs <= pos }
                    if (line != null && line.text.isNotEmpty()) {
                        val info = _currentMusicInfo.value?.musicInfo
                        val title = line.text
                        val artist = info?.let { "${it.name} - ${it.singer}" } ?: ""
                        if (title != lastBluetoothTitle) {
                            lastBluetoothTitle = title
                            exoPlayerManager.setMediaMetadata(title, artist, _albumArt.value)
                        }
                    }
                } else if (lastBluetoothTitle.isNotEmpty()) {
                    // Restore original metadata when bluetooth lyric is turned off
                    lastBluetoothTitle = ""
                    val info = _currentMusicInfo.value?.musicInfo
                    if (info != null) {
                        exoPlayerManager.setMediaMetadata(info.name, info.singer, _albumArt.value)
                    }
                }
                // Desktop lyric: send current line to floating overlay
                if (desktopLyricEnabled) {
                    try {
                        val dLines = _lyricLines.value
                        val dLine = dLines.lastOrNull { it.timeMs <= pos }
                        val dText = dLine?.text ?: ""
                        val dTrans = dLine?.translation
                        updateDesktopLyric(dText, dTrans, _isPlaying.value)
                    } catch (_: Exception) { /* Prevent lyric update crash from stopping playback */ }
                }
                delay(20)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun scheduleAutoSkip() {
        autoSkipJob?.cancel()
        autoSkipJob = scope.launch {
            delay(5000)
            playNext(isAuto = true)
        }
    }

    private fun cancelAutoSkip() {
        autoSkipJob?.cancel()
        autoSkipJob = null
    }

    /**
     * Try to find a playable URL from other music sources when the primary source fails.
     * Ported from RN's getOnlineOtherSourceMusicUrl + getOtherSource + findMusic.
     */
    /** Result of auto source toggle: URL found and the candidate song info (for lyrics). */
    private data class ToggleResult(val url: String, val candidate: MusicInfo)

    private suspend fun tryToggleSource(
        musicInfo: MusicInfo,
        quality: String,
        excludeSource: String?
    ): ToggleResult? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var result: ToggleResult? = null
        val keyword = "${musicInfo.name} ${musicInfo.singer}".trim()
        android.util.Log.i("PlayerController", "toggle source start, keyword=$keyword, exclude=$excludeSource, quality=$quality")
        // Search all enabled sources (except the current one)
        val candidates = aggregateSearch.allSources
            .filter { it.isEnabled && it.sourceId != excludeSource }
            .flatMap { src ->
                runCatching {
                    src.searchMusic(keyword, 1, 20).getOrNull()?.list ?: emptyList()
                }.getOrDefault(emptyList())
            }

        android.util.Log.i("PlayerController", "toggle source candidates from all sources: ${candidates.size}")
        if (candidates.isEmpty()) {
            android.util.Log.w("PlayerController", "toggle source: no candidates found")
        } else {
            // Match the best candidate by name + singer similarity (ported from RN's findMusic)
            val filterStr = { s: CharSequence ->
                s.toString().lowercase().replace(Regex("""\s|'|\.|,|，|&|"|、|\(|\)|（|）|`|~|-|<|>|\||\/|\]|\[|!|！"""), "")
            }
            val fName = filterStr(musicInfo.name)
            val fSinger = filterStr(musicInfo.singer)

            val scored = candidates.map { candidate ->
                val cName = filterStr(candidate.name)
                val cSinger = filterStr(candidate.singer)
                var score = 0
                if (cName == fName) score += 100
                else if (cName.contains(fName) || fName.contains(cName)) score += 60
                else if (cName.length > 2 && fName.length > 2) {
                    val common = cName.commonPrefixWith(fName).length + cName.commonSuffixWith(fName).length
                    if (common > 2) score += 30
                }
                if (cSinger == fSinger) score += 50
                else if (cSinger.isNotEmpty() && (cSinger.contains(fSinger) || fSinger.contains(cSinger))) score += 30
                Pair(candidate, score)
            }
                .filter { it.second >= 50 }
                .sortedByDescending { it.second }
                .map { it.first }

            android.util.Log.i("PlayerController", "toggle source scored candidates: ${scored.map { "${it.name}@${it.source}(${it.interval})" }.take(5)}")

            // Use user API source for URL fetching (built-in sources return "Custom source required")
            val userApiSource = sourceRegistry.getAllSources().firstOrNull { it is UserApiSource }
            for (candidate in scored) {
                android.util.Log.i("PlayerController", "try toggle to: ${candidate.source}, ${candidate.name}, ${candidate.singer}, ${candidate.interval}")
                val url = if (userApiSource != null) {
                    runCatching {
                        userApiSource.getMusicUrl(candidate, quality).getOrNull()
                    }.getOrNull() ?: ""
                } else {
                    // Fallback: try the built-in source (will fail without user API)
                    val src = aggregateSearch.getSource(candidate.source) ?: continue
                    runCatching { src.getMusicUrl(candidate, quality).getOrNull() }.getOrNull() ?: ""
                }
                if (url.isNotEmpty()) {
                    android.util.Log.i("PlayerController", "toggle source success: ${candidate.source}")
                    result = ToggleResult(url, candidate)
                    break
                } else {
                    android.util.Log.w("PlayerController", "toggle source failed for: ${candidate.source}")
                }
            }
            if (result == null) {
                android.util.Log.w("PlayerController", "toggle source: all candidates exhausted, no URL found")
            }
        }
        result
    }

    // ── Cache helpers ──────────────────────────────────────────
    private val cachePrefs = context.getSharedPreferences("lx_cache", android.content.Context.MODE_PRIVATE)

    private fun getCachedUrl(songId: String, quality: String): String {
        return cachePrefs.getString("@url__${songId}_$quality", null) ?: ""
    }

    private fun saveCachedUrl(songId: String, quality: String, url: String) {
        cachePrefs.edit().putString("@url__${songId}_$quality", url).apply()
    }

    private fun loadCachedLyric(songId: String): cn.guoyujie666.music.compose.core.model.LyricInfo? {
        return try {
            val json = cachePrefs.getString("@lyric__$songId", null) ?: return null
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
                .decodeFromString(cn.guoyujie666.music.compose.core.model.LyricInfo.serializer(), json)
        } catch (_: Exception) { null }
    }

    private fun saveCachedLyric(songId: String, info: cn.guoyujie666.music.compose.core.model.LyricInfo) {
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .encodeToString(cn.guoyujie666.music.compose.core.model.LyricInfo.serializer(), info)
            cachePrefs.edit().putString("@lyric__$songId", json).apply()
        } catch (_: Exception) {}
    }

    private fun loadCachedPic(songId: String): String? {
        return cachePrefs.getString("@pic__$songId", null)
    }

    private fun saveCachedPic(songId: String, url: String) {
        cachePrefs.edit().putString("@pic__$songId", url).apply()
        // Also download and cache the bitmap to disk
        Thread {
            try {
                val cacheDir = java.io.File(context.cacheDir, "covers")
                cacheDir.mkdirs()
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bitmap = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                conn.disconnect()
                if (bitmap != null) {
                    val file = java.io.File(cacheDir, "${songId}.jpg")
                    java.io.FileOutputStream(file).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    /**
     * Download album art as byte[] for MediaSession metadata.
     * Checks disk cache first, then fetches from network.
     * Returns JPEG bytes suitable for MediaMetadata.artworkData.
     */
    private fun downloadArtworkBytes(url: String, songId: String): ByteArray? {
        try {
            // Check disk cache first
            val cacheDir = java.io.File(context.cacheDir, "covers")
            val cachedFile = java.io.File(cacheDir, "${songId}.jpg")
            if (cachedFile.exists() && cachedFile.length() > 0) {
                return cachedFile.readBytes()
            }
            // Download from network
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000; conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
            conn.instanceFollowRedirects = true
            val bitmap = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
            conn.disconnect()
            if (bitmap != null) {
                val baos = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos)
                val bytes = baos.toByteArray()
                // Save to disk cache
                cacheDir.mkdirs()
                cachedFile.writeBytes(bytes)
                return bytes
            }
        } catch (e: Exception) {
            android.util.Log.w("PlayerController", "Failed to download artwork bytes: ${e.message}")
        }
        return null
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    /** Parse "MM:SS" or "M:SS" interval string to milliseconds. Returns 0 on failure. */
    private fun parseIntervalMs(interval: String?): Long {
        if (interval.isNullOrBlank()) return 0L
        val parts = interval.trim().split(":")
        if (parts.size != 2) return 0L
        val minutes = parts[0].toLongOrNull() ?: return 0L
        val seconds = parts[1].toLongOrNull() ?: return 0L
        return (minutes * 60 + seconds) * 1000
    }

    // === Cleanup ===
    fun destroy() {
        savePlayState()
        stopProgressTracking()
        pause()
        clearPlayInfo()
    }
}
