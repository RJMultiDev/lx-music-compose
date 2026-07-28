package cn.guoyujie666.music.compose.core.songlist

import android.content.Context
import cn.guoyujie666.music.compose.core.model.MusicInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class UserSongList(
    val id: String,
    val name: String,
    val source: String? = null,
    val sourceListId: String? = null
)

@Singleton
class SongListManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("lx_songlists", Context.MODE_PRIVATE)
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    private val _lists = MutableStateFlow<List<UserSongList>>(emptyList())
    val lists: StateFlow<List<UserSongList>> = _lists.asStateFlow()

    /** Incremented on every songs change so UI can react. */
    private val _songsVersion = MutableStateFlow(0L)
    val songsVersion: StateFlow<Long> = _songsVersion.asStateFlow()

    private fun bumpSongs() { _songsVersion.value++ }

    init {
        loadLists()
    }

    // ── List metadata ──────────────────────────────────────────

    private fun loadLists() {
        try {
            val jsonStr = prefs.getString("list_meta", null) ?: return
            val list = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(UserSongList.serializer()),
                jsonStr
            )
            _lists.value = list
        } catch (_: Exception) {}
    }

    private fun saveLists() {
        try {
            val jsonStr = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(UserSongList.serializer()),
                _lists.value
            )
            prefs.edit().putString("list_meta", jsonStr).apply()
        } catch (_: Exception) {}
    }

    fun createList(name: String, source: String? = null, sourceListId: String? = null): UserSongList {
        val id = "user_${System.currentTimeMillis()}"
        val list = UserSongList(id, name, source, sourceListId)
        _lists.value = _lists.value + list
        saveLists()
        bumpSongs()
        return list
    }

    fun deleteList(id: String) {
        _lists.value = _lists.value.filter { it.id != id }
        saveLists()
        // Clear songs for this list
        prefs.edit().remove("songs_$id").apply()
    }

    fun renameList(id: String, name: String) {
        _lists.value = _lists.value.map { if (it.id == id) it.copy(name = name) else it }
        saveLists()
    }

    // ── Songs in a list ────────────────────────────────────────

    fun getSongs(listId: String): List<MusicInfo> {
        return try {
            val jsonStr = prefs.getString("songs_$listId", null) ?: return emptyList()
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(MusicInfo.serializer()),
                jsonStr
            )
        } catch (_: Exception) { emptyList() }
    }

    fun saveSongs(listId: String, songs: List<MusicInfo>) {
        try {
            val jsonStr = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(MusicInfo.serializer()),
                songs
            )
            prefs.edit().putString("songs_$listId", jsonStr).apply()
        } catch (_: Exception) {}
    }

    fun addSong(listId: String, song: MusicInfo): Boolean {
        val songs = getSongs(listId).toMutableList()
        if (songs.any { it.id == song.id }) return false // already exists
        songs.add(song)
        saveSongs(listId, songs)
        bumpSongs()
        return true
    }

    fun removeSong(listId: String, songId: String) {
        val songs = getSongs(listId).toMutableList()
        songs.removeAll { it.id == songId }
        saveSongs(listId, songs)
        bumpSongs()
    }

    fun removeSongs(listId: String, songIds: Set<String>) {
        val songs = getSongs(listId).toMutableList()
        songs.removeAll { it.id in songIds }
        saveSongs(listId, songs)
        bumpSongs()
    }

    fun moveSong(fromListId: String, toListId: String, song: MusicInfo): Boolean {
        if (toListId == fromListId) return false
        val fromSongs = getSongs(fromListId).toMutableList()
        if (fromSongs.none { it.id == song.id }) return false
        val toSongs = getSongs(toListId).toMutableList()
        if (toSongs.any { it.id == song.id }) return false
        fromSongs.removeAll { it.id == song.id }
        toSongs.add(song)
        saveSongs(fromListId, fromSongs)
        saveSongs(toListId, toSongs)
        bumpSongs()
        return true
    }

    /** Replace all songs in a list (for reorder / bulk update). */
    fun setSongs(listId: String, songs: List<MusicInfo>) {
        saveSongs(listId, songs)
        bumpSongs()
    }

    fun containsSong(listId: String, songId: String): Boolean {
        return getSongs(listId).any { it.id == songId }
    }
}
