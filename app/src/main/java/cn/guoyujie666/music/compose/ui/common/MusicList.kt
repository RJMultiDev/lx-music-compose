package cn.guoyujie666.music.compose.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.ui.i18n.t

/**
 * Reusable online music list with pull-to-refresh and load-more.
 *
 * Ported from src/components/OnlineList/List.tsx.
 *
 * @param musicList The list of music items.
 * @param status Current fetch status (idle, loading, refreshing, end, error).
 * @param onRefresh Called when user pulls to refresh.
 * @param onLoadMore Called when user scrolls near the bottom.
 * @param onPlay Called when an item is tapped.
 * @param showAlbumName Whether to show album name in items.
 * @param showSource Whether to show source badge.
 */
enum class ListStatus { IDLE, LOADING, REFRESHING, END, ERROR }

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun MusicList(
    musicList: List<MusicInfo>,
    status: ListStatus = ListStatus.IDLE,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onPlay: (MusicInfo) -> Unit = {},
    showAlbumName: Boolean = false,
    showSource: Boolean = true,
    showInterval: Boolean = true,
    showQuality: Boolean = true,
    showAction: Boolean = false,
    onAction: ((MusicInfo) -> Unit)? = null,
    onPlayLater: ((MusicInfo) -> Unit)? = null,
    onAddTo: ((MusicInfo) -> Unit)? = null,
    onShare: ((MusicInfo) -> Unit)? = null,
    onDislike: ((MusicInfo) -> Unit)? = null,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    // Detect when scrolled near bottom for load-more
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= layoutInfo.totalItemsCount - 5
        }
    }

    // Trigger load more
    if (shouldLoadMore && status == ListStatus.IDLE && musicList.isNotEmpty()) {
        onLoadMore()
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    PullToRefreshBox(
        isRefreshing = status == ListStatus.REFRESHING,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        when {
            status == ListStatus.ERROR -> {
                ErrorView(
                    message = "Load failed",
                    onRetry = onRefresh,
                    modifier = Modifier.fillMaxSize()
                )
            }
            status == ListStatus.LOADING && musicList.isEmpty() -> {
                LoadingView(modifier = Modifier.fillMaxSize())
            }
            musicList.isEmpty() && status == ListStatus.IDLE -> {
                EmptyView(modifier = Modifier.fillMaxSize())
            }
            else -> {
                LazyColumn(state = listState,
                    contentPadding = PaddingValues(bottom = navBarPadding)) {
                    itemsIndexed(musicList) { index, item ->
                        MusicListItem(
                            musicInfo = item,
                            index = index,
                            showAlbumName = showAlbumName,
                            showSource = showSource,
                            showInterval = showInterval,
                            showQuality = showQuality,
                            showAction = showAction,
                            onPlay = onPlay,
                            onAction = onAction,
                            onPlayLater = onPlayLater,
                            onAddTo = onAddTo,
                            onShare = onShare,
                            onDislike = onDislike
                        )
                    }

                    // Footer
                    item {
                        when (status) {
                            ListStatus.LOADING -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                            ListStatus.END -> {
                                Text(
                                    text = t("loaded_all"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun EmptyView(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "No content",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
