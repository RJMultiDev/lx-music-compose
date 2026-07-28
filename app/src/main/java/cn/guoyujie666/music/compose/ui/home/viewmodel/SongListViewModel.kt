package cn.guoyujie666.music.compose.ui.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.guoyujie666.music.compose.core.music.AggregateSearch
import cn.guoyujie666.music.compose.core.music.SongListItem
import cn.guoyujie666.music.compose.core.music.SongListTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SongListUiState(
    val selectedSource: String = "kw",
    val selectedSort: String = "hot",   // kw/wy/mg: hot/new, kg/tx: 5/6/2 numeric
    val selectedTagId: String = "",      // empty = all categories
    val tags: List<SongListTag> = emptyList(),
    val isLoadingTags: Boolean = false,
    val items: List<SongListItem> = emptyList(),
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SongListViewModel @Inject constructor(
    private val search: AggregateSearch,
    private val settingsManager: cn.guoyujie666.music.compose.core.setting.SettingsManager
) : ViewModel() {
    private val _state = MutableStateFlow(SongListUiState())
    val state: StateFlow<SongListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val src = settingsManager.getSettings().searchSource
            val effective = if (src == "all" || src.isBlank()) "kw" else src
            if (effective != _state.value.selectedSource) {
                _state.value = _state.value.copy(selectedSource = effective)
                loadTags(); loadList()
            }
        }
        // Listen for global searchSource changes from other tabs
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.settingsFlow.collect { s ->
                val src = s.searchSource
                val effective = if (src == "all" || src.isBlank()) "kw" else src
                if (effective != _state.value.selectedSource) {
                    val defaultSort = when (effective) {
                        "kw" -> "hot"; "kg" -> "5"; "tx" -> "5"; "wy" -> "hot"; "mg" -> "1"; else -> "5"
                    }
                    _state.value = _state.value.copy(selectedSource = effective, selectedSort = defaultSort, selectedTagId = "", page = 1, items = emptyList())
                    loadTags(); loadList()
                }
            }
        }
        loadTags(); loadList()
    }

    fun selectSource(sourceId: String) {
        if (_state.value.selectedSource == sourceId) return
        val defaultSort = when (sourceId) {
            "kw" -> "hot"; "kg" -> "5"; "tx" -> "5"; "wy" -> "hot"; "mg" -> "1"; else -> "5"
        }
        _state.value = _state.value.copy(selectedSource = sourceId, selectedSort = defaultSort, selectedTagId = "", page = 1, items = emptyList())
        loadTags()
        loadList()
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.updateSettings(settingsManager.getSettings().copy(searchSource = sourceId))
        }
    }

    fun selectSort(sortId: String) {
        if (_state.value.selectedSort == sortId) return
        _state.value = _state.value.copy(selectedSort = sortId, page = 1, items = emptyList())
        loadList()
    }

    fun selectTag(tagId: String) {
        if (_state.value.selectedTagId == tagId) return
        _state.value = _state.value.copy(selectedTagId = tagId, page = 1, items = emptyList())
        loadList()
    }

    fun loadTags() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoadingTags = true)
            try {
                val src = search.getSource(_state.value.selectedSource)
                val tags = src?.getSongListTags()?.getOrElse { emptyList() } ?: emptyList()
                _state.value = _state.value.copy(tags = tags, isLoadingTags = false)
            } catch (_: Exception) {
                _state.value = _state.value.copy(tags = emptyList(), isLoadingTags = false)
            }
        }
    }

    fun loadList() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val src = search.getSource(_state.value.selectedSource)
                val s = _state.value
                val items = src?.getSongList(s.selectedSort, s.selectedTagId, s.page)?.getOrElse { emptyList() } ?: emptyList()
                _state.value = _state.value.copy(items = items, isLoading = false, isRefreshing = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, isRefreshing = false, error = e.message)
            }
        }
    }

    fun loadNextPage() {
        val s = _state.value
        if (s.isLoading) return
        _state.value = s.copy(page = s.page + 1)
        loadList()
    }

    fun refresh() {
        _state.value = _state.value.copy(isRefreshing = true, page = 1, items = emptyList())
        loadList()
    }
}
