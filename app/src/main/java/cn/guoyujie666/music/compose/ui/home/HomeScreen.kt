@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package cn.guoyujie666.music.compose.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import cn.guoyujie666.music.compose.ui.navigation.Navigator
import cn.guoyujie666.music.compose.ui.navigation.PlayDetailRoute
import cn.guoyujie666.music.compose.ui.navigation.SonglistDetailRoute
import cn.guoyujie666.music.compose.ui.home.tabs.LeaderboardTab
import cn.guoyujie666.music.compose.ui.home.tabs.MyListTab
import cn.guoyujie666.music.compose.ui.home.tabs.SearchTab
import cn.guoyujie666.music.compose.ui.home.tabs.SettingsTab
import cn.guoyujie666.music.compose.ui.common.MiniPlayer
import cn.guoyujie666.music.compose.ui.home.viewmodel.PlayerViewModel
import cn.guoyujie666.music.compose.ui.home.viewmodel.SettingsViewModel
import android.widget.Toast
import cn.guoyujie666.music.compose.core.event.AppEvent
import cn.guoyujie666.music.compose.core.event.EventBus
import cn.guoyujie666.music.compose.ui.i18n.t
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.ui.home.tabs.SongListTab

private data class HomeTab(
    val label: String, val icon: ImageVector, val selectedIcon: ImageVector
)

private val tabIcons = listOf(
    Icons.Outlined.Search to Icons.Outlined.Search,
    Icons.Outlined.Album to Icons.Outlined.Album,
    Icons.Outlined.Star to Icons.Outlined.Star,
    Icons.Outlined.FavoriteBorder to Icons.Filled.Favorite,
    Icons.Filled.Settings to Icons.Filled.Settings,
)

@Composable
fun HomeScreen(navigator: Navigator) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val playerVM: PlayerViewModel = hiltViewModel()
    val isPlaying by playerVM.isPlaying.collectAsState()
    val currentMusic by playerVM.currentMusicInfo.collectAsState()
    val pbProgress by playerVM.progress.collectAsState()
    val albumArt by playerVM.albumArt.collectAsState()
    val lyricLines by playerVM.lyricLines.collectAsState()
    val currentLyric = lyricLines.lastOrNull { it.timeMs <= pbProgress.nowPlayTime }?.text ?: ""
    val settingsVM: SettingsViewModel = hiltViewModel()
    val settings by settingsVM.settings.collectAsState()

    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val hideBar = settings.autoHidePlayBar && isKeyboardVisible

    val playSong: (MusicInfo) -> Unit = { song ->
        try { playerVM.playSong(song) } catch (_: Exception) {}
    }
    val pagerState = rememberPagerState(pageCount = { 5 }, initialPage = selectedTab)
    val coroutineScope = rememberCoroutineScope()

    // Tap on nav bar → animate pager (one-way)
    LaunchedEffect(selectedTab) { pagerState.animateScrollToPage(selectedTab) }
    // Swipe pager → sync nav bar
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (page, scrolling) -> if (!scrolling) selectedTab = page }
    }

    val tabLabels = listOf(t("tab_search"), t("tab_songlist"), t("tab_leaderboard"), t("tab_library"), t("tab_settings"))

    cn.guoyujie666.music.compose.ui.theme.ThemeBackground(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth > 600.dp

        if (isWide) {
            // Wide screen: NavigationRail on left, content + player on right
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(Modifier.height(8.dp))
                    tabLabels.forEachIndexed { index, label ->
                        val sel = selectedTab == index
                        val icons = tabIcons[index]
                        NavigationRailItem(
                            selected = sel,
                            onClick = { selectedTab = index },
                            icon = { Icon(if (sel) icons.second else icons.first, label) },
                            label = {
                                Text(label, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (!hideBar) MiniPlayerBar(
                            currentMusic = currentMusic, albumArt = albumArt,
                            pbProgress = pbProgress, isPlaying = isPlaying,
                            currentLyric = currentLyric, allowDrag = settings.allowProgressBarSeek,
                            onTogglePlay = { playerVM.togglePlay() },
                            onPlayNext = { playerVM.playNext() },
                            onSeek = { playerVM.seekTo(it) },
                            onClick = { navigator.push(PlayDetailRoute) }
                        )
                    }
                ) { padding ->
                    HorizontalPager(
                        state = pagerState, userScrollEnabled = settings.homePageScroll,
                        modifier = Modifier.fillMaxSize().padding(padding)
                    ) { page ->
                        when (page) {
                            0 -> SearchTab(onSongListClick = { id, src -> navigator.push(SonglistDetailRoute(id, src)) }, onPlay = playSong, onPlayLater = { playerVM.addToTempPlayList(it) })
                            1 -> SongListTab(onSongListClick = { id, src -> navigator.push(SonglistDetailRoute(id, src)) })
                            2 -> LeaderboardTab(onPlay = playSong)
                            3 -> MyListTab()
                            4 -> SettingsTab(navigator)
                        }
                    }
                }
            }
        } else {
            // Narrow screen: NavigationBar at bottom, player above it
            Scaffold(
                bottomBar = {
                    if (!hideBar) {
                        Column {
                            MiniPlayerBar(
                                currentMusic = currentMusic, albumArt = albumArt,
                                pbProgress = pbProgress, isPlaying = isPlaying,
                                currentLyric = currentLyric, allowDrag = settings.allowProgressBarSeek,
                                onTogglePlay = { playerVM.togglePlay() },
                                onPlayNext = { playerVM.playNext() },
                                onSeek = { playerVM.seekTo(it) },
                                onClick = { navigator.push(PlayDetailRoute) }
                            )

                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                tabLabels.forEachIndexed { index, label ->
                                    val sel = selectedTab == index
                                    val icons = tabIcons[index]
                                    NavigationBarItem(
                                        selected = sel,
                                        onClick = { selectedTab = index },
                                        icon = { Icon(if (sel) icons.second else icons.first, label) },
                                        label = {
                                            Text(label, style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                HorizontalPager(
                    state = pagerState, userScrollEnabled = settings.homePageScroll,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) { page ->
                    when (page) {
                        0 -> SearchTab(onSongListClick = { id, src -> navigator.push(SonglistDetailRoute(id, src)) }, onPlay = playSong, onPlayLater = { playerVM.addToTempPlayList(it) })
                        1 -> SongListTab(onSongListClick = { id, src -> navigator.push(SonglistDetailRoute(id, src)) })
                        2 -> LeaderboardTab(onPlay = playSong)
                        3 -> MyListTab()
                        4 -> SettingsTab(navigator)
                    }
                }
            }
        }
    }
    } // ThemeBackground
}

@Composable
private fun MiniPlayerBar(
    currentMusic: cn.guoyujie666.music.compose.core.model.PlayMusicInfo?,
    albumArt: String?,
    pbProgress: cn.guoyujie666.music.compose.core.model.PlaybackProgress,
    isPlaying: Boolean,
    currentLyric: String,
    allowDrag: Boolean,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onClick: () -> Unit
) {
    val song = currentMusic?.musicInfo
    val picUrl = albumArt ?: (song?.meta as? cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline)?.picUrl
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column {
            if (allowDrag && pbProgress.maxPlayTime > 0) {
                var barWidth by remember { mutableStateOf(1f) }
                var isDragging by remember { mutableStateOf(false) }
                var dragP by remember { mutableFloatStateOf(pbProgress.progress) }
                val dp = if (isDragging) dragP else pbProgress.progress
                // 12dp touch target with 3dp visual bars centered vertically.
                // Background matches Surface color so the gap above/below the bar
                // blends in and doesn't appear as a white strip.
                Box(Modifier.fillMaxWidth().height(12.dp)
                    .onSizeChanged { barWidth = it.width.toFloat() }
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .pointerInput(Unit) { detectTapGestures { pos -> onSeek((pos.x / barWidth * pbProgress.maxPlayTime).toLong()) } }
                    .pointerInput(Unit) { detectHorizontalDragGestures(
                        onDragStart = { offset -> isDragging = true; dragP = (offset.x / barWidth).coerceIn(0f, 1f) },
                        onDragEnd = { isDragging = false; onSeek((dragP * pbProgress.maxPlayTime).toLong()) },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { _, a -> dragP = (dragP + a / barWidth).coerceIn(0f, 1f) }
                    )},
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    Box(Modifier.fillMaxWidth(dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                }
            } else {
                LinearProgressIndicator(
                    progress = { pbProgress.progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (picUrl != null) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current).data(picUrl).size(80).build(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🎵", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (song != null) "${song.name} - ${song.singer}" else "未在播放",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (currentLyric.isNotEmpty()) {
                        Text(
                            text = currentLyric,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    "${pbProgress.nowPlayTimeStr}/${pbProgress.maxPlayTimeStr}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onPlayNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
