package cn.guoyujie666.music.compose.ui.home.tabs

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.guoyujie666.music.compose.ui.home.viewmodel.SongListViewModel
import cn.guoyujie666.music.compose.ui.i18n.t
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.absoluteValue

// ─── per-source layout ─────────────────────────────────────────────

internal data class SlSourceMeta(val id: String, val name: String, val defaultSort: String)
private val sources = listOf(
    SlSourceMeta("kw", "", "hot"),
    SlSourceMeta("kg", "", "5"),
    SlSourceMeta("tx", "", "5"),
    SlSourceMeta("wy", "", "hot"),
    SlSourceMeta("mg", "", "1"),
)

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListTab(
    modifier: Modifier = Modifier,
    onSongListClick: (listId: String, source: String) -> Unit = { _, _ -> },
    viewModel: SongListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val settingsVM: cn.guoyujie666.music.compose.ui.home.viewmodel.SettingsViewModel = hiltViewModel()
    val settings by settingsVM.settings.collectAsState()
    val useAlias = settings.sourceNameType == "alias"
    val srcAliases = mapOf("kw" to "小蜗音乐", "kg" to "小枸音乐", "tx" to "小秋音乐", "wy" to "小芸音乐", "mg" to "小蜜音乐")
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    // Import dialog (ported from src/screens/Home/Views/SongList/HeaderBar/OpenList)
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入歌单") },
            text = {
                OutlinedTextField(
                    value = importText, onValueChange = { importText = it },
                    label = { Text("输入歌单链接或ID") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = importText.trim()
                    if (id.isNotEmpty()) {
                        showImportDialog = false
                        onSongListClick(id, state.selectedSource)
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text("取消") } })
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Header ──────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(t("tab_songlist"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { importText = ""; showImportDialog = true }) { Icon(Icons.Default.Add, contentDescription = "导入歌单") }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // Platform + Tag dropdowns
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Platform dropdown
                var platExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = platExpanded, onExpandedChange = { platExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = if (useAlias) srcAliases[state.selectedSource] ?: state.selectedSource else t("src_${state.selectedSource}"),
                        onValueChange = {}, readOnly = true,
                        label = { Text(t("source")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = platExpanded) },
                        modifier = Modifier.menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = platExpanded, onDismissRequest = { platExpanded = false }) {
                        sources.forEach { src ->
                            DropdownMenuItem(text = { Text(if (useAlias) srcAliases[src.id] ?: src.id else t("src_${src.id}"), fontWeight = if (src.id == state.selectedSource) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { viewModel.selectSource(src.id); platExpanded = false })
                        }
                    }
                }
                // Tag dropdown
                var tagExpanded by remember { mutableStateOf(false) }
                val currentTags = state.tags.flatMap { it.children }
                val selectedTag = currentTags.firstOrNull { it.id == state.selectedTagId }
                ExposedDropdownMenuBox(
                    expanded = tagExpanded, onExpandedChange = { tagExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedTag?.name ?: t("default_tag"),
                        onValueChange = {}, readOnly = true,
                        label = { Text(t("genre")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tagExpanded) },
                        modifier = Modifier.menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = tagExpanded, onDismissRequest = { tagExpanded = false }) {
                        DropdownMenuItem(text = { Text(t("default_tag"), fontWeight = if (state.selectedTagId.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { viewModel.selectTag(""); tagExpanded = false })
                        for (cat in state.tags) {
                            DropdownMenuItem(
                                text = { Text(cat.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                onClick = {}, enabled = false)
                            for (t in cat.children) {
                                DropdownMenuItem(
                                    text = { Text("  ${t.name}", fontWeight = if (t.id == state.selectedTagId) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { viewModel.selectTag(t.id); tagExpanded = false })
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        // ── Grid ────────────────────────────────────────────────────
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.isLoading && state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (!state.isLoading && state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎵", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(if (state.error != null) "Failed to load playlists" else "No playlists found",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.error != null && state.items.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Text("⚠ ${state.error}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                    gridItems(state.items) { sl ->
                        SongListCardExpressive(
                            data = SongListCardData(sl.id, sl.name, sl.pic, sl.playCount, sl.author),
                            onClick = { onSongListClick(sl.id, sl.source) })
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  CARD  (unchanged — AsyncImage when pic present, gradient fallback)
// ═════════════════════════════════════════════════════════════════════

@Composable
fun SongListCardExpressive(
    data: SongListCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!data.pic.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(data.pic).crossfade(true).build(),
                        contentDescription = data.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize()
                        .background(Brush.linearGradient(colors = cardColors(data.id.hashCode()),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(1000f, 1000f))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = 0.7f))
                    }
                }
                Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.55f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(12.dp), tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text(formatCount(data.playCount), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White)
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                Text(data.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (data.author.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(data.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun formatCount(n: Long): String = when {
    n >= 100_000_000L -> "${"%.1f".format(n / 100_000_000.0)}亿"
    n >= 10_000L -> "${"%.1f".format(n / 10_000.0)}万"
    else -> n.toString()
}

private fun cardColors(seed: Int): List<Color> {
    val hues = listOf(210f, 270f, 340f, 30f, 160f, 50f, 190f, 290f)
    val h = hues[seed.absoluteValue % hues.size]
    return listOf(Color.hsl(h, 0.55f, 0.45f), Color.hsl((h + 30) % 360, 0.50f, 0.35f))
}

data class SongListCardData(
    val id: String, val name: String, val pic: String? = null,
    val playCount: Long = 0, val author: String = ""
)
