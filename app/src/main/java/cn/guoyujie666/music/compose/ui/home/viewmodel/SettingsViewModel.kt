package cn.guoyujie666.music.compose.ui.home.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.guoyujie666.music.compose.core.model.AppSetting
import cn.guoyujie666.music.compose.core.backup.BackupManager
import cn.guoyujie666.music.compose.core.player.PlayerController
import cn.guoyujie666.music.compose.core.setting.SettingsManager
import cn.guoyujie666.music.compose.core.userapi.UserApiManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val userApiManager: UserApiManager,
    val backupManager: BackupManager,
    private val playerController: PlayerController,
    val themeManager: cn.guoyujie666.music.compose.ui.theme.ThemeManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var _cacheSizeStr = MutableStateFlow("计算中...")
    val cacheSizeStr: StateFlow<String> = _cacheSizeStr

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val size = computeCacheSize()
            withContext(Dispatchers.Main) {
                _cacheSizeStr.value = formatSize(size)
            }
        }
    }

    fun clearCaches() {
        viewModelScope.launch(Dispatchers.IO) {
            // Clear media cache (ExoPlayer SimpleCache)
            val mediaDir = java.io.File(context.cacheDir, "media_cache")
            if (mediaDir.exists()) mediaDir.deleteRecursively()
            // Clear cover image cache
            val coversDir = java.io.File(context.cacheDir, "covers")
            if (coversDir.exists()) coversDir.deleteRecursively()
            // Clear URL/lyric cache only
            context.getSharedPreferences("lx_cache", Context.MODE_PRIVATE).edit().clear().apply()
            // lx_play_state: only clear position, preserve default_list (试听列表) + lastMusic/lastPic
            val ps = context.getSharedPreferences("lx_play_state", Context.MODE_PRIVATE)
            val savedId = ps.getString("listId", null)
            val savedIdx = ps.getInt("index", 0)
            val savedDefaultList = ps.getString("default_list", null)
            val savedLastMusic = ps.getString("lastMusic", null)
            val savedLastPic = ps.getString("lastPic", null)
            val savedMusicList = ps.getString("musicList", null)
            ps.edit().clear().apply()
            ps.edit().apply {
                if (savedId != null) putString("listId", savedId).putInt("index", savedIdx)
                if (savedDefaultList != null) putString("default_list", savedDefaultList)
                if (savedLastMusic != null) putString("lastMusic", savedLastMusic)
                if (savedLastPic != null) putString("lastPic", savedLastPic)
                if (savedMusicList != null) putString("musicList", savedMusicList)
                apply()
            }
            refreshCacheSize()
        }
    }

    private fun computeCacheSize(): Long {
        var total = 0L
        fun dirSize(dir: java.io.File): Long {
            if (!dir.exists()) return 0L
            return dir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L }
        }
        total += dirSize(java.io.File(context.cacheDir, "media_cache"))
        total += dirSize(java.io.File(context.cacheDir, "covers"))
        return total
    }

    private val _settings = MutableStateFlow(
        runCatching { kotlinx.coroutines.runBlocking { settingsManager.getSettings() } }.getOrDefault(AppSetting())
    )
    val settings: StateFlow<AppSetting> = _settings.asStateFlow()

    init {
        cn.guoyujie666.music.compose.ui.common.globalSourceNameType = _settings.value.sourceNameType
        viewModelScope.launch {
            settingsManager.settingsFlow.collect { s ->
                _settings.value = s
                cn.guoyujie666.music.compose.ui.common.globalSourceNameType = s.sourceNameType
            }
        }
    }

    /**
     * All available source IDs including built-in sources and loaded user APIs.
     */
    val availableSources: StateFlow<List<String>> = combine(
        userApiManager.apiList,
        userApiManager.status
    ) { apiList, status ->
        val builtIn = listOf("kw", "kg", "tx", "wy", "mg")
        val userSources = apiList.map { "user_api_${it.id}" }
        builtIn + userSources
    }.stateIn(viewModelScope, SharingStarted.Lazily, listOf("kw", "kg", "tx", "wy", "mg"))

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }

    fun update(block: AppSetting.() -> AppSetting) {
        val updated = block(settings.value)
        viewModelScope.launch {
            settingsManager.updateSettings(updated)
        }
        // Propagate settings
        cn.guoyujie666.music.compose.ui.common.globalSourceNameType = updated.sourceNameType
        if (updated.isShowBluetoothLyric != settings.value.isShowBluetoothLyric) {
            playerController.bluetoothLyricEnabled = updated.isShowBluetoothLyric
        }
    }

    /** Replace current settings with imported ones. */
    fun applyImportedSettings(imported: AppSetting) {
        viewModelScope.launch {
            settingsManager.updateSettings(imported)
        }
        cn.guoyujie666.music.compose.ui.common.globalSourceNameType = imported.sourceNameType
        playerController.bluetoothLyricEnabled = imported.isShowBluetoothLyric
    }
}
