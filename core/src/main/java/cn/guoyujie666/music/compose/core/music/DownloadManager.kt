package cn.guoyujie666.music.compose.core.music

import cn.guoyujie666.music.compose.core.model.DownloadListItem
import cn.guoyujie666.music.compose.core.model.DownloadProgressInfo
import cn.guoyujie666.music.compose.core.model.DownloadTaskStatuses
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.model.Quality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Download manager for offline music playback.
 *
 * Ported from src/core/music/download/.
 * Manages a queue of download tasks with progress tracking.
 */
data class DownloadTask(
    val id: String,
    val musicInfo: MusicInfo,
    val quality: Quality,
    val fileName: String,
    val filePath: String
)

@Singleton
class DownloadManager @Inject constructor() {

    private val _downloads = MutableStateFlow<List<DownloadListItem>>(emptyList())
    val downloads: StateFlow<List<DownloadListItem>> = _downloads.asStateFlow()

    private val activeTasks = mutableMapOf<String, DownloadTask>()
    private val progressMap = mutableMapOf<String, DownloadProgressInfo>()

    fun addToQueue(musicInfo: MusicInfo, quality: Quality, fileName: String, filePath: String): String {
        val id = "dl_${System.currentTimeMillis()}_${musicInfo.id}"
        val task = DownloadTask(id, musicInfo, quality, fileName, filePath)
        activeTasks[id] = task

        val item = DownloadListItem(
            id = id,
            status = DownloadTaskStatuses.WAITING,
            metadata = cn.guoyujie666.music.compose.core.model.DownloadMetadata(
                musicInfo = musicInfo,
                quality = quality,
                fileName = fileName,
                filePath = filePath
            )
        )
        _downloads.value = _downloads.value + item
        return id
    }

    fun startDownload(id: String) {
        val item = _downloads.value.find { it.id == id } ?: return
        val updated = item.copy(status = DownloadTaskStatuses.RUN)
        _downloads.value = _downloads.value.map { if (it.id == id) updated else it }

        // Future: actual HTTP download with Ktor, writing to file,
        // updating progress via progressMap and _downloads flow
    }

    fun pauseDownload(id: String) {
        updateStatus(id, DownloadTaskStatuses.PAUSE)
    }

    fun resumeDownload(id: String) {
        startDownload(id)
    }

    fun cancelDownload(id: String) {
        activeTasks.remove(id)
        progressMap.remove(id)
        _downloads.value = _downloads.value.filter { it.id != id }
    }

    fun clearCompleted() {
        _downloads.value = _downloads.value.filter { it.status != DownloadTaskStatuses.COMPLETED }
    }

    private fun updateStatus(id: String, status: String) {
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
    }

    fun updateProgress(id: String, progress: DownloadProgressInfo) {
        progressMap[id] = progress
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(
                progress = progress.progress,
                downloaded = progress.downloaded,
                total = progress.total,
                speed = progress.speed
            ) else it
        }
    }
}
