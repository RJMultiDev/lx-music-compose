@file:OptIn(ExperimentalMaterial3Api::class)
package cn.guoyujie666.music.compose.ui.home.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.guoyujie666.music.compose.ui.home.viewmodel.PlayerViewModel
import cn.guoyujie666.music.compose.ui.home.viewmodel.SettingsViewModel
import cn.guoyujie666.music.compose.ui.i18n.t
import cn.guoyujie666.music.compose.ui.modals.UserApiSourceManager
import cn.guoyujie666.music.compose.ui.navigation.Navigator
import cn.guoyujie666.music.compose.ui.navigation.SettingsDetailRoute

private data class SettingsCategory(
    val id: String, val name: String, val desc: String, val icon: ImageVector
)

private val settingsCategoryKeys = listOf(
    "setting_basic" to Icons.Filled.Tune,
    "setting_player" to Icons.Filled.MusicNote,
    "setting_lyric_desktop" to Icons.Filled.Lyrics,
    "setting_search" to Icons.Filled.Search,
    "setting_list" to Icons.Filled.Build,
    "setting_sync" to Icons.Filled.CloudSync,
    "setting_backup" to Icons.Filled.Backup,
    "setting_other" to Icons.Filled.Settings,
    "setting_version" to Icons.Filled.SystemUpdate,
    "setting_about" to Icons.Filled.Info,
)
private val settingCategoryIds = listOf("basic","player","desktopLyric","search","list","sync","backup","other","version","about")

// ═════════════════════════════════════════════════════════════════════

@Composable
fun SettingsTab(
    navigator: Navigator,
    modifier: Modifier = Modifier
) {
    CategoryList(onSelect = { category ->
        navigator.push(SettingsDetailRoute(category))
    })
}

// ═════════════════════════════════════════════════════════════════════
//  LIST
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun CategoryList(onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(t("settings"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(settingsCategoryKeys.size) { idx ->
                val (key, icon) = settingsCategoryKeys[idx]
                Surface(
                    onClick = { onSelect(settingCategoryIds[idx]) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(icon, null, Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(t(key), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  DETAIL
// ═════════════════════════════════════════════════════════════════════

@Composable
fun SettingsCategoryDetail(category: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = hiltViewModel()
    cn.guoyujie666.music.compose.ui.theme.ThemeBackground(modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("setting_${category}"), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            when (category) {
                "basic" -> BasicSettings(vm)
                "player" -> PlayerSettings(vm)
                "desktopLyric" -> DesktopLyricSettings(vm)
                "search" -> SearchSettings(vm)
                "list" -> ListSettings(vm)
                "sync" -> SyncSettings(vm)
                "backup" -> BackupSettings(vm)
                "other" -> OtherSettings(vm)
                "version" -> VersionSettings(vm)
                "about" -> AboutSettings(vm)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    } // ThemeBackground
}
// ── the setting sections ──────────────────────────────────────────

@Composable private fun BasicSettings(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    SettingSection(t("appearance")) {
        ThemeColorGrid(
            selectedId = s.themeId, isLast = false,
            onSelect = {
                vm.themeManager.setTheme(it)
                vm.update { copy(themeId = it, themeLightId = if (it != "black") it else themeLightId) }
            }
        )
        SettingsSwitchItem(t("auto_theme"), t("auto_theme_desc"), checked = s.isAutoTheme,
            onCheckedChange = { vm.update { copy(isAutoTheme = it) } })
        SettingsDropdownItem(t("language"), s.langId ?: "Auto", listOf("Auto", "zh-cn", "zh-tw", "en-us"),
            onSelect = { vm.update { copy(langId = it.takeIf { it != "Auto" }) } })
    }
    SettingSection(t("source")) {
        SettingsSwitchItem(t("use_real_source_name"), t("use_real_source_name_desc"), 
            checked = s.sourceNameType == "real",
            onCheckedChange = { vm.update { copy(sourceNameType = if (it) "real" else "alias") } })
        var showApiMgr by remember { mutableStateOf(false) }
        SettingsActionItem(t("setting_manage_user_api"), t("setting_manage_user_api_desc")) { showApiMgr = true }
        if (showApiMgr) UserApiSourceManager(onDismiss = { showApiMgr = false })
    }
    SettingSection(t("layout")) {
        SettingsSwitchItem(t("enable_tab_scroll"), t("enable_tab_scroll_desc"), checked = s.homePageScroll,
            onCheckedChange = { vm.update { copy(homePageScroll = it) } })
        SettingsSwitchItem(t("auto_hide_play_bar"), t("auto_hide_play_bar_desc"), checked = s.autoHidePlayBar,
            onCheckedChange = { vm.update { copy(autoHidePlayBar = it) } })
        SettingsSwitchItem(t("allow_progress_seek"), checked = s.allowProgressBarSeek,
            onCheckedChange = { vm.update { copy(allowProgressBarSeek = it) } })
    }
}

@Composable private fun PlayerSettings(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    SettingSection(t("setting_playback")) {
        SettingsDropdownItem(t("setting_play_quality"), s.playQuality, listOf("128k","192k","320k","flac","flac24bit"),
            onSelect = { vm.update { copy(playQuality = it) } })
        SettingsSwitchItem(t("setting_auto_play"), checked = s.startupAutoPlay,
            onCheckedChange = { vm.update { copy(startupAutoPlay = it) } })
        SettingsSwitchItem(t("setting_save_play_time"), t("setting_save_play_time_desc"), checked = s.isSavePlayTime,
            onCheckedChange = { vm.update { copy(isSavePlayTime = it) } })
        SettingsDropdownItem(t("setting_cache_size"), s.cacheSize, listOf("100","250","500","1024"),
            onSelect = { vm.update { copy(cacheSize = it) } })
    }
    SettingSection(t("setting_features")) {
        SettingsSwitchItem(t("setting_audio_focus"), checked = s.isHandleAudioFocus,
            onCheckedChange = { vm.update { copy(isHandleAudioFocus = it) } })
        SettingsSwitchItem(t("setting_lyric_translation"), checked = s.isShowLyricTranslation,
            onCheckedChange = { vm.update { copy(isShowLyricTranslation = it) } })
        SettingsSwitchItem(t("setting_notification_image"), checked = s.isShowNotificationImage,
            onCheckedChange = { vm.update { copy(isShowNotificationImage = it) } })
        SettingsSwitchItem(t("setting_auto_clean_played"), checked = s.isAutoCleanPlayedList,
            onCheckedChange = { vm.update { copy(isAutoCleanPlayedList = it) } })
        SettingsSwitchItem(t("setting_bluetooth_lyric"), t("setting_bluetooth_lyric_desc"), 
            checked = s.isShowBluetoothLyric,
            onCheckedChange = { vm.update { copy(isShowBluetoothLyric = it) } })
    }
}

@Composable private fun DesktopLyricSettings(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    val playerVM: cn.guoyujie666.music.compose.ui.home.viewmodel.PlayerViewModel = hiltViewModel()
    val ctx = LocalContext.current
    fun updateStyle() { playerVM.sendDesktopLyricStyle() }

    SettingSection(t("setting_desktop_lyric")) {
        SettingsSwitchItem(t("setting_desktop_enable"), checked = s.desktopLyricEnable,
            onCheckedChange = { enable ->
                if (enable && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                    !android.provider.Settings.canDrawOverlays(ctx)) {
                    // Direct user to overlay permission settings
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${ctx.packageName}"))
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                    return@SettingsSwitchItem
                }
                vm.update { copy(desktopLyricEnable = enable) }
                playerVM.setDesktopLyricEnabled(enable)
            })
        SettingsSwitchItem(t("setting_desktop_lock"), checked = s.desktopLyricIsLock,
            onCheckedChange = { vm.update { copy(desktopLyricIsLock = it) }; updateStyle() })
        SettingsSwitchItem(t("setting_desktop_single_line"), checked = s.desktopLyricIsSingleLine,
            onCheckedChange = { vm.update { copy(desktopLyricIsSingleLine = it) }; updateStyle() })
        SettingsSwitchItem(t("setting_desktop_toggle_anim"), checked = s.desktopLyricShowToggleAnima,
            onCheckedChange = { vm.update { copy(desktopLyricShowToggleAnima = it) }; updateStyle() })
        SettingsSliderItem(t("setting_desktop_max_lines"), value = s.desktopLyricMaxLineNum.toFloat(), range = 1f..15f,
            onValueChange = { vm.update { copy(desktopLyricMaxLineNum = it.toInt()) }; updateStyle() })
        SettingsSliderItem(t("setting_desktop_width"), value = s.desktopLyricWidth.toFloat(), range = 100f..800f,
            onValueChange = { vm.update { copy(desktopLyricWidth = it.toInt()) }; updateStyle() })
    }
    SettingSection(t("setting_style")) {
        SettingsSliderItem(t("setting_desktop_font_size"), value = s.desktopLyricFontSize.toFloat(), range = 12f..48f,
            onValueChange = { vm.update { copy(desktopLyricFontSize = it.toInt()) }; updateStyle() })
        SettingsSliderItem(t("setting_desktop_opacity"), value = s.desktopLyricOpacity, range = 0.1f..1f,
            onValueChange = { vm.update { copy(desktopLyricOpacity = it) }; updateStyle() })
    }
}

@Composable private fun SearchSettings(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    SettingSection(t("setting_search")) {
        SettingsSwitchItem(t("setting_hot_search"), checked = s.searchIsShowHotSearch,
            onCheckedChange = { vm.update { copy(searchIsShowHotSearch = it) } })
        SettingsSwitchItem(t("setting_search_history"), checked = s.searchIsShowHistorySearch,
            onCheckedChange = { vm.update { copy(searchIsShowHistorySearch = it) } })
    }
}

@Composable private fun ListSettings(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    SettingSection(t("setting_list_display")) {
        SettingsSwitchItem(t("setting_click_play_list"), t("setting_click_play_list_desc"), checked = s.listIsClickPlayList,
            onCheckedChange = { vm.update { copy(listIsClickPlayList = it) } })
        SettingsSwitchItem(t("setting_show_source"), checked = s.listIsShowSource,
            onCheckedChange = { vm.update { copy(listIsShowSource = it) } })
        SettingsSwitchItem(t("setting_show_album_name"), checked = s.listIsShowAlbumName,
            onCheckedChange = { vm.update { copy(listIsShowAlbumName = it) } })
        SettingsSwitchItem(t("setting_show_duration"), checked = s.listIsShowInterval,
            onCheckedChange = { vm.update { copy(listIsShowInterval = it) } })
        SettingsSwitchItem(t("setting_save_scroll"), checked = s.listIsSaveScrollLocation,
            onCheckedChange = { vm.update { copy(listIsSaveScrollLocation = it) } })
    }
    SettingSection(t("setting_add_music")) {
        SettingsDropdownItem(t("setting_add_location"), s.listAddMusicLocationType, listOf(t("top"), t("bottom")),
            onSelect = { vm.update { copy(listAddMusicLocationType = it) } })
    }
}

@Composable private fun SyncSettings(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    SettingSection(t("setting_sync")) {
        SettingsSwitchItem(t("setting_sync_enable"), t("setting_sync_enable_desc"), checked = s.syncEnable,
            onCheckedChange = { vm.update { copy(syncEnable = it) } })
        Text(t("setting_sync_server_note"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable private fun BackupSettings(vm: SettingsViewModel) {
    val context = LocalContext.current
    val playerVM: PlayerViewModel = hiltViewModel()
    val settings by vm.settings.collectAsState()

    val exportOkMsg = t("setting_export_success")
    val exportFailMsg = t("setting_export_failed")
    val importOkMsg = t("setting_import_success")
    val importFailMsg = t("setting_import_failed")

    // Export launchers
    val exportListLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = vm.backupManager.exportAllLists(uri, playerVM.getDefaultList())
        Toast.makeText(context, if (ok) exportOkMsg else exportFailMsg, Toast.LENGTH_SHORT).show()
    }
    val exportSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = vm.backupManager.exportSettings(uri, settings)
        Toast.makeText(context, if (ok) exportOkMsg else exportFailMsg, Toast.LENGTH_SHORT).show()
    }
    val exportAllLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = vm.backupManager.exportAllData(uri, settings, playerVM.getDefaultList())
        Toast.makeText(context, if (ok) exportOkMsg else exportFailMsg, Toast.LENGTH_SHORT).show()
    }

    // Import launcher: reads file once, dispatches to settings and/or lists
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (settingsResult, listResult) = vm.backupManager.importAll(uri)
        var ok = false
        if (settingsResult != null) {
            vm.applyImportedSettings(settingsResult)
            ok = true
        }
        if (listResult != null) {
            if (listResult.defaultListSongs.isNotEmpty()) {
                playerVM.addToDefaultList(listResult.defaultListSongs)
            }
            ok = true
        }
        Toast.makeText(context, if (ok) importOkMsg else importFailMsg, Toast.LENGTH_SHORT).show()
    }

    SettingSection(t("setting_backup")) {
        SettingsActionItem(t("setting_export_settings"), t("setting_export_settings_desc")) {
            exportSettingsLauncher.launch("lx_setting.lxmc")
        }
        SettingsActionItem(t("setting_import_settings"), t("setting_import_settings_desc")) {
            importLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
        }
        Spacer(Modifier.height(8.dp))
        SettingsActionItem(t("setting_export_lists"), t("setting_export_lists_desc")) {
            exportListLauncher.launch("lx_list.lxmc")
        }
        SettingsActionItem(t("setting_import_lists"), t("setting_import_lists_desc")) {
            importLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
        }
        Spacer(Modifier.height(8.dp))
        SettingsActionItem(t("setting_export_all"), t("setting_export_all_desc")) {
            exportAllLauncher.launch("lx_all.lxmc")
        }
    }
}

@Composable private fun OtherSettings(vm: SettingsViewModel) {
    val cacheSize by vm.cacheSizeStr.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(t("setting_clear_res")) },
            text = { Text("当前缓存: $cacheSize\n确定清除所有缓存？") },
            confirmButton = {
                TextButton(onClick = { vm.clearCaches(); showConfirm = false }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(t("cancel")) } }
        )
    }
    SettingSection(t("setting_tools")) {
        SettingsActionItem(t("setting_dislike_list"), t("setting_dislike_list_desc"))
        SettingsInfoItem(t("setting_cache_size"), cacheSize)
        SettingsActionItem(t("setting_clear_res"), t("setting_clear_res_desc")) {
            showConfirm = true
        }
    }
}

@Composable private fun VersionSettings(vm: SettingsViewModel) {
    SettingSection(t("setting_version")) {
        SettingsInfoItem(t("setting_current_version"), "2.0.0-alpha.1")
        SettingsInfoItem(t("setting_build"), "76")
        SettingsActionItem(t("setting_check_update"), t("setting_check_update_desc"))
    }
}

@Composable private fun AboutSettings(vm: SettingsViewModel) {
    SettingSection(t("setting_about")) {
        SettingsInfoItem(t("setting_name"), "LX Music (落雪音乐)")
        SettingsInfoItem(t("setting_developer"), "lyswhut")
        SettingsInfoItem(t("setting_license"), "Apache 2.0")
        SettingsActionItem(t("setting_github"), t("setting_github_desc"))
        SettingsActionItem(t("setting_faq"), t("setting_faq_desc"))
    }
}

// ═════════════════════════════════════════════════════════════════════
//  reusable setting controls (same API, slightly polished)
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun ThemeColorGrid(selectedId: String, onSelect: (String) -> Unit, isFirst: Boolean = false, isLast: Boolean = false) {
    val themes = cn.guoyujie666.music.compose.ui.theme.ThemeRegistry.themes
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(t("theme_color"), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp))
        // 4 columns grid
        val rows = themes.chunked(4)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { theme ->
                    val isSelected = theme.id == selectedId
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onSelect(theme.id) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp)
                                .clip(CircleShape)
                                .background(theme.primary)
                                .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(theme.name, style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                // Fill remaining space if row has fewer than 4
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp))
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsSwitchItem(title: String, description: String = "", checked: Boolean = false,
                       onCheckedChange: (Boolean) -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (description.isNotEmpty())
                    Text(description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun SettingsDropdownItem(title: String, selected: String, options: List<String>,
                          labels: List<String>? = null, onSelect: (String) -> Unit = {},
                          isFirst: Boolean = false, isLast: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = remember(selected, labels) {
        if (labels != null) {
            val idx = options.indexOf(selected)
            if (idx >= 0) labels[idx] else selected.ifEmpty { "Auto" }
        } else selected.ifEmpty { "Auto" }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        onClick = { expanded = true }
    ) {
        Box {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(displayLabel, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { idx, opt ->
                    val label = labels?.getOrNull(idx) ?: opt
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onSelect(opt); expanded = false },
                        leadingIcon = if (opt == selected) { { Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } } else null
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSliderItem(title: String, value: Float, range: ClosedFloatingPointRange<Float>,
                       onValueChange: (Float) -> Unit = {},
                       isFirst: Boolean = false, isLast: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatSliderVal(title, value), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
fun SettingsActionItem(title: String, description: String,
                       isFirst: Boolean = false, isLast: Boolean = false,
                       onClick: () -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        onClick = onClick
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun SettingsInfoItem(label: String, value: String,
                     isFirst: Boolean = false, isLast: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSliderVal(title: String, v: Float) = when {
    title.contains("Font") || title.contains("Size") || title.contains("Lines") || title.contains("Width") -> v.toInt().toString()
    title.contains("Volume") || title.contains("音量") -> "${(v * 100).toInt()}%"
    title.contains("Rate") || title.contains("Speed") || title.contains("速率") || title.contains("倍速") -> "${"%.2f".format(v)}x"
    title.contains("Opacity") -> "${(v * 100).toInt()}%"
    else -> v.toString()
}
