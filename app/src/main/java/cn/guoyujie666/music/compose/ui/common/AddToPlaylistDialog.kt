@file:OptIn(ExperimentalLayoutApi::class)
package cn.guoyujie666.music.compose.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.songlist.UserSongList
import cn.guoyujie666.music.compose.ui.i18n.t

/**
 * Shared "Add to playlist" dialog used by Search, SongList, Leaderboard, and MyList tabs.
 *
 * Shows the user's song lists as fixed-width buttons in a scrollable column,
 * with a "+" button at the bottom to create a new list.
 */
@Composable
fun AddToPlaylistDialog(
    song: MusicInfo,
    userLists: List<UserSongList>,
    songCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onAddToList: (listId: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    excludeListId: String? = null
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                onCreateList(name)
                showCreateDialog = false
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("add_to_playlist")) },
        text = {
            // Built-in lists (exclude current list)
            val defaultLists = listOf(
                UserSongList("default", t("trial_list")),
                UserSongList("love", t("my_favorites"))
            ).filter { it.id != excludeListId }
            // Scrollable list with item borders + scroll indicator
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(defaultLists) { list ->
                    val count = songCounts[list.id] ?: 0
                    PlaylistItemButton(
                        name = list.name, count = count,
                        onClick = { onAddToList(list.id) }
                    )
                }

                items(userLists.filter { it.id != excludeListId }) { list ->
                    val count = songCounts[list.id] ?: 0
                    PlaylistItemButton(
                        name = list.name, count = count,
                        onClick = { onAddToList(list.id) }
                    )
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(t("cancel")) }
        }
    )
}

@Composable
private fun PlaylistItemButton(
    name: String,
    count: Int,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(40.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Text(
            text = "$name ($count)",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("new_playlist")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(t("playlist_name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(t("create")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("cancel")) }
        }
    )
}
