package cn.guoyujie666.music.compose.core.model

import kotlinx.serialization.Serializable

// === Download Task Status ===

typealias DownloadTaskStatus = String

object DownloadTaskStatuses {
    const val RUN = "run"
    const val WAITING = "waiting"
    const val PAUSE = "pause"
    const val ERROR = "error"
    const val COMPLETED = "completed"
}

// === File Extension ===

typealias FileExt = String

object FileExtensions {
    const val MP3 = "mp3"
    const val FLAC = "flac"
    const val WAV = "wav"
    const val APE = "ape"
}

// === Download Progress ===

@Serializable
data class DownloadProgressInfo(
    val progress: Float = 0f,
    val speed: String = "",
    val downloaded: Long = 0,
    val total: Long = 0
)

// === Download List Item ===

@Serializable
data class DownloadListItem(
    val id: String,
    val isComplete: Boolean = false,
    val status: DownloadTaskStatus = DownloadTaskStatuses.WAITING,
    val statusText: String = "",
    val downloaded: Long = 0,
    val total: Long = 0,
    val progress: Float = 0f,
    val speed: String = "",
    val metadata: DownloadMetadata = DownloadMetadata()
)

@Serializable
data class DownloadMetadata(
    val musicInfo: MusicInfo = MusicInfo("", "", "", KnownSources.KW),
    val url: String? = null,
    val quality: Quality = KnownQualities._128K,
    val ext: FileExt = FileExtensions.MP3,
    val fileName: String = "",
    val filePath: String = ""
)

// === Save Download Music Info ===

@Serializable
data class SaveDownloadMusicInfo(
    val list: List<DownloadListItem>,
    val addMusicLocationType: AddMusicLocationType
)
