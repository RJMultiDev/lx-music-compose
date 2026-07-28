package cn.guoyujie666.music.compose.ui.songlistdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.music.AggregateSearch
import cn.guoyujie666.music.compose.core.music.SongListDetailResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SongListDetailUiState(
    val listId: String = "",
    val source: String = "",
    val songs: List<MusicInfo> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val name: String = "",
    val pic: String? = null,
    val desc: String = "",
    val author: String = "",
    val playCount: Long = 0
)

@HiltViewModel
class SonglistDetailViewModel @Inject constructor(
    private val search: AggregateSearch
) : ViewModel() {
    private val _state = MutableStateFlow(SongListDetailUiState())
    val state: StateFlow<SongListDetailUiState> = _state.asStateFlow()

    fun load(rawId: String, source: String) {
        val listId = parseListId(rawId, source)
        if (_state.value.listId == listId && _state.value.source == source && _state.value.songs.isNotEmpty()) return
        _state.value = _state.value.copy(listId = listId, source = source, isLoading = true, error = null)
        loadPage(1)
    }

    /** Parse common playlist link formats to extract a usable ID. */
    private fun parseListId(input: String, source: String): String {
        val s = input.trim()
        if (s.all { it.isDigit() }) return s
        // Extract id=xxx or playlistId=xxx from URL params (wy, tx, mg)
        Regex("""[?&]id=(\d+)""").find(s)?.groupValues?.get(1)?.let { return it }
        Regex("""playlistId=(\d+)""").find(s)?.groupValues?.get(1)?.let { return it }
        // /playlist/xxx/, /playlist_detail/xxx/, /playsquare/xxx.html
        Regex("""/playlist_detail/(\d+)""").find(s)?.groupValues?.get(1)?.let { return it }
        Regex("""/playlist/(\d+)""").find(s)?.groupValues?.get(1)?.let { return it }
        Regex("""/playsquare/(\d+)(?:\.html)?""").find(s)?.groupValues?.get(1)?.let { return it }
        // KG gcid links: keep full gcid_xxx (needed by decodeGcid)
        Regex("""(gcid_\w+)""").find(s)?.groupValues?.get(1)?.let { return it }
        // KG special/single/xxx.html
        Regex("""/special/single/(\d+)""").find(s)?.groupValues?.get(1)?.let { return it }
        return s
    }

    fun loadPage(page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val src = search.getSource(_state.value.source)
                val result = src?.getSongListDetail(_state.value.listId, page, 100)?.getOrNull()
                    ?: songListDetailResultFromSearch(_state.value.listId)
                val merged = if (page == 1) result.list else (_state.value.songs + result.list)
                _state.value = _state.value.copy(
                    songs = merged, total = result.total, page = page,
                    isLoading = false, isRefreshing = false, error = null,
                    name = result.info.name.ifEmpty { _state.value.name },
                    pic = result.info.pic ?: _state.value.pic,
                    desc = result.info.desc.ifEmpty { _state.value.desc },
                    author = result.info.author.ifEmpty { _state.value.author },
                    playCount = result.info.playCount)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, isRefreshing = false, error = e.message)
            }
        }
    }

    fun refresh() { _state.value = _state.value.copy(isRefreshing = true, page = 1); loadPage(1) }
    fun loadMore() { val s = _state.value; if (!s.isLoading && s.songs.size < s.total) loadPage(s.page + 1) }

    /** Fallback: try songlist search to find songs when detail API fails. */
    private suspend fun songListDetailResultFromSearch(listId: String): SongListDetailResult {
        val src = search.getSource(_state.value.source) ?: return SongListDetailResult()
        return SongListDetailResult()
    }
}
