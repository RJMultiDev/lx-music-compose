package cn.guoyujie666.music.compose.ui.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.guoyujie666.music.compose.core.model.PlayerMusicInfo
import cn.guoyujie666.music.compose.ui.home.viewmodel.PlayerViewModel
import cn.guoyujie666.music.compose.ui.i18n.t
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentScreen(
    musicInfo: PlayerMusicInfo? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val commentsData by playerViewModel.comments.collectAsState()

    LaunchedEffect(Unit) { playerViewModel.fetchComments() }

    val hotList = commentsData?.first ?: emptyList()
    val newList = commentsData?.second ?: emptyList()
    val hot = hotList.map { CommentData(it.id, it.userName ?: "", it.avatar, it.content, it.time ?: "", it.likes) }
    val nw  = newList.map { CommentData(it.id, it.userName ?: "", it.avatar, it.content, it.time ?: "", it.likes) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = {
                Column {
                    Text(t("comments"), style = MaterialTheme.typography.titleMedium)
                    if (musicInfo != null) {
                        Text("${musicInfo.name} - ${musicInfo.singer}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { playerViewModel.fetchComments() }) { Icon(Icons.Default.Refresh, t("refresh")) } }
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Row(verticalAlignment = Alignment.CenterVertically) { Text(t("hot")); Spacer(Modifier.width(4.dp)); Badge { Text("${hot.size}") } } }
            )
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Row(verticalAlignment = Alignment.CenterVertically) { Text(t("new")); Spacer(Modifier.width(4.dp)); Badge { Text("${nw.size}") } } }
            )
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            CommentList(comments = if (page == 0) hot else nw, isLoading = false, onLoadMore = {})
        }
    }
}

@Composable
fun CommentList(comments: List<CommentData>, isLoading: Boolean, onLoadMore: () -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        if (comments.isEmpty() && !isLoading) {
            item { Text(t("no_comments"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp)) }
        }
        items(comments.size) { index ->
            CommentItemView(comment = comments[index])
            HorizontalDivider()
        }
    }
}

@Composable
fun CommentItemView(comment: CommentData, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(comment.userName.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.userName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(comment.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.content, style = MaterialTheme.typography.bodyMedium, maxLines = 10, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ThumbUp, "Like", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("${comment.likes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

data class CommentData(
    val id: String, val userName: String = "", val avatar: String? = null,
    val content: String = "", val time: String = "", val likes: Int = 0
)
