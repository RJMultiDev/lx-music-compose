package cn.guoyujie666.music.compose.ui.playdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import cn.guoyujie666.music.compose.R
import androidx.compose.ui.res.painterResource
import cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline
import cn.guoyujie666.music.compose.core.model.MusicToggleModes
import cn.guoyujie666.music.compose.ui.home.tabs.SettingsSliderItem
import cn.guoyujie666.music.compose.ui.home.tabs.SettingsSwitchItem
import cn.guoyujie666.music.compose.ui.home.viewmodel.PlayerViewModel
import cn.guoyujie666.music.compose.ui.i18n.t
import cn.guoyujie666.music.compose.ui.playdetail.components.LyricView
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayDetailScreen(
    onBack: () -> Unit = {},
    onShowComment: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentMusic by viewModel.currentMusicInfo.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val playbackRate by viewModel.playbackRate.collectAsState()
    val playMode by viewModel.playMode.collectAsState()
    val lyrics by viewModel.lyricLines.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    val lyricFontSize by viewModel.lyricFontSize.collectAsState()
    val transFontSize by viewModel.transFontSize.collectAsState()
    val lyricAlign by viewModel.lyricAlign.collectAsState()
    val lyricSeekEnabled by viewModel.lyricSeekEnabled.collectAsState()
    val showWordHighlight by viewModel.showWordHighlight.collectAsState()
    val showDuetLyric by viewModel.showDuetLyric.collectAsState()

    val musicInfo = currentMusic?.musicInfo
    val displayName = musicInfo?.name ?: t("not_playing")
    val displaySinger = musicInfo?.singer ?: t("select_song_to_play")
    val displayPic = albumArt ?: (musicInfo?.meta as? MusicInfoMetaOnline)?.picUrl
    val displayAlbum = (musicInfo?.meta as? MusicInfoMetaOnline)?.albumName

    val pagerState = rememberPagerState(pageCount = { 2 })
    val pagerPage = pagerState.currentPage

    cn.guoyujie666.music.compose.ui.theme.ThemeBackground(Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PlayDetailHeader(songName = displayName, singer = displaySinger, onBack = onBack)

        // HorizontalPager: album art (0) ↔ lyrics (1)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AlbumArtBox(picUrl = displayPic, albumName = displayAlbum, modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f).padding(16.dp))
                }
                1 -> LyricView(lyrics = lyrics, currentTime = progress.nowPlayTime, lyricFontSize = lyricFontSize.sp, transFontSize = transFontSize.sp, lyricAlign = lyricAlign, allowSeek = lyricSeekEnabled, showTranslation = true, showWordHighlight = showWordHighlight, showDuetLyric = showDuetLyric, onSeekToLine = { viewModel.seekTo(it) }, modifier = Modifier.fillMaxSize())
            }
        }

        // Action row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (playMode == MusicToggleModes.LIST) {
                ActionButton(painterResource(cn.guoyujie666.music.compose.R.drawable.list_arrow_24), MusicToggleModes.label(playMode)) { viewModel.cyclePlayMode() }
            } else {
                val modeIcon = when (playMode) {
                    MusicToggleModes.LIST_LOOP -> Icons.Default.Repeat
                    MusicToggleModes.RANDOM -> Icons.Filled.Shuffle
                    MusicToggleModes.SINGLE_LOOP -> Icons.Filled.RepeatOne
                    MusicToggleModes.NONE -> Icons.Filled.Block
                    else -> Icons.Default.Repeat
                }
                ActionButton(modeIcon, MusicToggleModes.label(playMode)) { viewModel.cyclePlayMode() }
            }
            ActionButton(Icons.Default.Comment, t("comment")) { onShowComment() }
            ActionButton(Icons.Default.DesktopWindows, t("desktop_lyric")) { /* toggle */ }
            ActionButton(Icons.Default.Timer, t("sleep_timer")) { /* timer */ }
            ActionButton(Icons.Default.Settings, t("play_settings")) { showSettings = true }
        }

        Spacer(Modifier.height(4.dp))

        // Progress + controls
        Column(Modifier.padding(horizontal = 16.dp)) {
            SeekableBar(
                progress = progress.progress,
                onSeek = { viewModel.seekTo((it * progress.maxPlayTime).toLong()) },
                modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(progress.nowPlayTimeStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(progress.maxPlayTimeStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.playPrev() }, Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Prev", Modifier.size(32.dp))
                }
                Spacer(Modifier.width(20.dp))
                IconButton(onClick = { viewModel.togglePlay() }, Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(20.dp))
                IconButton(onClick = { viewModel.playNext() }, Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", Modifier.size(32.dp))
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    // Settings bottom sheet
    if (showSettings) {
        PlaySettingsSheet(
            volume = volume,
            playbackRate = playbackRate,
            lyricFontSize = lyricFontSize,
            transFontSize = transFontSize,
            lyricAlign = lyricAlign,
            lyricSeekEnabled = lyricSeekEnabled,
            showWordHighlight = showWordHighlight,
            showDuetLyric = showDuetLyric,
            onVolumeChange = { viewModel.setVolume(it) },
            onPlaybackRateChange = { viewModel.setPlaybackRate(it) },
            onFontSizeChange = { viewModel.setLyricFontSize(it.roundToInt()) },
            onTransFontSizeChange = { viewModel.setTransFontSize(it.roundToInt()) },
            onAlignChange = { viewModel.setLyricAlign(it) },
            onLyricSeekChange = { viewModel.setLyricSeekEnabled(it) },
            onWordHighlightChange = { viewModel.setShowWordHighlight(it) },
            onDuetLyricChange = { viewModel.setShowDuetLyric(it) },
            onDismiss = { showSettings = false }
        )
    }
    } // ThemeBackground
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.size(44.dp)) {
        Icon(icon, label, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionButton(painter: androidx.compose.ui.graphics.painter.Painter, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.size(44.dp)) {
        Icon(painter = painter, contentDescription = label, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlbumArtBox(picUrl: String?, albumName: String?, modifier: Modifier) {
    if (picUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(picUrl).crossfade(true).build(),
            contentDescription = albumName ?: "Album Art",
            modifier = modifier.clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("🎵", style = MaterialTheme.typography.displayLarge)
        }
    }
}

@Composable
private fun SeekableBar(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier) {
    var barWidth by remember { mutableStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(progress) }
    val dp = if (isDragging) dragProgress else progress
    Box(modifier.fillMaxWidth().onSizeChanged { barWidth = it.width.toFloat() }
        .pointerInput(Unit) { detectTapGestures { onSeek((it.x / barWidth).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) { detectHorizontalDragGestures(
            onDragStart = { offset -> isDragging = true; dragProgress = (offset.x / barWidth).coerceIn(0f, 1f) },
            onDragEnd = { isDragging = false; onSeek(dragProgress) },
            onDragCancel = { isDragging = false },
            onHorizontalDrag = { _, a -> dragProgress = (dragProgress + a / barWidth).coerceIn(0f, 1f) }
        )},
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant))
        Box(Modifier.fillMaxWidth(dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Box(Modifier.fillMaxWidth(dp).height(4.dp), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayDetailHeader(songName: String, singer: String, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(songName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(singer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaySettingsSheet(
    volume: Float, playbackRate: Float,
    lyricFontSize: Int, transFontSize: Int,
    lyricAlign: String, lyricSeekEnabled: Boolean,
    showWordHighlight: Boolean, showDuetLyric: Boolean,
    onVolumeChange: (Float) -> Unit, onPlaybackRateChange: (Float) -> Unit,
    onFontSizeChange: (Float) -> Unit, onTransFontSizeChange: (Float) -> Unit,
    onAlignChange: (String) -> Unit,
    onLyricSeekChange: (Boolean) -> Unit,
    onWordHighlightChange: (Boolean) -> Unit,
    onDuetLyricChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(t("play_settings"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            SettingsSwitchItem(t("lyric_seek"), t("lyric_seek_desc"), checked = lyricSeekEnabled, onCheckedChange = onLyricSeekChange)
            SettingsSwitchItem(t("word_highlight"), t("word_highlight_desc"), checked = showWordHighlight, onCheckedChange = onWordHighlightChange)
            SettingsSwitchItem(t("duet_lyric"), t("duet_lyric_desc"), checked = showDuetLyric, onCheckedChange = onDuetLyricChange)
            SettingsSliderItem(t("lyric_font_size"), value = lyricFontSize.toFloat(), range = 10f..24f, onValueChange = onFontSizeChange)
            SettingsSliderItem(t("trans_font_size"), value = transFontSize.toFloat(), range = 8f..20f, onValueChange = onTransFontSizeChange)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("alignment"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("left", "center", "right").forEach { a ->
                        FilterChip(selected = a == lyricAlign, onClick = { onAlignChange(a) }, label = { Text(t(a)) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsSliderItem(t("volume"), value = volume, range = 0f..1f, onValueChange = onVolumeChange)
            SettingsSliderItem(t("playback_rate"), value = playbackRate, range = 0.5f..2f, onValueChange = onPlaybackRateChange)
        }
    }
}
