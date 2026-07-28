package cn.guoyujie666.music.compose.ui.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.guoyujie666.music.compose.core.lyric.LyricLine
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.model.PlayInfo
import cn.guoyujie666.music.compose.core.model.PlayMusicInfo
import cn.guoyujie666.music.compose.core.model.PlaybackProgress
import cn.guoyujie666.music.compose.core.model.PlayerStatus
import cn.guoyujie666.music.compose.core.player.PlayerController
import cn.guoyujie666.music.compose.core.setting.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val settingsManager: SettingsManager
) : ViewModel() {
    // Play detail UI settings persisted via DataStore
    private val _lyricFontSize = MutableStateFlow(16)
    val lyricFontSize = _lyricFontSize.asStateFlow()
    private val _transFontSize = MutableStateFlow(12)
    val transFontSize = _transFontSize.asStateFlow()
    private val _lyricAlign = MutableStateFlow("center")
    val lyricAlign = _lyricAlign.asStateFlow()
    private val _lyricSeekEnabled = MutableStateFlow(true)
    val lyricSeekEnabled = _lyricSeekEnabled.asStateFlow()
    private val _showWordHighlight = MutableStateFlow(true)
    val showWordHighlight = _showWordHighlight.asStateFlow()
    private val _showDuetLyric = MutableStateFlow(true)
    val showDuetLyric = _showDuetLyric.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsManager.getSettings()
            _lyricFontSize.value = s.lyricFontSize
            _transFontSize.value = s.transFontSize
            _lyricAlign.value = s.lyricAlign
            _lyricSeekEnabled.value = s.lyricSeekEnabled
            _showWordHighlight.value = s.showWordHighlight
            _showDuetLyric.value = s.showDuetLyric
        }
    }

    fun setLyricFontSize(v: Int) { _lyricFontSize.value = v; savePlaySettings() }
    fun setTransFontSize(v: Int) { _transFontSize.value = v; savePlaySettings() }
    fun setLyricAlign(v: String) { _lyricAlign.value = v; savePlaySettings() }
    fun setLyricSeekEnabled(v: Boolean) { _lyricSeekEnabled.value = v; savePlaySettings() }
    fun setShowWordHighlight(v: Boolean) { _showWordHighlight.value = v; savePlaySettings() }
    fun setShowDuetLyric(v: Boolean) { _showDuetLyric.value = v; savePlaySettings() }
    fun setBluetoothLyric(v: Boolean) {
        playerController.bluetoothLyricEnabled = v
    }
    fun setDesktopLyricEnabled(v: Boolean) {
        playerController.desktopLyricEnabled = v
        if (v) {
            // If already playing, start desktop lyric service immediately
            if (playerController.isPlaying.value) {
                playerController.ensureDesktopLyricService()
            }
        }
    }
    fun sendDesktopLyricStyle() = playerController.sendDesktopLyricStyle()

    /** Replace a song in the default list (试听列表) with one from a different source. */
    fun replaceInDefaultList(oldSong: MusicInfo, newSong: MusicInfo) {
        val list = playerController.getDefaultList().toMutableList()
        val idx = list.indexOfFirst { it.id == oldSong.id }
        list.removeAll { it.id == oldSong.id }
        list.add(idx.coerceIn(0, list.size), newSong)
        playerController.replaceDefaultList(list)
        // If the old song was playing, switch playback to the new song
        if (playerController.currentMusicInfo.value?.musicInfo?.id == oldSong.id) {
            viewModelScope.launch {
                playerController.playListById(
                    playerController.currentMusicInfo.value?.listId ?: "default",
                    newSong.id
                )
            }
        }
    }

    private fun savePlaySettings() {
        viewModelScope.launch {
            val current = settingsManager.getSettings()
            settingsManager.updateSettings(
                current.copy(
                    lyricFontSize = _lyricFontSize.value,
                    transFontSize = _transFontSize.value,
                    lyricAlign = _lyricAlign.value,
                    lyricSeekEnabled = _lyricSeekEnabled.value,
                    showDuetLyric = _showDuetLyric.value,
                    showWordHighlight = _showWordHighlight.value
                )
            )
        }
    }

    val isPlaying: StateFlow<Boolean> = playerController.isPlaying
    val currentMusicInfo: StateFlow<PlayMusicInfo?> = playerController.currentMusicInfo
    val progress: StateFlow<PlaybackProgress> = playerController.progress
    val volume: StateFlow<Float> = playerController.volume
    val playbackRate: StateFlow<Float> = playerController.playbackRate
    val playMode: StateFlow<String> = playerController.togglePlayMethod
    val status: StateFlow<PlayerStatus> = playerController.status
    val playInfo: StateFlow<PlayInfo> = playerController.playInfo
    val lyricLines: StateFlow<List<LyricLine>> = playerController.lyricLines
    val albumArt: StateFlow<String?> = playerController.albumArt
    val comments: StateFlow<Pair<List<cn.guoyujie666.music.compose.core.music.CommentItem>, List<cn.guoyujie666.music.compose.core.music.CommentItem>>?> = playerController.comments
    fun fetchComments() = playerController.fetchComments()

    // === Playback Control ===
    fun playList(listId: String, list: List<MusicInfo>, index: Int = 0) =
        playerController.playList(listId, list, index)

    fun playListById(listId: String, musicId: String) =
        playerController.playListById(listId, musicId)

    fun playSong(song: MusicInfo) = playerController.playFromDefault(song)
    fun playInList(listId: String, list: List<MusicInfo>, index: Int) = playerController.playInList(listId, list, index)
    fun getDefaultList(): List<MusicInfo> = playerController.getDefaultList()
    fun removeFromDefaultList(ids: Set<String>) = playerController.removeFromDefaultList(ids)
    fun addToDefaultList(songs: List<MusicInfo>) = playerController.addToDefaultList(songs)
    fun sortDefaultList(songs: List<MusicInfo>) = playerController.replaceDefaultList(songs)

    fun togglePlay() = playerController.togglePlay()
    fun cyclePlayMode() = playerController.cyclePlayMode()
    fun play() = playerController.play()
    fun pause() = playerController.pause()
    fun stop() = playerController.stop()
    fun playNext() = playerController.playNext(isAuto = true)
    fun playPrev() = playerController.playPrev(isAuto = false)
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun setVolume(vol: Float) = playerController.setVolume(vol)
    fun setPlaybackRate(rate: Float) = playerController.setPlaybackRate(rate)

    // === Playlist Management ===
    fun addToTempPlayList(musicInfo: MusicInfo, listId: String? = null) =
        playerController.addToTempPlayList(musicInfo, listId)

    fun dislikeCurrentMusic() = playerController.dislikeCurrentMusic()

    // === Cleanup ===
    // NOTE: Do NOT call playerController.destroy() here.
    // destroy() stops playback, which would kill music when navigating back.
    // PlayerController is a singleton — it lives for the app's lifetime.
}
