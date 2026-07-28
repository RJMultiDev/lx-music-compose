@file:OptIn(ExperimentalMaterial3Api::class)

package cn.guoyujie666.music.compose.ui.songlistdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.ui.i18n.t
import cn.guoyujie666.music.compose.ui.common.AddToPlaylistDialog
import cn.guoyujie666.music.compose.ui.common.ListStatus
import cn.guoyujie666.music.compose.ui.common.MusicList
import cn.guoyujie666.music.compose.ui.home.viewmodel.UserListViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun SonglistDetailScreen(
    listId: String,
    source: String = "",
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SonglistDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val playerVM: cn.guoyujie666.music.compose.ui.home.viewmodel.PlayerViewModel = hiltViewModel()
    val settingsVM: cn.guoyujie666.music.compose.ui.home.viewmodel.SettingsViewModel = hiltViewModel()
    val settings by settingsVM.settings.collectAsState()
    val userListVM: UserListViewModel = hiltViewModel()
    val userLists by userListVM.userLists.collectAsState()
    var showAddToDialog by remember { mutableStateOf(false) }
    var pendingAddToSong by remember { mutableStateOf<MusicInfo?>(null) }
    var descExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(listId, source) {
        viewModel.load(listId, source)
    }

    // Wrap in ThemeBackground so background image shows on this page too.
    cn.guoyujie666.music.compose.ui.theme.ThemeBackground(modifier.fillMaxSize()) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("歌单详情", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )

            if (state.isLoading && state.songs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(120.dp).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.pic != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(state.pic).crossfade(true).build(),
                                    contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text("🎵", style = MaterialTheme.typography.displaySmall)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(state.name.ifEmpty { "Song List" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            if (state.author.isNotEmpty()) {
                                Text(state.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(formatPlayCount(state.playCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.desc.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    state.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (descExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (state.desc.length > 50) {
                                    Text(
                                        if (descExpanded) "收起" else "展开",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { descExpanded = !descExpanded }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Check if this songlist is already collected (exists in user lists)
                    val collectedList = userLists.find { it.name == state.name }
                    Row(Modifier.fillMaxWidth()) {
                        FilledTonalButton(onClick = {
                                if (state.songs.isNotEmpty()) playerVM.playList("songlist", state.songs, 0)
                            }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t("play_all"))
                        }
                        Spacer(Modifier.width(12.dp))
                        FilledTonalButton(onClick = {
                            val existing = userLists.find { it.name == state.name }
                            if (existing != null) {
                                userListVM.deleteList(existing.id)
                            } else {
                                val list = userListVM.createList(
                                    state.name.ifEmpty { "歌单" },
                                    source = source.ifBlank { null },
                                    sourceListId = listId.ifBlank { null }
                                )
                                state.songs.forEach { userListVM.addSong(list.id, it) }
                            }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(
                                if (collectedList != null) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                                null, Modifier.size(20.dp),
                                tint = if (collectedList != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(t("collect"), color = if (collectedList != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    Text(t("songs_count_unit", "n" to "${state.songs.size}"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                    if (state.error != null && state.songs.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                            Text("Load failed", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                            Text(state.error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Song list — fills remaining space, scrolls independently
                if (state.songs.isNotEmpty() || state.isRefreshing) {
                    MusicList(
                        musicList = state.songs, status = if (state.isRefreshing) ListStatus.REFRESHING else ListStatus.IDLE,
                        onRefresh = { viewModel.refresh() }, onLoadMore = { viewModel.loadMore() },
                        onPlay = { playerVM.playSong(it) }, showSource = false, showAction = true,
                        showAlbumName = settings.listIsShowAlbumName,
                        showInterval = settings.listIsShowInterval,
                        onPlayLater = { playerVM.addToTempPlayList(it) },
                        onAddTo = { pendingAddToSong = it; showAddToDialog = true },
                        modifier = Modifier.weight(1f, fill = true))
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
    } // ThemeBackground
}

@Composable
private fun formatPlayCount(count: Long): String = when {
    count >= 100_000_000L -> t("play_count_yi", "n" to "%.1f".format(count / 100_000_000.0))
    count >= 10_000L -> t("play_count_wan", "n" to "%.1f".format(count / 10_000.0))
    else -> t("play_count", "n" to "$count")
}
