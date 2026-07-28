@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.guoyujie666.music.compose.ui.home.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import cn.guoyujie666.music.compose.ui.home.viewmodel.PlayerViewModel
import cn.guoyujie666.music.compose.ui.home.viewmodel.SettingsViewModel
import cn.guoyujie666.music.compose.ui.home.viewmodel.UserListViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.songlist.UserSongList
import cn.guoyujie666.music.compose.ui.common.AddToPlaylistDialog
import cn.guoyujie666.music.compose.ui.common.MusicListItem
import cn.guoyujie666.music.compose.ui.common.MusicToggleDialog
import cn.guoyujie666.music.compose.ui.i18n.t

// ─── list data ────────────────────────────────────────────────────

private data class ListTabData(val id: String, val name: String)

private val builtinLists = listOf(
    ListTabData("default", "试听列表"),
    ListTabData("love", "我的收藏"),
)

/** Ported from RN's sortListMusicInfo — locale-aware sort with random shuffle. */
private fun sortList(
    list: List<MusicInfo>,
    field: String,
    sortType: String,
    locale: java.util.Locale = java.util.Locale.getDefault()
): List<MusicInfo> {
    val data = list.toMutableList()
    if (sortType == "random") {
        data.shuffle()
        return data
    }
    val parseTime: (MusicInfo) -> Long = { s ->
        s.interval?.split(":")?.foldRightIndexed(0L) { i, part, acc ->
            acc + (part.toLongOrNull() ?: 0) * (if (i == 0) 1 else 60)
        } ?: 0L
    }
    val collator = java.text.Collator.getInstance(locale)
    val asc = sortType == "up"
    when (field) {
        "time" -> {
            data.sortWith { a, b ->
                val ta = parseTime(a); val tb = parseTime(b)
                when { ta == 0L && tb == 0L -> 0; ta == 0L -> if (asc) -1 else 1; tb == 0L -> if (asc) 1 else -1
                else -> if (asc) ta.compareTo(tb) else tb.compareTo(ta) }
            }
        }
        "name", "singer", "source" -> {
            val getter: (MusicInfo) -> String = when (field) {
                "name" -> { s -> s.name }
                "singer" -> { s -> s.singer }
                else -> { s -> s.source }
            }
            data.sortWith { a, b ->
                val va = getter(a); val vb = getter(b)
                if (asc) collator.compare(va, vb) else collator.compare(vb, va)
            }
        }
        "album" -> {
            val getter: (MusicInfo) -> String = { s -> s.meta.albumName }
            data.sortWith { a, b ->
                val va = getter(a); val vb = getter(b)
                if (asc) collator.compare(va, vb) else collator.compare(vb, va)
            }
        }
    }
    return data
}

@Composable
fun MyListTab(modifier: Modifier = Modifier) {
    val playerVM: PlayerViewModel = hiltViewModel()
    val userListVM: UserListViewModel = hiltViewModel()
    val settingsVM: SettingsViewModel = hiltViewModel()
    val settings by settingsVM.settings.collectAsState()
    val currentMusic by playerVM.currentMusicInfo.collectAsState()
    val currentMusicId = currentMusic?.musicInfo?.id ?: ""
    val currentMusicListId = currentMusic?.listId ?: ""
    val userLists by userListVM.userLists.collectAsState()
    val syncingIds by userListVM.syncingIds.collectAsState()
    val _songsVersion by userListVM.songsVersion.collectAsState()

    var activeListId by rememberSaveable { mutableStateOf("default") }
    var isMultiSelect by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf("single") }
    var rangeStartIdx by remember { mutableStateOf(-1) }
    val dropdownExpanded = remember { mutableStateOf(false) }

    // Dialog states
    var showAddToDialog by remember { mutableStateOf(false) }
    var showMoveToDialog by remember { mutableStateOf(false) }
    var showSongDetailDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showListMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var isReorderMode by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showToggleSourceDialog by remember { mutableStateOf(false) }
    var showMultiAddDialog by remember { mutableStateOf(false) }
    var showMultiMoveDialog by remember { mutableStateOf(false) }
    var showMultiMenu by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var pendingSong by remember { mutableStateOf<MusicInfo?>(null) }

    // allLists = builtin ("default", "love") + user lists from SongListManager
    val allLists = builtinLists + userLists.map { ListTabData(it.id, it.name) }
    val activeList = allLists.find { it.id == activeListId } ?: allLists[0]

    // Load songs for current list reactively (songsVersion triggers reload on changes)
    var refreshTick by remember { mutableStateOf(0) }
    val allSongs = remember(activeListId, _songsVersion, userLists, refreshTick) {
        when (activeListId) {
            "default" -> playerVM.getDefaultList()
            "love" -> userListVM.getSongs("love")
            else -> userListVM.getSongs(activeListId)
        }
    }
    val songs = if (searchQuery.isBlank()) allSongs
        else allSongs.filter { it.name.contains(searchQuery, ignoreCase = true) || it.singer.contains(searchQuery, ignoreCase = true) }

    fun isSelected(id: String) = id in selectedIds
    fun toggleSelect(id: String) { selectedIds = if (isSelected(id)) selectedIds - id else selectedIds + id }
    fun selectRange(fromIdx: Int, toIdx: Int) {
        val (lo, hi) = minOf(fromIdx, toIdx) to maxOf(fromIdx, toIdx)
        selectedIds = selectedIds + songs.slice(lo..hi).map { it.id }.toSet()
    }

    fun handleSongTap(idx: Int, song: MusicInfo) {
        if (!isMultiSelect) {
            playerVM.playInList(activeListId, allSongs, idx)
            return
        }
        when (selectMode) {
            "range" -> {
                if (rangeStartIdx < 0) { rangeStartIdx = idx; selectedIds = setOf(song.id) }
                else { selectRange(rangeStartIdx, idx); rangeStartIdx = -1 }
            }
            else -> toggleSelect(song.id)
        }
    }

    fun deleteSongs(ids: Set<String>) {
        if (activeListId == "default") {
            playerVM.removeFromDefaultList(ids)
        } else {
            userListVM.removeSongs(activeListId, ids)
        }
        // If deleting the currently playing song, skip to next
        if (currentMusicId.isNotEmpty() && currentMusicId in ids) {
            val remaining = if (activeListId == "default") playerVM.getDefaultList()
                else userListVM.getSongs(activeListId)
            if (remaining.isEmpty()) {
                playerVM.stop()
            } else {
                playerVM.playNext()
            }
        }
        selectedIds = emptySet()
        refreshTick++
    }

    val context = LocalContext.current
    // Pre-resolve toast strings (t() is @Composable)
    val importOk = t("setting_import_success")
    val importFail = t("setting_import_failed")
    val exportOk = t("setting_export_success")
    val exportFail = t("setting_export_failed")

    // Import/export launchers for single lists
    val importListLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = userListVM.backupManager.importAll(uri)
        if (result != null) {
            val songs = result.second?.defaultListSongs
                ?: result.second?.loveListSongs
                ?: result.second?.importedUserLists?.firstOrNull()?.second
            if (songs != null) {
                val currentSongs = userListVM.getSongs(activeListId)
                val existingIds = currentSongs.map { it.id }.toSet()
                val toAdd = songs.filter { it.id !in existingIds }
                if (toAdd.isNotEmpty()) {
                    userListVM.setSongs(activeListId, currentSongs + toAdd)
                }
            }
            refreshTick++
            Toast.makeText(context, importOk, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, importFail, Toast.LENGTH_SHORT).show()
        }
    }

    val exportListLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = userListVM.backupManager.exportSingleList(uri, activeListId, activeList.name, allSongs)
        Toast.makeText(context, if (ok) exportOk else exportFail, Toast.LENGTH_SHORT).show()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── Title ──────────────────────────────────────────
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(t("tab_library"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                // Three-dot menu
                Box {
                    IconButton(onClick = { showListMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showListMenu, onDismissRequest = { showListMenu = false }) {
                        val isUserList = activeListId !in setOf("default", "love")
                        DropdownMenuItem(text = { Text(t("list_rename")) }, onClick = {
                            showListMenu = false
                            renameText = activeList.name
                            showRenameDialog = true
                        }, enabled = isUserList)
                        DropdownMenuItem(text = { Text(t("list_sort")) }, onClick = {
                            showListMenu = false; showSortDialog = true
                        })
                        DropdownMenuItem(text = { Text(t("list_duplicate")) }, onClick = {
                            showListMenu = false; showDuplicateDialog = true
                        })
                        // Sync: only for lists collected from online songlist/leaderboard
                        val hasSource = activeListId.startsWith("user_") &&
                            userLists.find { it.id == activeListId }?.source != null
                        val syncTip = t("list_update_tip", "name" to activeList.name)
                        if (hasSource) {
                            val isSyncing = activeListId in syncingIds
                            DropdownMenuItem(text = { Text(t("list_sync")) }, onClick = {
                                showListMenu = false
                                val list = userLists.find { it.id == activeListId } ?: return@DropdownMenuItem
                                Toast.makeText(context, syncTip, Toast.LENGTH_SHORT).show()
                                userListVM.syncList(list)
                            }, enabled = !isSyncing)
                        }
                        DropdownMenuItem(text = { Text(t("list_import")) }, onClick = {
                            showListMenu = false; importListLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                        })
                        DropdownMenuItem(text = { Text(t("list_export")) }, onClick = {
                            showListMenu = false
                            exportListLauncher.launch("lx_list_part_${activeList.name}.lxmc")
                        })
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        DropdownMenuItem(text = {
                            Text(t("list_remove"), color = MaterialTheme.colorScheme.error)
                        }, onClick = {
                            showListMenu = false; showRemoveDialog = true
                        }, enabled = isUserList)
                    }
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, "New playlist", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // ── dropdown + add button ──────────────────────────
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded.value,
                    onExpandedChange = { dropdownExpanded.value = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = activeList.name, onValueChange = {}, readOnly = true,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (activeListId in syncingIds) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded.value)
                            }
                        },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    ExposedDropdownMenu(expanded = dropdownExpanded.value,
                        onDismissRequest = { dropdownExpanded.value = false }) {
                        allLists.forEach { list ->
                            DropdownMenuItem(text = {
                                Text(list.name,
                                    fontWeight = if (list.id == activeListId) FontWeight.Bold else FontWeight.Normal,
                                    color = if (list.id == activeListId) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                            }, onClick = {
                                activeListId = list.id; dropdownExpanded.value = false
                                if (isMultiSelect) { isMultiSelect = false; selectedIds = emptySet(); rangeStartIdx = -1 }
                            })
                        }
                    }
                }
            }

            // ── selection bar / search bar / action row ────────
            if (isMultiSelect) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = { selectMode = "single"; rangeStartIdx = -1 },
                        shape = RoundedCornerShape(6.dp),
                        color = if (selectMode == "single") MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(t("single"), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(4.dp))
                    Surface(onClick = { selectMode = "range"; rangeStartIdx = -1 },
                        shape = RoundedCornerShape(6.dp),
                        color = if (selectMode == "range") MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(t("range"), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { selectedIds = songs.map { it.id }.toSet() - selectedIds; rangeStartIdx = -1 },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(t("invert"), style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { selectedIds = emptySet(); isMultiSelect = false; rangeStartIdx = -1 },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(t("cancel"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (isSearching) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                        singleLine = true, placeholder = { Text(t("search_in_list")) },
                        modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { searchQuery = ""; isSearching = false }) { Text(t("done")) }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(t("songs_count", "n" to "${songs.size}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Surface(onClick = { isSearching = true }, shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(2.dp))
                            Text(t("search_in_list"), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Surface(onClick = { isMultiSelect = true }, shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SelectAll, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(2.dp))
                            Text(t("select"), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    // Reorder toggle — enters inline drag-to-reorder mode
                    Surface(onClick = { isReorderMode = !isReorderMode },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isReorderMode) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Menu, null, Modifier.size(16.dp),
                                tint = if (isReorderMode) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(2.dp))
                            Text(if (isReorderMode) t("done") else t("change_position"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isReorderMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(top = 4.dp))

            // ── Song list ──────────────────────────────────────
            val listState = rememberLazyListState()
            LaunchedEffect(activeListId) {
                listState.scrollToItem(0)
            }
            // Inline drag-to-reorder state (Material 2 style: drag handle + long-press + smooth reorder)
            val reorderSongs = remember { mutableStateListOf<MusicInfo>() }
            LaunchedEffect(isReorderMode) {
                if (isReorderMode) {
                    reorderSongs.clear(); reorderSongs.addAll(allSongs)
                } else if (reorderSongs.isNotEmpty() && reorderSongs.toList() != allSongs) {
                    if (activeListId != "default") {
                        userListVM.setSongs(activeListId, reorderSongs.toList())
                    } else {
                        playerVM.sortDefaultList(reorderSongs.toList())
                    }
                    refreshTick++
                }
            }
            var draggedSongId by remember { mutableStateOf<String?>(null) }
            var draggedOffset by remember { mutableFloatStateOf(0f) }
            var draggedOrigIdx by remember { mutableIntStateOf(-1) }
            val itemHPxVal = with(LocalDensity.current) { 56.dp.toPx() }

            // Calculate virtual target index from drag offset
            fun targetIdx(): Int = (draggedOrigIdx + (draggedOffset / itemHPxVal).roundToInt())
                .coerceIn(0, reorderSongs.lastIndex)

            LazyColumn(Modifier.fillMaxSize().weight(1f),
                state = listState,
                contentPadding = PaddingValues(bottom = 80.dp),
                userScrollEnabled = draggedSongId == null
            ) {
                itemsIndexed(if (isReorderMode) reorderSongs else songs, key = { _, s -> s.id }) { idx, song ->
                    if (isReorderMode) {
                        val isDragged = draggedSongId == song.id
                        // Visual shift for items between orig position and target position
                        val shiftY = if (!isDragged && draggedSongId != null) {
                            val t = targetIdx()
                            when {
                                t > draggedOrigIdx && idx in (draggedOrigIdx + 1)..t -> -itemHPxVal
                                t < draggedOrigIdx && idx in t until draggedOrigIdx -> +itemHPxVal
                                else -> 0f
                            }
                        } else 0f
                        Row(Modifier.fillMaxWidth()
                            .graphicsLayer {
                                translationY = if (isDragged) draggedOffset else shiftY
                                scaleX = if (isDragged) 1.03f else 1f; scaleY = if (isDragged) 1.03f else 1f
                                shadowElevation = if (isDragged) 8f else 0f
                            }
                            .zIndex(if (isDragged) 1f else 0f)
                            .background(MaterialTheme.colorScheme.surface),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                MusicListItem(
                                    musicInfo = song, index = idx,
                                    showAlbumName = settings.listIsShowAlbumName,
                                    showSource = settings.listIsShowSource,
                                    showInterval = settings.listIsShowInterval,
                                    showQuality = false, showAction = false,
                                    isPlaying = currentMusicId == song.id && currentMusicListId == activeListId,
                                )
                            }
                            // Drag handle on the right
                            Icon(Icons.Filled.Menu, "Drag",
                                Modifier.size(28.dp).padding(end = 8.dp).pointerInput(song.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { draggedSongId = song.id; draggedOrigIdx = idx; draggedOffset = 0f },
                                        onDragEnd = {
                                            val t = targetIdx()
                                            val orig = reorderSongs.indexOfFirst { it.id == song.id }
                                            if (t != orig && orig >= 0) reorderSongs.add(t, reorderSongs.removeAt(orig))
                                            draggedSongId = null; draggedOrigIdx = -1; draggedOffset = 0f
                                        },
                                        onDragCancel = { draggedSongId = null; draggedOrigIdx = -1; draggedOffset = 0f },
                                        onDrag = { _, a ->
                                            draggedOffset = (draggedOffset + a.y)
                                                .coerceIn(-itemHPxVal * reorderSongs.size, itemHPxVal * reorderSongs.size)
                                        }
                                    )
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        // Normal item
                        val checked = isSelected(song.id)
                        val origIdx = allSongs.indexOfFirst { it.id == song.id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isMultiSelect) {
                                Checkbox(checked = checked, onCheckedChange = { handleSongTap(origIdx, song) },
                                    modifier = Modifier.padding(start = 8.dp))
                                if (selectMode == "range" && rangeStartIdx == origIdx) {
                                    Text("←", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                MusicListItem(
                                    musicInfo = song, index = origIdx,
                                    showAlbumName = settings.listIsShowAlbumName,
                                    showSource = settings.listIsShowSource,
                                    showInterval = settings.listIsShowInterval,
                                    showQuality = false,
                                    showAction = !isMultiSelect,
                                    isPlaying = currentMusicId == song.id && currentMusicListId == activeListId,
                                    onPlay = { handleSongTap(idx, song) },
                                    onAddTo = if (!isMultiSelect) ({ pendingSong = it; showAddToDialog = true }) else null,
                                    onMoveTo = if (!isMultiSelect) ({ pendingSong = it; showMoveToDialog = true }) else null,
                                    onToggleSource = if (!isMultiSelect) ({
                                        pendingSong = it; showToggleSourceDialog = true
                                    }) else null,
                                    onDelete = if (!isMultiSelect) ({ deleteSongs(setOf(it.id)) }) else null,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Multi-select bottom bar ───────────────────────────
        AnimatedVisibility(
            visible = isMultiSelect, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("selected_count", "n" to "${selectedIds.size}"),
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { if (selectedIds.isNotEmpty()) showMultiMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, "Actions", Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showMultiMenu, onDismissRequest = { showMultiMenu = false }) {
                            DropdownMenuItem(text = { Text(t("add_to_playlist")) }, onClick = {
                                showMultiMenu = false; showMultiAddDialog = true
                            })
                            DropdownMenuItem(text = { Text(t("move_to_playlist")) }, onClick = {
                                showMultiMenu = false; showMultiMoveDialog = true
                            })
                            DropdownMenuItem(text = { Text(t("delete")) }, onClick = {
                                showMultiMenu = false; deleteSongs(selectedIds)
                            })
                        }
                    }
                    IconButton(onClick = { selectedIds = emptySet(); isMultiSelect = false; rangeStartIdx = -1 }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Done", Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // ── Add to playlist dialog (shared component) ──────────────
    if (showAddToDialog && pendingSong != null) {
        AddToPlaylistDialog(
            song = pendingSong!!,
            userLists = userLists,
            songCounts = mapOf(
                "default" to playerVM.getDefaultList().size,
                "love" to userListVM.getSongCount("love")
            ) + userLists.associate { it.id to userListVM.getSongCount(it.id) },
            onDismiss = { showAddToDialog = false; pendingSong = null },
            onAddToList = { listId ->
                val song = pendingSong!!
                if (listId == "default") {
                    playerVM.addToTempPlayList(song)
                } else {
                    userListVM.addSong(listId, song)
                }
                showAddToDialog = false; pendingSong = null
            },
            onCreateList = { name -> userListVM.createList(name) },
            excludeListId = activeListId
        )
    }

    // ── Move to playlist dialog ───────────────────────────────
    if (showMoveToDialog && pendingSong != null) {
        val moveSong = pendingSong!!
        val moveCounts = mapOf(
            "default" to playerVM.getDefaultList().size,
            "love" to userListVM.getSongCount("love")
        ) + userLists.associate { it.id to userListVM.getSongCount(it.id) }
        AlertDialog(
            onDismissRequest = { showMoveToDialog = false },
            title = { Text(t("move_to_playlist")) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allLists) { list ->
                        if (list.id != activeListId) {
                            val exists = userListVM.containsSong(list.id, moveSong.id)
                            val cnt = moveCounts[list.id] ?: 0
                            FilledTonalButton(
                                onClick = {
                                    if (list.id == "default") {
                                        playerVM.addToTempPlayList(moveSong)
                                    } else if (activeListId == "default") {
                                        userListVM.addSong(list.id, moveSong)
                                    } else {
                                        userListVM.moveSong(activeListId, list.id, moveSong)
                                    }
                                    showMoveToDialog = false; pendingSong = null
                                },
                                enabled = !exists,
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Text("${list.name} ($cnt)",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (exists) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    item {
                        FilledTonalButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("new_playlist"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMoveToDialog = false }) { Text(t("cancel")) } }
        )
    }

    // ── Multi-select add to playlist dialog ────────────────────
    if (showMultiAddDialog) {
        val selectedSongs = allSongs.filter { it.id in selectedIds }
        val trialName = t("trial_list")
        val favName = t("my_favorites")
        val newListText = t("new_playlist")
        val counts = mapOf(
            "default" to playerVM.getDefaultList().size,
            "love" to userListVM.getSongCount("love")
        ) + userLists.associate { it.id to userListVM.getSongCount(it.id) }
        AlertDialog(
            onDismissRequest = { showMultiAddDialog = false },
            title = { Text(t("add_to_playlist")) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val defaultLists = listOf(
                        cn.guoyujie666.music.compose.core.songlist.UserSongList("default", trialName),
                        cn.guoyujie666.music.compose.core.songlist.UserSongList("love", favName)
                    ).filter { it.id != activeListId }
                    items(defaultLists + userLists.filter { it.id != activeListId }) { list ->
                        val cnt = counts[list.id] ?: 0
                        FilledTonalButton(
                            onClick = {
                                selectedSongs.forEach { song ->
                                    when (list.id) {
                                        "default" -> playerVM.addToTempPlayList(song)
                                        "love" -> userListVM.addSong("love", song)
                                        else -> userListVM.addSong(list.id, song)
                                    }
                                }
                                selectedIds = emptySet(); isMultiSelect = false; rangeStartIdx = -1
                                showMultiAddDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Text("${list.name} ($cnt)",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item {
                        FilledTonalButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(newListText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMultiAddDialog = false }) { Text(t("cancel")) } }
        )
    }

    // ── Multi-select move to playlist dialog ───────────────────
    if (showMultiMoveDialog) {
        val selectedSongs = allSongs.filter { it.id in selectedIds }
        val counts = mapOf(
            "default" to playerVM.getDefaultList().size,
            "love" to userListVM.getSongCount("love")
        ) + userLists.associate { it.id to userListVM.getSongCount(it.id) }
        AlertDialog(
            onDismissRequest = { showMultiMoveDialog = false },
            title = { Text(t("move_to_playlist")) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allLists.filter { it.id != activeListId }) { list ->
                                        val cnt = counts[list.id] ?: 0
                        FilledTonalButton(
                            onClick = {
                                selectedSongs.forEach { song ->
                                    when (list.id) {
                                        "default" -> {
                                            playerVM.addToTempPlayList(song)
                                            if (activeListId != "default") userListVM.removeSong(activeListId, song.id)
                                        }
                                        else -> {
                                            if (activeListId == "default") {
                                                if (!userListVM.containsSong(list.id, song.id))
                                                    userListVM.addSong(list.id, song)
                                            } else {
                                                userListVM.moveSong(activeListId, list.id, song)
                                            }
                                        }
                                    }
                                }
                                selectedIds = emptySet(); isMultiSelect = false; rangeStartIdx = -1
                                refreshTick++
                                showMultiMoveDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Text("${list.name} ($cnt)",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMultiMoveDialog = false }) { Text(t("cancel")) } }
        )
    }

    // ── Song detail dialog ─────────────────────────────────────
    if (showSongDetailDialog && pendingSong != null) {
        val s = pendingSong!!
        AlertDialog(
            onDismissRequest = { showSongDetailDialog = false },
            title = { Text(s.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text("${t("artist")}: ${s.singer}", style = MaterialTheme.typography.bodyMedium)
                    Text("${t("source")}: ${s.source}", style = MaterialTheme.typography.bodyMedium)
                    Text("${t("duration")}: ${s.interval ?: "--:--"}", style = MaterialTheme.typography.bodyMedium)
                    if (s.meta.albumName.isNotEmpty())
                        Text("${t("album")}: ${s.meta.albumName}", style = MaterialTheme.typography.bodyMedium)
                    Text("ID: ${s.id}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showSongDetailDialog = false }) { Text(t("close")) } }
        )
    }

    // ── Rename dialog ─────────────────────────────────────────
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(t("list_rename")) },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    singleLine = true, placeholder = { Text(activeList.name) })
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    if (newName.isNotEmpty() && newName != activeList.name) {
                        userListVM.renameList(activeListId, newName)
                    }
                    showRenameDialog = false
                }) { Text(t("confirm")) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text(t("cancel")) } }
        )
    }

    // ── Remove list confirmation dialog ────────────────────────
    if (showRemoveDialog && activeListId !in setOf("default", "love")) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(t("list_remove")) },
            text = { Text(t("list_remove_tip", "name" to activeList.name)) },
            confirmButton = {
                TextButton(onClick = {
                    userListVM.deleteList(activeListId)
                    activeListId = "default"
                    showRemoveDialog = false
                }) { Text(t("list_remove_tip_button"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRemoveDialog = false }) { Text(t("cancel")) } }
        )
    }

    // ── Sort dialog ────────────────────────────────────────────
    if (showSortDialog) {
        val sortFields = listOf("name", "singer", "album", "time", "source")
        val sortTypes = listOf("up", "down", "random")
        var sortField by remember { mutableStateOf("name") }
        var sortType by remember { mutableStateOf("up") }
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(t("list_sort")) },
            text = {
                Column {
                    Text(t("list_sort_by_field"), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    sortFields.forEach { f ->
                        Row(Modifier.fillMaxWidth().clickable { if (sortType != "random") sortField = f }
                            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sortField == f, onClick = { if (sortType != "random") sortField = f },
                                enabled = sortType != "random")
                            Spacer(Modifier.width(8.dp))
                            Text(t("list_sort_by_$f"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(t("list_sort_by_type"), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    sortTypes.forEach { ty ->
                        Row(Modifier.fillMaxWidth().clickable { sortType = ty }
                            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sortType == ty, onClick = { sortType = ty })
                            Spacer(Modifier.width(8.dp))
                            Text(t("list_sort_$ty"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sorted = sortList(allSongs, sortField, sortType)
                    if (activeListId == "default") {
                        playerVM.sortDefaultList(sorted)
                    } else {
                        userListVM.setSongs(activeListId, sorted)
                    }
                    refreshTick++
                    showSortDialog = false
                }) { Text(t("confirm")) }
            },
            dismissButton = { TextButton(onClick = { showSortDialog = false }) { Text(t("cancel")) } }
        )
    }

    // ── Duplicate music dialog ──────────────────────────────────
    if (showDuplicateDialog) {
        val variantRxp = remember { Regex("""(\(|（).+(\)|）)""") }
        val variantRxp2 = remember { Regex("""\s|'|\.|,|，|&|"|、|\(|\)|（|）|`|~|-|<|>|\||/|]|\[""") }
        // Match RN's filterDuplicateMusic: normalize name, group by result
        data class DupItem(val index: Int, val song: MusicInfo, val group: String)
        val dupGroups = remember(allSongs) {
            val items = allSongs.mapIndexed { idx, s ->
                val normalized = s.name.lowercase().replace(variantRxp, "").replace(variantRxp2, "")
                DupItem(idx, s, normalized.ifEmpty { s.name.lowercase().replace(Regex("""\s+"""), "") })
            }
            items.groupBy { it.group }.filter { it.value.size > 1 }
                .entries.sortedBy { it.key }
        }
        var selectedDups by remember(showDuplicateDialog) { mutableStateOf(setOf<String>()) }
        var playingDupId by remember { mutableStateOf("") }
        LaunchedEffect(showDuplicateDialog) { playingDupId = "" }

        Dialog(onDismissRequest = { showDuplicateDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f)) {
                Column(Modifier.padding(16.dp)) {
                    // Title
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("list_duplicate"), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f))
                        val allIds = dupGroups.flatMap { g -> g.value.map { it.song.id } }.toSet()
                        val allSelected = allIds.isNotEmpty() && selectedDups.size >= allIds.size
                        TextButton(onClick = {
                            selectedDups = if (allSelected) emptySet() else allIds
                        }) { Text(if (allSelected) t("deselect_all") else t("select_all")) }
                        IconButton(onClick = { showDuplicateDialog = false }) {
                            Icon(Icons.Default.Check, "Close")
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (dupGroups.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(t("list_duplicate_none"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            dupGroups.forEachIndexed { gi, (groupName, items) ->
                                item(key = "hdr_${gi}_$groupName") {
                                    Text("${gi + 1}.  ${items[0].song.name}  (${items.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                                }
                                items(items.size, key = { subIdx -> items[subIdx].song.id }) { subIdx ->
                                    val (idx, song, _) = items[subIdx]
                                    val isChecked = song.id in selectedDups
                                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = isChecked, onCheckedChange = {
                                            selectedDups = if (isChecked) selectedDups - song.id else selectedDups + song.id
                                        })
                                        Column(Modifier.weight(1f)) {
                                            Text(song.name, style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Row {
                                                Text(song.singer, style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (song.meta.albumName.isNotEmpty()) {
                                                    Text(" · ${song.meta.albumName}", style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        Text(song.source, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 4.dp, end = 4.dp))
                                        Text(song.interval ?: "--:--", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(end = 2.dp))
                                        IconButton(onClick = {
                                            playerVM.playInList(activeListId, allSongs, idx)
                                            playingDupId = song.id
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.PlayArrow, "Play",
                                                Modifier.size(16.dp),
                                                tint = if (playingDupId == song.id) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = {
                                            deleteSongs(setOf(song.id))
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, "Remove", Modifier.size(14.dp))
                                        }
                                    }
                                }
                                if (gi < dupGroups.size - 1) {
                                    item(key = "div_$gi") {
                                        HorizontalDivider(Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        // Batch remove selected
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                if (selectedDups.isNotEmpty()) {
                                    deleteSongs(selectedDups)
                                    selectedDups = emptySet()
                                }
                            }, enabled = selectedDups.isNotEmpty()) {
                                Text(t("list_duplicate_remove_selected", "count" to "${selectedDups.size}"))
                            }
                            TextButton(onClick = {
                                // Remove all duplicates (keep first of each group)
                                val allIds = dupGroups.flatMap { g -> g.value.map { it.song.id } }.toSet()
                                deleteSongs(allIds)
                                selectedDups = emptySet()
                            }) {
                                Text(t("list_duplicate_remove_all"), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Create playlist dialog ─────────────────────────────────
    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(t("new_playlist")) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { if (it.length <= 50) newName = it },
                    singleLine = true, placeholder = { Text(t("playlist_name")) })
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) {
                        userListVM.createList(name)
                        showCreateDialog = false; newName = ""
                    }
                }, enabled = newName.isNotBlank()) { Text(t("create")) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(t("cancel")) } }
        )
    }

    // ── Toggle source dialog ──────────────────────────────────────
    if (showToggleSourceDialog && pendingSong != null) {
        val song = pendingSong!!
        val scope = rememberCoroutineScope()
        MusicToggleDialog(
            song = song,
            allSongs = allSongs,
            onDismiss = { showToggleSourceDialog = false; pendingSong = null },
            onReplace = { old: MusicInfo, new: MusicInfo ->
                if (activeListId == "default") {
                    playerVM.replaceInDefaultList(old, new)
                } else {
                    val origIdx = allSongs.indexOfFirst { s -> s.id == old.id }
                    userListVM.toggleSourceInList(
                        listId = activeListId,
                        oldSong = old,
                        newSong = new,
                        oldIndex = origIdx,
                        onDuplicateConfirm = { true }
                    )
                }
                refreshTick++
            },
            searchMusic = { name: String, singer: String, onResult: (List<Pair<String, List<MusicInfo>>>) -> Unit ->
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val results = userListVM.searchForToggleSource(name, singer)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(results)
                    }
                }
            }
        )
    }
}
