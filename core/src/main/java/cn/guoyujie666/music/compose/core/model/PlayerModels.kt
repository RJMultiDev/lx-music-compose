package cn.guoyujie666.music.compose.core.model

import kotlinx.serialization.Serializable

// === Player Music Info (display-level, resolved from raw MusicInfo) ===

@Serializable
data class PlayerMusicInfo(
    val id: String? = null,
    val pic: String? = null,
    val lrc: String? = null,
    val tlrc: String? = null,
    val rlrc: String? = null,
    val lxlrc: String? = null,
    val rawlrc: String? = null,
    val name: String = "",
    val singer: String = "",
    val album: String = ""
)

// === Player Lyric Info (extends base LyricInfo with raw lyrics) ===

data class PlayerLyricInfo(
    val lyric: String = "",
    val tlyric: String? = null,
    val rlyric: String? = null,
    val lxlyric: String? = null,
    val rawlrcInfo: LyricInfo = LyricInfo()
)

// === Play Music Info (describes what's currently selected to play) ===

data class PlayMusicInfo(
    val musicInfo: MusicInfo,
    val listId: String,
    val isTempPlay: Boolean = false
)

// === Play Info (position within the current playlist) ===

data class PlayInfo(
    val playIndex: Int = -1,
    val playerListId: String? = null,
    val playerPlayIndex: Int = -1
)

// === Temp Play List Item ===

data class TempPlayListItem(
    val listId: String?,
    val musicInfo: MusicInfo,
    val isTop: Boolean = false
)

// === Saved Play Info (for resume) ===

@Serializable
data class SavedPlayInfo(
    val time: Long = 0,
    val maxTime: Long = 0,
    val listId: String = "",
    val index: Int = 0
)

// === Playback Progress ===

data class PlaybackProgress(
    val nowPlayTime: Long = 0,
    val maxPlayTime: Long = 0,
    val progress: Float = 0f,
    val nowPlayTimeStr: String = "00:00",
    val maxPlayTimeStr: String = "00:00",
    val buffered: Float = 0f   // buffer progress 0.0 - 1.0
)

// === Player Status ===

data class PlayerStatus(
    val isInitialized: Boolean = false,
    val isRegisteredService: Boolean = false,
    val isIniting: Boolean = false
)

// === Player Track ===

data class PlayerTrack(
    val musicId: String,
    val url: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val duration: Long = 0
)
