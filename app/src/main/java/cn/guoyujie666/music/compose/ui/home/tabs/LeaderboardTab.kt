@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.guoyujie666.music.compose.ui.home.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.ui.common.AddToPlaylistDialog
import cn.guoyujie666.music.compose.ui.common.ListStatus
import cn.guoyujie666.music.compose.ui.common.MusicList
import cn.guoyujie666.music.compose.ui.home.viewmodel.LeaderboardViewModel
import cn.guoyujie666.music.compose.ui.home.viewmodel.PlayerViewModel
import cn.guoyujie666.music.compose.ui.home.viewmodel.UserListViewModel
import cn.guoyujie666.music.compose.ui.i18n.t

private data class LbSourceMeta(val id: String, val name: String)
private val sourceIds = listOf("kw", "kg", "tx", "wy", "mg")

@Composable
fun LeaderboardTab(onPlay: (MusicInfo) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val playerVM: cn.guoyujie666.music.compose.ui.home.viewmodel.PlayerViewModel = hiltViewModel()
    val settingsVM: cn.guoyujie666.music.compose.ui.home.viewmodel.SettingsViewModel = hiltViewModel()
    val settings by settingsVM.settings.collectAsState()
    val userListVM: UserListViewModel = hiltViewModel()
    val userLists by userListVM.userLists.collectAsState()
    var showAddToDialog by remember { mutableStateOf(false) }
    var pendingAddToSong by remember { mutableStateOf<MusicInfo?>(null) }
    val useAlias = settings.sourceNameType == "alias"
    val srcAliases = mapOf("kw" to "小蜗音乐", "kg" to "小枸音乐", "tx" to "小秋音乐", "wy" to "小芸音乐", "mg" to "小蜜音乐")

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(t("tab_leaderboard"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // Source + Board dropdowns
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var srcExp by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = srcExp, onExpandedChange = { srcExp = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = if (useAlias) srcAliases[state.selectedSource] ?: state.selectedSource else t("src_${state.selectedSource}"),
                        onValueChange = {}, readOnly = true, label = { Text(t("source")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = srcExp) },
                        modifier = Modifier.menuAnchor(), singleLine = true)
                    ExposedDropdownMenu(expanded = srcExp, onDismissRequest = { srcExp = false }) {
                        sourceIds.forEach { sid ->
                            DropdownMenuItem(text = { Text(if (useAlias) srcAliases[sid] ?: sid else t("src_$sid"), fontWeight = if (sid == state.selectedSource) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { viewModel.selectSource(sid); srcExp = false })
                        }
                    }
                }
                var brdExp by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = brdExp, onExpandedChange = { brdExp = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = state.selectedBoard?.name ?: t("select_board"),
                        onValueChange = {}, readOnly = true, label = { Text(t("board")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brdExp) },
                        modifier = Modifier.menuAnchor(), singleLine = true)
                    ExposedDropdownMenu(expanded = brdExp, onDismissRequest = { brdExp = false }) {
                        state.boards.forEach { b ->
                            DropdownMenuItem(text = { Text(b.name, fontWeight = if (b.id == state.selectedBoard?.id) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { viewModel.selectBoard(b); brdExp = false })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        // Song list
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = { viewModel.refresh() }, modifier = Modifier.fillMaxSize()) {
            if (state.isLoading && state.songs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.songs.isEmpty() && !state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t("no_songs"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                MusicList(
                    musicList = state.songs,
                    status = if (state.isRefreshing) ListStatus.REFRESHING else ListStatus.IDLE,
                    onRefresh = { viewModel.refresh() },
                    onLoadMore = { viewModel.loadMore() },
                    onPlay = onPlay,
                    onPlayLater = { playerVM.addToTempPlayList(it) },
                    onAddTo = { pendingAddToSong = it; showAddToDialog = true },
                    showAlbumName = settings.listIsShowAlbumName,
                    showInterval = settings.listIsShowInterval,
                    showSource = false,
                    showAction = true,
                    modifier = Modifier.fillMaxSize())
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
    }
}
