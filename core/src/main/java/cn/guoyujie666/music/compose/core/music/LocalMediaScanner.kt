package cn.guoyujie666.music.compose.core.music

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import cn.guoyujie666.music.compose.core.model.KnownSources
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.model.MusicInfoMetaLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans device storage for local audio files.
 *
 * Ported from src/core/music/local/.
 * Uses Android MediaStore to discover music files.
 */
@Singleton
class LocalMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class ScanResult(
        val files: List<MusicInfo>,
        val totalCount: Int = 0
    )

    /**
     * Scan all audio files on the device using MediaStore.
     */
    fun scanAll(): List<MusicInfo> {
        val results = mutableListOf<MusicInfo>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, null, sortOrder)
            cursor?.let {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Unknown"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val durationMs = it.getLong(durationCol)
                    val filePath = it.getString(dataCol) ?: continue
                    val displayName = it.getString(nameCol) ?: title
                    val ext = displayName.substringAfterLast('.', "mp3")

                    val interval = formatDuration(durationMs)

                    val musicInfo = MusicInfo(
                        id = "local_$id",
                        name = title,
                        singer = artist,
                        source = KnownSources.LOCAL,
                        interval = interval,
                        meta = MusicInfoMetaLocal(
                            songId = filePath,
                            albumName = album,
                            picUrl = null,
                            filePath = filePath,
                            ext = ext
                        )
                    )
                    results.add(musicInfo)
                }
            }
        } catch (e: Exception) {
            // Permission denied or other error
        } finally {
            cursor?.close()
        }

        return results
    }

    /**
     * Scan a specific directory for audio files.
     */
    fun scanDirectory(path: String): List<MusicInfo> {
        val dir = java.io.File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles()
            ?.filter { it.isFile && isAudioFile(it.name) }
            ?.map { file ->
                val name = file.nameWithoutExtension
                MusicInfo(
                    id = "local_${file.absolutePath.hashCode()}",
                    name = name,
                    singer = "Unknown Artist",
                    source = KnownSources.LOCAL,
                    meta = MusicInfoMetaLocal(
                        songId = file.absolutePath,
                        albumName = "",
                        picUrl = null,
                        filePath = file.absolutePath,
                        ext = file.extension
                    )
                )
            } ?: emptyList()
    }

    private fun isAudioFile(name: String): Boolean {
        val lower = name.lowercase()
        return AudioExtensions.any { lower.endsWith(".$it") }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val m = seconds / 60
        val s = seconds % 60
        return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    companion object {
        private val AudioExtensions = listOf(
            "mp3", "flac", "wav", "ape", "ogg", "m4a", "aac", "wma", "opus"
        )
    }
}
