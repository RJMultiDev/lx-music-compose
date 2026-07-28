package cn.guoyujie666.music.compose.ui.modals

import cn.guoyujie666.music.compose.ui.i18n.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/**
 * Sync mode selection modal.
 *
 * Ported from the SyncModeModal overlay.
 * Allows user to choose how to merge local and remote data when connecting
 * to a sync server. Modes: merge, overwrite, cancel.
 */
@Composable
fun SyncModeModal(
    visible: Boolean,
    syncType: String = "list", // "list" or "dislike"
    serverName: String = "",
    onSelect: (String) -> Unit = {},
    onCancel: () -> Unit = {}
) {
    if (!visible) return

    var selectedMode by remember { mutableStateOf<String?>(null) }

    val listModes = listOf(
        SyncMode("merge_local_remote", "Merge Local → Remote", "Keep both local and remote data, merging together"),
        SyncMode("merge_remote_local", "Merge Remote → Local", "Keep both local and remote data, merging together"),
        SyncMode("overwrite_local_remote", "Overwrite Remote with Local", "Replace remote data with your local data"),
        SyncMode("overwrite_remote_local", "Overwrite Local with Remote", "Replace your local data with remote data"),
        SyncMode("overwrite_local_remote_full", "Full Overwrite Remote", "Completely replace all remote data with local"),
        SyncMode("overwrite_remote_local_full", "Full Overwrite Local", "Completely replace all local data with remote"),
        SyncMode("cancel", t("cancel"), "Do not sync this data type")
    )

    val dislikeModes = listOf(
        SyncMode("merge_local_remote", "Merge Local → Remote", "Keep both, merging together"),
        SyncMode("merge_remote_local", "Merge Remote → Local", "Keep both, merging together"),
        SyncMode("overwrite_local_remote", "Overwrite Remote with Local", "Replace remote data"),
        SyncMode("overwrite_remote_local", "Overwrite Local with Remote", "Replace local data"),
        SyncMode("cancel", t("cancel"), "Do not sync")
    )

    val modes = if (syncType == "dislike") dislikeModes else listModes
    val typeLabel = if (syncType == "dislike") "Dislike List" else "Playlists"

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Select Sync Mode",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (serverName.isNotEmpty()) {
                    Text(
                        text = "Server: $serverName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = "Data type: $typeLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                modes.forEach { mode ->
                    SyncModeItem(
                        mode = mode,
                        isSelected = selectedMode == mode.id,
                        onSelect = { selectedMode = mode.id }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedMode?.let { onSelect(it) }
                },
                enabled = selectedMode != null
            ) {
                Text(t("confirm"))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onSelect("cancel")
                onCancel()
            }) {
                Text(t("cancel"))
            }
        }
    )
}

data class SyncMode(
    val id: String,
    val name: String,
    val description: String
)

@Composable
private fun SyncModeItem(
    mode: SyncMode,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mode.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
