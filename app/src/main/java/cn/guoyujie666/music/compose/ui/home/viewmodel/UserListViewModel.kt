package cn.guoyujie666.music.compose.ui.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.backup.BackupManager
import cn.guoyujie666.music.compose.core.music.AggregateSearch
import cn.guoyujie666.music.compose.core.songlist.SongListManager
import cn.guoyujie666.music.compose.core.songlist.UserSongList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserListViewModel @Inject constructor(
    private val songListManager: SongListManager,
    val backupManager: BackupManager,
    private val aggregateSearch: AggregateSearch
) : ViewModel() {

    val userLists: StateFlow<List<UserSongList>> = songListManager.lists
    val songsVersion: StateFlow<Long> = songListManager.songsVersion

    private val _syncingIds = MutableStateFlow(setOf<String>())
    val syncingIds: StateFlow<Set<String>> = _syncingIds.asStateFlow()

    fun getSongs(listId: String): List<MusicInfo> = songListManager.getSongs(listId)
    fun getSongCount(listId: String): Int = songListManager.getSongs(listId).size
    fun addSong(listId: String, song: MusicInfo): Boolean = songListManager.addSong(listId, song)
    fun removeSong(listId: String, songId: String) = songListManager.removeSong(listId, songId)
    fun removeSongs(listId: String, songIds: Set<String>) = songListManager.removeSongs(listId, songIds)
    fun moveSong(fromListId: String, toListId: String, song: MusicInfo): Boolean =
        songListManager.moveSong(fromListId, toListId, song)
    fun createList(name: String, source: String? = null, sourceListId: String? = null): UserSongList =
        songListManager.createList(name, source, sourceListId)
    fun setSongs(listId: String, songs: List<MusicInfo>) = songListManager.setSongs(listId, songs)
    fun deleteList(id: String) = songListManager.deleteList(id)
    fun renameList(id: String, name: String) = songListManager.renameList(id, name)
    fun containsSong(listId: String, songId: String): Boolean = songListManager.containsSong(listId, songId)

    /**
     * Search for a song across all sources for the "切换音源" feature.
     * Returns results grouped by source ID.
     */
    suspend fun searchForToggleSource(name: String, singer: String): List<Pair<String, List<MusicInfo>>> =
        aggregateSearch.allSources.filter { it.isEnabled }.map { src ->
            src.sourceId to (src.searchMusic("$name $singer", 1, 20).getOrElse { cn.guoyujie666.music.compose.core.music.SearchResult() }.list)
        }.filter { it.second.isNotEmpty() }

    /**
     * Replace a song in the list with one from a different source.
     * Ported from RN's handleToggleSource in listAction.ts.
     */
    fun toggleSourceInList(
        listId: String,
        oldSong: MusicInfo,
        newSong: MusicInfo,
        oldIndex: Int,
        onDuplicateConfirm: () -> Boolean
    ) {
        val currentSongs = getSongs(listId).toMutableList()
        val oldId = oldSong.id
        var targetIdx = oldIndex
        val newId = newSong.id
        val dupIdx = currentSongs.indexOfFirst { it.id == newId }
        // Build new list: remove old + optional duplicate, then insert new at original position
        currentSongs.removeAll { it.id == oldId }
        if (dupIdx >= 0 && dupIdx != oldIndex) {
            if (!onDuplicateConfirm()) return
            currentSongs.removeAll { it.id == newId }
            if (dupIdx < targetIdx) targetIdx--
        }
        currentSongs.add(targetIdx.coerceIn(0, currentSongs.size), newSong)
        setSongs(listId, currentSongs)
    }

    /** Sync a collected song list from its source. Mirrors RN's syncSourceList. */
    fun syncList(list: UserSongList) {
        val src = list.source ?: return
        val srcListId = list.sourceListId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _syncingIds.value = _syncingIds.value + list.id
            try {
                val source = aggregateSearch.getSource(src) ?: return@launch
                var allSongs = emptyList<MusicInfo>()
                var page = 1
                while (true) {
                    val songs: List<MusicInfo>
                    val total: Int
                    if (srcListId.startsWith("board__")) {
                        val r = source.getLeaderboardDetail(srcListId.removePrefix("board__"), page, 100).getOrElse { break }
                        songs = r.list; total = r.total
                    } else {
                        val r = source.getSongListDetail(srcListId, page, 100).getOrElse { break }
                        songs = r.list; total = r.total
                    }
                    allSongs = allSongs + songs
                    if (allSongs.size >= total || songs.isEmpty()) break
                    page++
                }
                if (allSongs.isNotEmpty()) {
                    songListManager.setSongs(list.id, allSongs)
                }
            } catch (_: Exception) {} finally {
                _syncingIds.value = _syncingIds.value - list.id
            }
        }
    }
}
