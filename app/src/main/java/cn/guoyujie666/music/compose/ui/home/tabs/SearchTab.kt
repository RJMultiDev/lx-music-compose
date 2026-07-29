@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package cn.guoyujie666.music.compose.ui.home.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.ui.common.AddToPlaylistDialog
import cn.guoyujie666.music.compose.ui.common.MusicListItem
import cn.guoyujie666.music.compose.ui.i18n.t
import cn.guoyujie666.music.compose.ui.home.viewmodel.SearchViewModel
import cn.guoyujie666.music.compose.ui.home.viewmodel.UserListViewModel

@Composable
fun SearchTab(onPlay: (cn.guoyujie666.music.compose.core.model.MusicInfo) -> Unit = {},
    modifier: Modifier = Modifier,
    onSongListClick: (listId: String, source: String) -> Unit = { _, _ -> },
    onPlayLater: (cn.guoyujie666.music.compose.core.model.MusicInfo) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val settingsVM: cn.guoyujie666.music.compose.ui.home.viewmodel.SettingsViewModel = hiltViewModel()
    val settings by settingsVM.settings.collectAsState()
    val playerVM: cn.guoyujie666.music.compose.ui.home.viewmodel.PlayerViewModel = hiltViewModel()
    val userListVM: UserListViewModel = hiltViewModel()
    val userLists by userListVM.userLists.collectAsState()
    var showAddToDialog by remember { mutableStateOf(false) }
    var pendingAddToSong by remember { mutableStateOf<MusicInfo?>(null) }
    var isFocused by remember { mutableStateOf(false) }
    val focusMgr = LocalFocusManager.current
    var localQuery by remember { mutableStateOf(state.query) }
    LaunchedEffect(state.query) { if (state.query != localQuery) localQuery = state.query }

    fun doSearch(word: String) {
        localQuery = word
        viewModel.search(word)
        focusMgr.clearFocus(); isFocused = false
    }

    val showResults = state.isSearching || state.hasSearched

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

        // ── Search Bar ───────────────────────────────────────────
        SearchBarExpressive(
            query = localQuery,
            onQueryChange = {
                localQuery = it
                viewModel.onQueryChange(it)
            },
            onSearch = { _ ->
                val term = localQuery
                if (term.isNotBlank()) {
                    viewModel.search(term)
                    focusMgr.clearFocus(); isFocused = false
                }
            },
            onClear = {
                localQuery = ""
                viewModel.clearResults()
            },
            isFocused = isFocused,
            onFocusChange = { isFocused = it }
        )

        // ── Music/Songlist + Source selector ─────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                SegmentedButton(selected = state.searchType == 0, onClick = { viewModel.setSearchType(0) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    label = { Text(t("music"), style = MaterialTheme.typography.labelMedium) },
                    icon = { Icon(Icons.Filled.MusicNote, null, Modifier.size(14.dp)) })
                SegmentedButton(selected = state.searchType == 1, onClick = { viewModel.setSearchType(1) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text(t("songlist"), style = MaterialTheme.typography.labelMedium) },
                    icon = { Icon(Icons.Outlined.Album, null, Modifier.size(14.dp)) })
            }

            var expanded by remember { mutableStateOf(false) }
            val useAlias = settings.sourceNameType == "alias"
            val srcNames = if (useAlias) linkedMapOf(
                "kw" to "小蜗音乐", "kg" to "小枸音乐", "tx" to "小秋音乐",
                "wy" to "小芸音乐", "mg" to "小蜜音乐", "all" to "聚合大会"
            ) else linkedMapOf(
                "kw" to t("src_kw"), "kg" to t("src_kg"), "tx" to t("src_tx"),
                "wy" to t("src_wy"), "mg" to t("src_mg"), "all" to t("src_all")
            )
            Box {
                FilterChip(selected = false, onClick = { expanded = true },
                    label = { Text(srcNames[state.selectedSource] ?: t("src_all"), style = MaterialTheme.typography.labelMedium) })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    srcNames.forEach { (id, name) ->
                        DropdownMenuItem(text = {
                            Text(name, fontWeight = if (id == state.selectedSource) FontWeight.Bold else FontWeight.Normal,
                                color = if (id == state.selectedSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }, onClick = { viewModel.selectSource(id); expanded = false })
                    }
                }
            }
        }

        // ── Content ──────────────────────────────────────────────
        when {
            showResults -> {
                val resultsEmpty = if (state.searchType == 1) state.songlistResults.isEmpty() else state.results.isEmpty()
                if (!state.isSearching && resultsEmpty) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t("no_results", "q" to state.query),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.error != null) Text(state.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                } else {
                    Text(if (state.searchType == 1) t("playlists_count", "n" to "${state.songlistResults.size}") else t("results_count", "n" to "${state.total}"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    if (state.isSearching && resultsEmpty) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (state.searchType == 1) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            gridItems(state.songlistResults) { sl ->
                                SongListCardExpressive(
                                    data = SongListCardData(sl.id, sl.name, sl.pic, sl.playCount, sl.author),
                                    onClick = { onSongListClick(sl.id, sl.source) })
                            }
                        }
                    } else {
                        val listState = rememberLazyListState()
                        // Scroll to top when source changes
                        LaunchedEffect(state.selectedSource) {
                            listState.scrollToItem(0)
                        }
                        val shouldLoadMore = remember {
                            derivedStateOf {
                                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                last >= state.results.size - 5 && !state.isLoading && state.results.size < state.total
                            }
                        }
                        LaunchedEffect(shouldLoadMore.value) {
                            if (shouldLoadMore.value) viewModel.loadMore()
                        }
                        LazyColumn(Modifier.fillMaxSize(), state = listState,
                            contentPadding = PaddingValues(bottom = 80.dp)) {
                            itemsIndexed(state.results) { idx, song ->
                                MusicListItem(song, idx,
                                showAlbumName = settings.listIsShowAlbumName,
                                showSource = settings.listIsShowSource && state.selectedSource == "all",
                                showInterval = settings.listIsShowInterval,
                                showAction = true, onPlay = onPlay, onPlayLater = onPlayLater,
                                onAddTo = { pendingAddToSong = it; showAddToDialog = true })
                                if (idx < state.results.lastIndex)
                                    HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                            if (state.isLoading) {
                                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                            }
                        }
                    }
                }
            }
            else -> {
                // Idle / suggestions
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    if (settings.searchIsShowHotSearch && state.hotSearches.isNotEmpty()) {
                        item {
                            Row(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Whatshot, null, tint = Color(0xFFFF6D00), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(t("trending_now"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                        item {
                            FlowRow(Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                state.hotSearches.forEach { word ->
                                    SuggestionChip(onClick = { doSearch(word) },
                                        label = { Text(word, style = MaterialTheme.typography.bodySmall) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                        modifier = Modifier.height(28.dp))
                                    }
                                }
                            }
                        }

                    if (settings.searchIsShowHistorySearch && state.searchHistory.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(t("recent"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(t("clear"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { viewModel.clearHistory() })
                            }
                        }
                        item {
                            FlowRow(Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                state.searchHistory.forEach { w ->
                                    SuggestionChip(onClick = { doSearch(w) },
                                        label = { Text(w, style = MaterialTheme.typography.bodySmall) },
                                        icon = {
                                            IconButton(onClick = { viewModel.removeHistory(w) }, modifier = Modifier.size(16.dp)) {
                                                Icon(Icons.Outlined.Close, "Remove", Modifier.size(12.dp))
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                        modifier = Modifier.height(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        } // Column

        // ── Suggestions overlay (floating, doesn't push content) ──
        if (isFocused && state.suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .align(Alignment.TopCenter).offset(y = 56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp
            ) {
                Column {
                    state.suggestions.take(8).forEach { word ->
                        Row(
                            Modifier.fillMaxWidth().clickable { doSearch(word) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(word, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        // ── Add to playlist dialog ──────────────────────────────
        if (showAddToDialog && pendingAddToSong != null) {
            AddToPlaylistDialog(
                song = pendingAddToSong!!,
                userLists = userLists,
                songCounts = mapOf(
                    "default" to playerVM.getDefaultList().size,
                    "love" to userListVM.getSongCount("love")
                ) + userLists.associate { it.id to userListVM.getSongCount(it.id) },
                onDismiss = { showAddToDialog = false; pendingAddToSong = null },
                onAddToList = { listId ->
                    val song = pendingAddToSong!!
                    if (listId == "default") {
                        playerVM.addToTempPlayList(song)
                    } else if (listId == "love") {
                        userListVM.addSong("love", song)
                    } else {
                        userListVM.addSong(listId, song)
                    }
                    showAddToDialog = false; pendingAddToSong = null
                },
                onCreateList = { name -> userListVM.createList(name) }
            )
        }
    } // Box
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarExpressive(
    query: String, onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit, onClear: () -> Unit,
    isFocused: Boolean, onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    cn.guoyujie666.music.compose.ui.common.SearchBar(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { value ->
            if (value.isBlank()) onClear() else onSearch(value)
        },
        placeholder = t("search_placeholder"),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 2.dp)
            .onFocusChanged { onFocusChange(it.isFocused) }
    )
}
