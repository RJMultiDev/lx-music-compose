package cn.guoyujie666.music.compose.ui.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline
import cn.guoyujie666.music.compose.core.music.AggregateSearch
import cn.guoyujie666.music.compose.core.music.SearchResult
import cn.guoyujie666.music.compose.core.music.SongListItem
import cn.guoyujie666.music.compose.core.setting.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val hotSearches: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val results: List<MusicInfo> = emptyList(),
    val total: Int = 0, val page: Int = 1,
    val isLoading: Boolean = false, val isRefreshing: Boolean = false, val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
    val selectedSource: String = "all",
    val searchType: Int = 0, // 0=music, 1=songlist
    val songlistResults: List<SongListItem> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: AggregateSearch,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Levenshtein-based similarity score (ported from RN utils/common similar) */
    private fun similar(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var shorter = a; var longer = b
        if (a.length > b.length) { shorter = b; longer = a }
        val sl = shorter.length; val ll = longer.length
        val mp = IntArray(ll + 1) { it }
        for (i in 1..sl) {
            val ai = shorter[i - 1]
            var lt = mp[0]
            mp[0] = mp[0] + 1
            for (j in 1..ll) {
                val tmp = minOf(mp[j] + 1, mp[j - 1] + 1, lt + if (ai == longer[j - 1]) 0 else 1)
                lt = mp[j]
                mp[j] = tmp
            }
        }
        return 1.0 - (mp[ll].toDouble() / ll)
    }

    /** Sort + deduplicate results by keyword relevance (ported from RN setLists) */
    private fun sortAndDedupe(list: List<MusicInfo>, keyword: String): List<MusicInfo> {
        if (list.isEmpty()) return list
        // Deduplicate by name + singer
        val seen = mutableSetOf<String>()
        val deduped = list.filter { seen.add("${it.name}|${it.singer}") }
        // Sort by similarity to keyword (RN handleSortList)
        if (keyword.isBlank()) return deduped
        return deduped.map { item ->
            item to similar(keyword, "${item.name} ${item.singer}")
        }.sortedByDescending { it.second }.map { it.first }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val s = settingsManager.getSettings()
            // Restore persisted source and history
            val history = try {
                json.parseToJsonElement(s.searchHistoryList).jsonArray.map { it.jsonPrimitive.content }
            } catch (_: Exception) { emptyList() }
            _state.value = _state.value.copy(
                selectedSource = s.searchSource,
                searchHistory = history
            )
            loadHotSearch()
        }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        if (query.isNotBlank()) loadSuggestions(query)
        else _state.value = _state.value.copy(suggestions = emptyList())
    }

    fun selectSource(source: String) {
        _state.value = _state.value.copy(selectedSource = source)
        loadHotSearch()
        val q = _state.value.query
        if (q.isNotBlank()) search(q)
        // Persist source selection
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.updateSettings(settingsManager.getSettings().copy(searchSource = source))
        }
    }

    fun setSearchType(type: Int) {
        if (_state.value.searchType == type) return
        _state.value = _state.value.copy(searchType = type)
        val q = _state.value.query
        if (q.isNotBlank() && _state.value.hasSearched) search(q)
    }

    private fun loadHotSearch() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = if (_state.value.selectedSource == "all")
                search.hotSearchAll().values.flatMap { it.take(20) }.distinct()
            else search.hotSearchOne(_state.value.selectedSource).take(20)
            _state.value = _state.value.copy(hotSearches = list)
        }
    }

    private fun loadSuggestions(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Always use KW (酷我) for search suggestions — most stable API,
            // consistent with the RN version which hardcodes temp_source = 'kw'
            val src = search.getSource("kw")
            src?.tipSearch(keyword)?.onSuccess { r ->
                _state.value = _state.value.copy(suggestions = r.list)
            }
        }
    }

    fun search(query: String) {
        val q = query.ifBlank { _state.value.query }
        if (q.isBlank()) return
        val history = (listOf(q) + _state.value.searchHistory).distinct().take(12)
        _state.value = _state.value.copy(query = q, isSearching = true, hasSearched = true, error = null, searchHistory = history, page = 1)

        // Persist history
        viewModelScope.launch(Dispatchers.IO) {
            val historyJson = history.joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
            settingsManager.updateSettings(settingsManager.getSettings().copy(searchHistoryList = historyJson))
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_state.value.searchType == 1) {
                    val list = if (_state.value.selectedSource == "all")
                        search.searchSongListAll(q, 1)
                    else search.searchSongListOne(_state.value.selectedSource, q, 1)
                    _state.value = _state.value.copy(
                        songlistResults = list, total = list.size, page = 1,
                        isSearching = false, isRefreshing = false, error = null
                    )
                    return@launch
                }
                val result = if (_state.value.selectedSource == "all") {
                    val all = search.searchAll(q, 1, 30)
                    SearchResult(list = sortAndDedupe(all.flatMap { it.list }, q), total = all.sumOf { it.total })
                } else {
                    search.searchOne(_state.value.selectedSource, q, 1, 30)
                }
                _state.value = _state.value.copy(
                    results = result.list, total = result.total, page = 1,
                    isSearching = false, isRefreshing = false, error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSearching = false, isRefreshing = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.searchType == 1) return
        if (s.isLoading || s.results.size >= s.total) return
        _state.value = s.copy(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nextPage = s.page + 1
                val result = if (s.selectedSource == "all") {
                    val all = search.searchAll(s.query, nextPage, 30)
                    SearchResult(list = sortAndDedupe(all.flatMap { it.list }, s.query), total = all.sumOf { it.total })
                } else {
                    search.searchOne(s.selectedSource, s.query, nextPage, 30)
                }
                _state.value = _state.value.copy(
                    results = _state.value.results + result.list,
                    page = nextPage, isLoading = false
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun refresh() { _state.value = _state.value.copy(isRefreshing = true, page = 1); search(_state.value.query) }
    fun clearResults() { _state.value = _state.value.copy(results = emptyList(), songlistResults = emptyList(), query = "", hasSearched = false, error = null, isSearching = false, suggestions = emptyList()) }
    fun removeHistory(word: String) {
        _state.value = _state.value.copy(searchHistory = _state.value.searchHistory - word)
        // Persist removal
        viewModelScope.launch(Dispatchers.IO) {
            val h = _state.value.searchHistory
            val historyJson = h.joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
            settingsManager.updateSettings(settingsManager.getSettings().copy(searchHistoryList = historyJson))
        }
    }
    fun clearHistory() {
        _state.value = _state.value.copy(searchHistory = emptyList())
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.updateSettings(settingsManager.getSettings().copy(searchHistoryList = ""))
        }
    }

}
