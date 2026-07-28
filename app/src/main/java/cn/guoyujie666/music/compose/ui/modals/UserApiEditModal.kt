package cn.guoyujie666.music.compose.ui.modals

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.guoyujie666.music.compose.ui.i18n.t

@Composable
fun UserApiSourceManager(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val vm: UserApiViewModel = hiltViewModel()
    val apiList by vm.apiList.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val initResults by vm.initResults.collectAsState()
    var showImportUrlDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val script = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                if (script.isNotBlank()) vm.importScript(script)
            } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理自定义音源", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                // Info tip
                Text("自定义音源脚本可从以下地址获取：", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("https://lyswhut.github.io/lx-music-doc/mobile/custom-source",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                // API list
                if (apiList.isEmpty()) {
                    Text("暂无自定义音源，点击下方按钮导入", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(apiList.toList()) { api ->
                            val enabled = activeId == api.id
                            val status = initResults[api.id]
                            Surface(
                                onClick = { vm.selectSource(api.id) },
                                color = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = enabled,
                                        onClick = { vm.selectSource(api.id) }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("${api.name} v${api.version}", style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium)
                                        if (api.author.isNotEmpty()) Text(api.author, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (status != null) {
                                            Text(
                                                if (status == "success") "初始化成功" else "初始化失败",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (status == "success") Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                            )
                                        } else if (enabled) {
                                            Text("初始化中...", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = { vm.removeApi(api.id) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Close, "删除", Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showImportUrlDialog = true }) {
                    Icon(Icons.Filled.Language, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("从URL导入")
                }
                OutlinedButton(onClick = { filePicker.launch(arrayOf("application/javascript", "text/javascript", "*/*")) }) {
                    Icon(Icons.Filled.FileOpen, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("从文件导入")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("close")) }
        }
    )

    // Import from URL dialog
    if (showImportUrlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportUrlDialog = false },
            title = { Text("从URL导入脚本") },
            text = {
                OutlinedTextField(value = url, onValueChange = { url = it },
                    placeholder = { Text("https://...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (url.startsWith("http")) {
                        vm.importFromUrl(url)
                        showImportUrlDialog = false
                    }
                }, enabled = url.startsWith("http")) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showImportUrlDialog = false }) { Text(t("cancel")) } }
        )
    }
}
