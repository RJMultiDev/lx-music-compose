package cn.guoyujie666.music.compose.ui.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.music.AggregateSearch
import cn.guoyujie666.music.compose.core.music.LeaderboardItem
import cn.guoyujie666.music.compose.core.music.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaderboardUiState(
    val selectedSource: String = "kw",
    val boards: List<LeaderboardItem> = emptyList(),
    val selectedBoard: LeaderboardItem? = null,
    val songs: List<MusicInfo> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val search: AggregateSearch,
    private val settingsManager: cn.guoyujie666.music.compose.core.setting.SettingsManager
) : ViewModel() {
    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val src = settingsManager.getSettings().searchSource
            val effective = if (src == "all" || src.isBlank()) "kw" else src
            if (effective != _state.value.selectedSource) {
                _state.value = _state.value.copy(selectedSource = effective)
                loadBoards()
            }
        }
        // Listen for global searchSource changes from other tabs
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.settingsFlow.collect { s ->
                val src = s.searchSource
                val effective = if (src == "all" || src.isBlank()) "kw" else src
                if (effective != _state.value.selectedSource) {
                    _state.value = _state.value.copy(selectedSource = effective, boards = emptyList(), selectedBoard = null, songs = emptyList())
                    loadBoards()
                }
            }
        }
        loadBoards()
    }

    fun selectSource(sourceId: String) {
        if (_state.value.selectedSource == sourceId) return
        _state.value = _state.value.copy(selectedSource = sourceId, boards = emptyList(), selectedBoard = null, songs = emptyList())
        loadBoards()
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.updateSettings(settingsManager.getSettings().copy(searchSource = sourceId))
        }
    }

    fun selectBoard(board: LeaderboardItem) {
        if (_state.value.selectedBoard?.id == board.id) return
        _state.value = _state.value.copy(selectedBoard = board, songs = emptyList(), page = 1)
        loadSongs()
    }

    fun loadBoards() {
        viewModelScope.launch(Dispatchers.IO) {
            val src = search.getSource(_state.value.selectedSource)
            val boards = src?.getLeaderboards()?.getOrElse { emptyList() } ?: emptyList()
            val first = boards.firstOrNull()
            _state.value = _state.value.copy(boards = boards, selectedBoard = first)
            if (first != null) loadSongs()
        }
    }

    fun loadSongs() {
        val board = _state.value.selectedBoard ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true)
            val src = search.getSource(_state.value.selectedSource)
            val result = src?.getLeaderboardDetail(board.id, _state.value.page, 100)?.getOrElse { SearchResult() }
                ?: SearchResult()
            val merged = if (_state.value.page == 1) result.list else (_state.value.songs + result.list)
            _state.value = _state.value.copy(songs = merged, total = result.total, isLoading = false, isRefreshing = false)
        }
    }

    fun refresh() { _state.value = _state.value.copy(isRefreshing = true, page = 1, songs = emptyList()); loadSongs() }
    fun loadMore() { val s = _state.value; if (!s.isLoading && s.songs.size < s.total) { _state.value = s.copy(page = s.page + 1); loadSongs() } }
}
