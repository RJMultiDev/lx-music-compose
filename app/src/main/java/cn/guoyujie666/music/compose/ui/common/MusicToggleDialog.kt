package cn.guoyujie666.music.compose.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.ui.i18n.t
import cn.guoyujie666.music.compose.ui.common.globalSourceNameType

/**
 * Dialog for the "切换音源" (toggle source) feature.
 * Searches for a song across all music sources and lets the user pick a replacement.
 *
 * Ported from src/screens/Home/Views/Mylist/MusicList/MusicToggleModal.tsx.
 */
@Composable
fun MusicToggleDialog(
    song: MusicInfo,
    allSongs: List<MusicInfo>,
    onDismiss: () -> Unit,
    onReplace: (oldSong: MusicInfo, newSong: MusicInfo) -> Unit,
    searchMusic: (String, String, (List<Pair<String, List<MusicInfo>>>) -> Unit) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var sourceResults by remember { mutableStateOf<List<Pair<String, List<MusicInfo>>>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedSong by remember { mutableStateOf<MusicInfo?>(null) }
    var isReplacing by remember { mutableStateOf(false) }

    fun reload() {
        loading = true
        error = false
        sourceResults = emptyList()
        selectedTab = 0
        selectedSong = null
        searchMusic(song.name, song.singer) { results ->
            sourceResults = results
            loading = false
            selectedTab = sourceResults.indexOfFirst { it.second.isNotEmpty() }
                .let { if (it < 0) 0 else it }
        }
    }

    LaunchedEffect(song.id) {
        searchMusic(song.name, song.singer) { results ->
            sourceResults = results
            loading = false
            selectedTab = sourceResults.indexOfFirst { it.second.isNotEmpty() }
                .let { if (it < 0) 0 else it }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f)
        ) {
            Column(Modifier.padding(16.dp)) {
                // Title row
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t("toggle_source"),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (error) {
                        IconButton(onClick = { reload() }) {
                            Icon(Icons.Default.Refresh, t("retry"), Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Check, t("close"), Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Original song info
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(song.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${song.singer} · ${song.source} · ${song.interval ?: "--:--"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (error) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t("list_error"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { reload() }) { Text(t("retry")) }
                        }
                    }
                } else if (sourceResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(t("no_item"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    // Tabs — respect source name type setting
                    val tabLabels = sourceResults.map { (src, _) ->
                        val key = if (globalSourceNameType == "real") "src_$src" else "source_alias_$src"
                        val raw = t(key)
                        if (raw == key) src.uppercase() else raw
                    }
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabLabels.forEachIndexed { i, label ->
                            Tab(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i; selectedSong = null },
                                text = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    // Search results for selected tab
                    val results = sourceResults.getOrNull(selectedTab)?.second ?: emptyList()
                    LazyColumn(Modifier.weight(1f)) {
                        items(results, key = { it.id }) { item ->
                            val isSelected = selectedSong?.id == item.id
                            val isDuplicate = allSongs.any { it.id == item.id }
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !isDuplicate) { selectedSong = if (isSelected) null else item }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (isDuplicate) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                else MaterialTheme.colorScheme.onSurface)
                                    Row {
                                        Text(item.singer, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (item.meta.albumName.isNotEmpty()) {
                                            Text(" · ${item.meta.albumName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Text(item.interval ?: "--:--",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 4.dp))
                                IconButton(onClick = {
                                    selectedSong = item
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.PlayArrow, "Preview",
                                        Modifier.size(18.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (!isDuplicate && isSelected) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                ) {
                                    Text("✓ ${t("selected")}", Modifier.padding(4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            if (isDuplicate) {
                                Text(t("music_toggle__duplicate_tip"),
                                    Modifier.padding(horizontal = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                        }
                    }

                    // Bottom: selected song preview + confirm button
                    if (selectedSong != null) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(selectedSong!!.name, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val srcKey = if (globalSourceNameType == "real") "src_${selectedSong!!.source}" else "source_alias_${selectedSong!!.source}"
                                val srcLabel = t(srcKey).let { if (it == srcKey) selectedSong!!.source.uppercase() else it }
                                Text("${selectedSong!!.singer} · $srcLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Button(
                                onClick = {
                                    val sel = selectedSong ?: return@Button
                                    if (!isReplacing) {
                                        isReplacing = true
                                        onReplace(song, sel)
                                        onDismiss()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(t("music_toggle__confirm"))
                            }
                        }
                    }
                }
            }
        }
    }
}
