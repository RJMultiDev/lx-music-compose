package cn.guoyujie666.music.compose.ui.modals

import cn.guoyujie666.music.compose.ui.i18n.t
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.guoyujie666.music.compose.core.model.VersionInfo

/**
 * Version update modal.
 *
 * Ported from the VersionModal overlay.
 * Shows version info, update progress, and action buttons.
 */
@Composable
fun VersionModal(
    visible: Boolean,
    currentVersion: String,
    versionInfo: VersionInfo?,
    updateStatus: String = "idle", // "idle", "checking", "downloading", "downloaded", "error"
    downloadProgress: Float = 0f, // 0.0 - 1.0
    ignoreVersion: String? = null,
    onDismiss: () -> Unit = {},
    onDownload: () -> Unit = {},
    onInstall: () -> Unit = {},
    onIgnore: () -> Unit = {},
    onRecheck: () -> Unit = {}
) {
    if (!visible) return

    val hasUpdate = versionInfo != null && versionInfo.version != currentVersion
    val isLatest = versionInfo != null && versionInfo.version == currentVersion
    val isIgnored = ignoreVersion == versionInfo?.version

    AlertDialog(
        onDismissRequest = {
            if (updateStatus != "downloading") onDismiss()
        },
        title = {
            Text(
                text = "Version Update",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Current version
                Text(
                    text = "Current: v$currentVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // New version
                if (versionInfo != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Latest: v${versionInfo.version}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Status messages
                Spacer(Modifier.height(12.dp))
                when (updateStatus) {
                    "checking" -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Checking for updates...")
                        }
                    }
                    "downloading" -> {
                        Column {
                            Text("Downloading update...")
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    "downloaded" -> {
                        Text(
                            text = "✅ Download complete! Ready to install.",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    "error" -> {
                        Text(
                            text = "❌ Update failed. Please try again.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    "idle" -> {
                        when {
                            isLatest -> Text("✅ You are using the latest version.")
                            isIgnored -> Text("This version has been skipped.")
                            hasUpdate -> {
                                if (versionInfo!!.desc.isNotEmpty()) {
                                    Text(
                                        text = versionInfo.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 10
                                    )
                                }
                            }
                            else -> Text("No update information available.")
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                updateStatus == "downloaded" -> {
                    TextButton(onClick = onInstall) {
                        Text("Install")
                    }
                }
                updateStatus == "downloading" -> {
                    TextButton(onClick = onDismiss) {
                        Text("Background")
                    }
                }
                hasUpdate && updateStatus == "idle" -> {
                    TextButton(onClick = onDownload) {
                        Text("Update")
                    }
                }
                isLatest || isIgnored -> {
                    TextButton(onClick = onRecheck) {
                        Text("Re-check")
                    }
                }
            }
        },
        dismissButton = {
            when {
                updateStatus == "downloading" -> {} // No dismiss during download
                hasUpdate && updateStatus == "idle" -> {
                    TextButton(onClick = onIgnore) {
                        Text("Skip")
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text(t("close"))
                    }
                }
            }
        }
    )
}
