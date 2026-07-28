package cn.guoyujie666.music.compose.core.setting

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.guoyujie666.music.compose.core.model.AppSetting
import cn.guoyujie666.music.compose.core.model.KnownQualities
import cn.guoyujie666.music.compose.core.model.KnownSources
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lx_settings")

/**
 * Settings persistence manager using DataStore.
 *
 * Ported from AsyncStorage-based settings in the RN app.
 * Uses Preferences DataStore (key-value) to maintain backward compatibility
 * with the flat key structure of AppSetting.
 *
 * Migration note: When upgrading from the RN app, a one-time migration
 * reads AsyncStorage @setting_v1 and writes to DataStore.
 */
@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Preference keys matching the original flat dotted keys
        private val KEY_VERSION = stringPreferencesKey("version")
        private val KEY_IS_AUTO_THEME = booleanPreferencesKey("common.isAutoTheme")
        private val KEY_LANG_ID = stringPreferencesKey("common.langId")
        private val KEY_API_SOURCE = stringPreferencesKey("common.apiSource")
        private val KEY_SOURCE_NAME_TYPE = stringPreferencesKey("common.sourceNameType")
        private val KEY_SHARE_TYPE = stringPreferencesKey("common.shareType")
        private val KEY_IS_AGREE_PACT = booleanPreferencesKey("common.isAgreePact")
        private val KEY_AUTO_HIDE_PLAY_BAR = booleanPreferencesKey("common.autoHidePlayBar")
        private val KEY_DRAWER_LAYOUT_POSITION = stringPreferencesKey("common.drawerLayoutPosition")
        private val KEY_HOME_PAGE_SCROLL = booleanPreferencesKey("common.homePageScroll")
        private val KEY_ALLOW_PROGRESS_BAR_SEEK = booleanPreferencesKey("common.allowProgressBarSeek")
        private val KEY_SHOW_BACK_BTN = booleanPreferencesKey("common.showBackBtn")
        private val KEY_SHOW_EXIT_BTN = booleanPreferencesKey("common.showExitBtn")
        private val KEY_USE_SYSTEM_FILE_SELECTOR = booleanPreferencesKey("common.useSystemFileSelector")
        private val KEY_ALWAYS_KEEP_STATUSBAR_HEIGHT = booleanPreferencesKey("common.alwaysKeepStatusbarHeight")

        // Theme
        private val KEY_THEME_ID = stringPreferencesKey("theme.id")
        private val KEY_THEME_LIGHT_ID = stringPreferencesKey("theme.lightId")
        private val KEY_THEME_DARK_ID = stringPreferencesKey("theme.darkId")
        private val KEY_THEME_HIDE_BG_DARK = booleanPreferencesKey("theme.hideBgDark")
        private val KEY_THEME_DYNAMIC_BG = booleanPreferencesKey("theme.dynamicBg")
        private val KEY_THEME_FONT_SHADOW = booleanPreferencesKey("theme.fontShadow")

        // Player
        private val KEY_STARTUP_AUTO_PLAY = booleanPreferencesKey("player.startupAutoPlay")
        private val KEY_STARTUP_PUSH_PLAY_DETAIL = booleanPreferencesKey("player.startupPushPlayDetailScreen")
        private val KEY_TOGGLE_PLAY_METHOD = stringPreferencesKey("player.togglePlayMethod")
        private val KEY_PLAY_QUALITY = stringPreferencesKey("player.playQuality")
        private val KEY_IS_SAVE_PLAY_TIME = booleanPreferencesKey("player.isSavePlayTime")
        private val KEY_VOLUME = floatPreferencesKey("player.volume")
        private val KEY_PLAYBACK_RATE = floatPreferencesKey("player.playbackRate")
        private val KEY_CACHE_SIZE = stringPreferencesKey("player.cacheSize")
        private val KEY_TIMEOUT_EXIT = stringPreferencesKey("player.timeoutExit")
        private val KEY_TIMEOUT_EXIT_PLAYED = booleanPreferencesKey("player.timeoutExitPlayed")
        private val KEY_IS_AUTO_CLEAN_PLAYED_LIST = booleanPreferencesKey("player.isAutoCleanPlayedList")
        private val KEY_IS_HANDLE_AUDIO_FOCUS = booleanPreferencesKey("player.isHandleAudioFocus")
        private val KEY_IS_ENABLE_AUDIO_OFFLOAD = booleanPreferencesKey("player.isEnableAudioOffload")
        private val KEY_IS_SHOW_LYRIC_TRANSLATION = booleanPreferencesKey("player.isShowLyricTranslation")
        private val KEY_IS_SHOW_LYRIC_ROMA = booleanPreferencesKey("player.isShowLyricRoma")
        private val KEY_IS_SHOW_NOTIFICATION_IMAGE = booleanPreferencesKey("player.isShowNotificationImage")
        private val KEY_IS_S2T = booleanPreferencesKey("player.isS2t")
        private val KEY_IS_SHOW_BLUETOOTH_LYRIC = booleanPreferencesKey("player.isShowBluetoothLyric")
        private val KEY_IS_SHOW_BLUETOOTH_FULL_LYRIC = booleanPreferencesKey("player.isShowBluetoothFullLyric")

        // Play Detail
        private val KEY_PLAYDETAIL_STYLE_ALIGN = stringPreferencesKey("playDetail.style.align")
        private val KEY_PLAYDETAIL_VERTICAL_LRC_FONT_SIZE = intPreferencesKey("playDetail.vertical.style.lrcFontSize")
        private val KEY_PLAYDETAIL_HORIZONTAL_LRC_FONT_SIZE = intPreferencesKey("playDetail.horizontal.style.lrcFontSize")
        private val KEY_PLAYDETAIL_IS_SHOW_LYRIC_PROGRESS = booleanPreferencesKey("playDetail.isShowLyricProgressSetting")
        private val KEY_PLAYDETAIL_LYRIC_FONT_SIZE = intPreferencesKey("playDetail.lyricFontSize")
        private val KEY_PLAYDETAIL_TRANS_FONT_SIZE = intPreferencesKey("playDetail.transFontSize")
        private val KEY_PLAYDETAIL_LYRIC_ALIGN = stringPreferencesKey("playDetail.lyricAlign")
        private val KEY_PLAYDETAIL_LYRIC_SEEK = booleanPreferencesKey("playDetail.lyricSeek")
        private val KEY_PLAYDETAIL_WORD_HIGHLIGHT = booleanPreferencesKey("playDetail.wordHighlight")
        private val KEY_PLAYDETAIL_DUET_LYRIC = booleanPreferencesKey("playDetail.duetLyric")

        // Desktop Lyric
        private val KEY_DESKTOP_LYRIC_ENABLE = booleanPreferencesKey("desktopLyric.enable")
        private val KEY_DESKTOP_LYRIC_IS_LOCK = booleanPreferencesKey("desktopLyric.isLock")
        private val KEY_DESKTOP_LYRIC_WIDTH = intPreferencesKey("desktopLyric.width")
        private val KEY_DESKTOP_LYRIC_MAX_LINE_NUM = intPreferencesKey("desktopLyric.maxLineNum")
        private val KEY_DESKTOP_LYRIC_IS_SINGLE_LINE = booleanPreferencesKey("desktopLyric.isSingleLine")
        private val KEY_DESKTOP_LYRIC_SHOW_TOGGLE_ANIMA = booleanPreferencesKey("desktopLyric.showToggleAnima")
        private val KEY_DESKTOP_LYRIC_FONT_SIZE = intPreferencesKey("desktopLyric.style.fontSize")
        private val KEY_DESKTOP_LYRIC_OPACITY = floatPreferencesKey("desktopLyric.style.opacity")
        private val KEY_DESKTOP_LYRIC_UNPLAY_COLOR = stringPreferencesKey("desktopLyric.style.lyricUnplayColor")
        private val KEY_DESKTOP_LYRIC_PLAYED_COLOR = stringPreferencesKey("desktopLyric.style.lyricPlayedColor")
        private val KEY_DESKTOP_LYRIC_SHADOW_COLOR = stringPreferencesKey("desktopLyric.style.lyricShadowColor")

        // Search
        private val KEY_SEARCH_IS_SHOW_HOT = booleanPreferencesKey("search.isShowHotSearch")
        private val KEY_SEARCH_IS_SHOW_HISTORY = booleanPreferencesKey("search.isShowHistorySearch")
        private val KEY_SEARCH_HISTORY_LIST = stringPreferencesKey("search.historyList")
        private val KEY_SEARCH_SOURCE = stringPreferencesKey("search.source")

        // Player save/restore
        private val KEY_SAVED_PLAY_INFO = stringPreferencesKey("player.savedPlayInfo")

        // List
        private val KEY_LIST_IS_CLICK_PLAY = booleanPreferencesKey("list.isClickPlayList")
        private val KEY_LIST_IS_SHOW_SOURCE = booleanPreferencesKey("list.isShowSource")
        private val KEY_LIST_IS_SHOW_ALBUM_NAME = booleanPreferencesKey("list.isShowAlbumName")
        private val KEY_LIST_IS_SHOW_INTERVAL = booleanPreferencesKey("list.isShowInterval")
        private val KEY_LIST_IS_SAVE_SCROLL = booleanPreferencesKey("list.isSaveScrollLocation")
        private val KEY_LIST_ADD_MUSIC_LOCATION = stringPreferencesKey("list.addMusicLocationType")

        // Download
        private val KEY_DOWNLOAD_FILE_NAME = stringPreferencesKey("download.fileName")

        // Sync
        private val KEY_SYNC_ENABLE = booleanPreferencesKey("sync.enable")
    }

    /**
     * Observe the full settings as a Flow.
     */
    val settingsFlow: Flow<AppSetting> = context.dataStore.data.map { prefs ->
        AppSetting(
            version = prefs[KEY_VERSION] ?: "2.0.0",
            isAutoTheme = prefs[KEY_IS_AUTO_THEME] ?: false,
            langId = prefs[KEY_LANG_ID],
            apiSource = prefs[KEY_API_SOURCE] ?: KnownSources.KW,
            sourceNameType = prefs[KEY_SOURCE_NAME_TYPE] ?: "real",
            shareType = prefs[KEY_SHARE_TYPE] ?: "system",
            isAgreePact = prefs[KEY_IS_AGREE_PACT] ?: false,
            autoHidePlayBar = prefs[KEY_AUTO_HIDE_PLAY_BAR] ?: true,
            drawerLayoutPosition = prefs[KEY_DRAWER_LAYOUT_POSITION] ?: "left",
            homePageScroll = prefs[KEY_HOME_PAGE_SCROLL] ?: true,
            allowProgressBarSeek = prefs[KEY_ALLOW_PROGRESS_BAR_SEEK] ?: true,
            showBackBtn = prefs[KEY_SHOW_BACK_BTN] ?: false,
            showExitBtn = prefs[KEY_SHOW_EXIT_BTN] ?: false,
            useSystemFileSelector = prefs[KEY_USE_SYSTEM_FILE_SELECTOR] ?: false,
            alwaysKeepStatusbarHeight = prefs[KEY_ALWAYS_KEEP_STATUSBAR_HEIGHT] ?: false,

            themeId = prefs[KEY_THEME_ID] ?: "green",
            themeLightId = prefs[KEY_THEME_LIGHT_ID] ?: "green",
            themeDarkId = prefs[KEY_THEME_DARK_ID] ?: "black",
            themeHideBgDark = prefs[KEY_THEME_HIDE_BG_DARK] ?: false,
            themeDynamicBg = prefs[KEY_THEME_DYNAMIC_BG] ?: false,
            themeFontShadow = prefs[KEY_THEME_FONT_SHADOW] ?: false,

            startupAutoPlay = prefs[KEY_STARTUP_AUTO_PLAY] ?: false,
            startupPushPlayDetailScreen = prefs[KEY_STARTUP_PUSH_PLAY_DETAIL] ?: false,
            togglePlayMethod = prefs[KEY_TOGGLE_PLAY_METHOD] ?: "listLoop",
            playQuality = prefs[KEY_PLAY_QUALITY] ?: KnownQualities._128K,
            isSavePlayTime = prefs[KEY_IS_SAVE_PLAY_TIME] ?: false,
            volume = prefs[KEY_VOLUME] ?: 1f,
            playbackRate = prefs[KEY_PLAYBACK_RATE] ?: 1f,
            cacheSize = prefs[KEY_CACHE_SIZE] ?: "500",
            timeoutExit = prefs[KEY_TIMEOUT_EXIT] ?: "",
            timeoutExitPlayed = prefs[KEY_TIMEOUT_EXIT_PLAYED] ?: false,
            isAutoCleanPlayedList = prefs[KEY_IS_AUTO_CLEAN_PLAYED_LIST] ?: false,
            isHandleAudioFocus = prefs[KEY_IS_HANDLE_AUDIO_FOCUS] ?: true,
            isEnableAudioOffload = prefs[KEY_IS_ENABLE_AUDIO_OFFLOAD] ?: false,
            isShowLyricTranslation = prefs[KEY_IS_SHOW_LYRIC_TRANSLATION] ?: false,
            isShowLyricRoma = prefs[KEY_IS_SHOW_LYRIC_ROMA] ?: false,
            isShowNotificationImage = prefs[KEY_IS_SHOW_NOTIFICATION_IMAGE] ?: true,
            isS2t = prefs[KEY_IS_S2T] ?: false,
            isShowBluetoothLyric = prefs[KEY_IS_SHOW_BLUETOOTH_LYRIC] ?: false,
            isShowBluetoothFullLyric = prefs[KEY_IS_SHOW_BLUETOOTH_FULL_LYRIC] ?: false,

            playDetailStyleAlign = prefs[KEY_PLAYDETAIL_STYLE_ALIGN] ?: "center",
            playDetailVerticalLrcFontSize = prefs[KEY_PLAYDETAIL_VERTICAL_LRC_FONT_SIZE] ?: 14,
            playDetailHorizontalLrcFontSize = prefs[KEY_PLAYDETAIL_HORIZONTAL_LRC_FONT_SIZE] ?: 18,
            playDetailIsShowLyricProgressSetting = prefs[KEY_PLAYDETAIL_IS_SHOW_LYRIC_PROGRESS] ?: true,
            lyricFontSize = prefs[KEY_PLAYDETAIL_LYRIC_FONT_SIZE] ?: 16,
            transFontSize = prefs[KEY_PLAYDETAIL_TRANS_FONT_SIZE] ?: 12,
            lyricAlign = prefs[KEY_PLAYDETAIL_LYRIC_ALIGN] ?: "center",
            lyricSeekEnabled = prefs[KEY_PLAYDETAIL_LYRIC_SEEK] ?: false,
            showDuetLyric = prefs[KEY_PLAYDETAIL_DUET_LYRIC] ?: true,
            showWordHighlight = prefs[KEY_PLAYDETAIL_WORD_HIGHLIGHT] ?: true,

            desktopLyricEnable = prefs[KEY_DESKTOP_LYRIC_ENABLE] ?: false,
            desktopLyricIsLock = prefs[KEY_DESKTOP_LYRIC_IS_LOCK] ?: false,
            desktopLyricWidth = prefs[KEY_DESKTOP_LYRIC_WIDTH] ?: 300,
            desktopLyricMaxLineNum = prefs[KEY_DESKTOP_LYRIC_MAX_LINE_NUM] ?: 5,
            desktopLyricIsSingleLine = prefs[KEY_DESKTOP_LYRIC_IS_SINGLE_LINE] ?: false,
            desktopLyricShowToggleAnima = prefs[KEY_DESKTOP_LYRIC_SHOW_TOGGLE_ANIMA] ?: true,
            desktopLyricFontSize = prefs[KEY_DESKTOP_LYRIC_FONT_SIZE] ?: 24,
            desktopLyricOpacity = prefs[KEY_DESKTOP_LYRIC_OPACITY] ?: 1f,
            desktopLyricUnplayColor = prefs[KEY_DESKTOP_LYRIC_UNPLAY_COLOR] ?: "#FFFFFF",
            desktopLyricPlayedColor = prefs[KEY_DESKTOP_LYRIC_PLAYED_COLOR] ?: "#4CAF50",
            desktopLyricShadowColor = prefs[KEY_DESKTOP_LYRIC_SHADOW_COLOR] ?: "#000000",

            searchIsShowHotSearch = prefs[KEY_SEARCH_IS_SHOW_HOT] ?: false,
            searchIsShowHistorySearch = prefs[KEY_SEARCH_IS_SHOW_HISTORY] ?: false,
            searchHistoryList = prefs[KEY_SEARCH_HISTORY_LIST] ?: "",
            searchSource = prefs[KEY_SEARCH_SOURCE] ?: "all",

            listIsClickPlayList = prefs[KEY_LIST_IS_CLICK_PLAY] ?: false,
            listIsShowSource = prefs[KEY_LIST_IS_SHOW_SOURCE] ?: true,
            listIsShowAlbumName = prefs[KEY_LIST_IS_SHOW_ALBUM_NAME] ?: false,
            listIsShowInterval = prefs[KEY_LIST_IS_SHOW_INTERVAL] ?: true,
            listIsSaveScrollLocation = prefs[KEY_LIST_IS_SAVE_SCROLL] ?: true,
            listAddMusicLocationType = prefs[KEY_LIST_ADD_MUSIC_LOCATION] ?: "top",

            downloadFileName = prefs[KEY_DOWNLOAD_FILE_NAME] ?: "歌名 - 歌手",
            syncEnable = prefs[KEY_SYNC_ENABLE] ?: false
        )
    }

    /** Get current settings as a one-shot snapshot */
    suspend fun getSettings(): AppSetting = settingsFlow.first()

    /** Update a subset of settings */
    suspend fun updateSettings(update: AppSetting) = context.dataStore.edit { prefs ->
        prefs[KEY_VERSION] = update.version
        prefs[KEY_IS_AUTO_THEME] = update.isAutoTheme
        update.langId?.let { prefs[KEY_LANG_ID] = it } ?: prefs.remove(KEY_LANG_ID)
        prefs[KEY_API_SOURCE] = update.apiSource
        prefs[KEY_SOURCE_NAME_TYPE] = update.sourceNameType
        prefs[KEY_SHARE_TYPE] = update.shareType
        prefs[KEY_IS_AGREE_PACT] = update.isAgreePact
        prefs[KEY_AUTO_HIDE_PLAY_BAR] = update.autoHidePlayBar
        prefs[KEY_DRAWER_LAYOUT_POSITION] = update.drawerLayoutPosition
        prefs[KEY_HOME_PAGE_SCROLL] = update.homePageScroll
        prefs[KEY_ALLOW_PROGRESS_BAR_SEEK] = update.allowProgressBarSeek
        prefs[KEY_SHOW_BACK_BTN] = update.showBackBtn
        prefs[KEY_SHOW_EXIT_BTN] = update.showExitBtn
        prefs[KEY_USE_SYSTEM_FILE_SELECTOR] = update.useSystemFileSelector
        prefs[KEY_ALWAYS_KEEP_STATUSBAR_HEIGHT] = update.alwaysKeepStatusbarHeight

        prefs[KEY_THEME_ID] = update.themeId
        prefs[KEY_THEME_LIGHT_ID] = update.themeLightId
        prefs[KEY_THEME_DARK_ID] = update.themeDarkId
        prefs[KEY_THEME_HIDE_BG_DARK] = update.themeHideBgDark
        prefs[KEY_THEME_DYNAMIC_BG] = update.themeDynamicBg
        prefs[KEY_THEME_FONT_SHADOW] = update.themeFontShadow

        prefs[KEY_STARTUP_AUTO_PLAY] = update.startupAutoPlay
        prefs[KEY_STARTUP_PUSH_PLAY_DETAIL] = update.startupPushPlayDetailScreen
        prefs[KEY_TOGGLE_PLAY_METHOD] = update.togglePlayMethod
        prefs[KEY_PLAY_QUALITY] = update.playQuality
        prefs[KEY_IS_SAVE_PLAY_TIME] = update.isSavePlayTime
        prefs[KEY_VOLUME] = update.volume
        prefs[KEY_PLAYBACK_RATE] = update.playbackRate
        prefs[KEY_CACHE_SIZE] = update.cacheSize
        prefs[KEY_TIMEOUT_EXIT] = update.timeoutExit
        prefs[KEY_TIMEOUT_EXIT_PLAYED] = update.timeoutExitPlayed
        prefs[KEY_IS_AUTO_CLEAN_PLAYED_LIST] = update.isAutoCleanPlayedList
        prefs[KEY_IS_HANDLE_AUDIO_FOCUS] = update.isHandleAudioFocus
        prefs[KEY_IS_ENABLE_AUDIO_OFFLOAD] = update.isEnableAudioOffload
        prefs[KEY_IS_SHOW_LYRIC_TRANSLATION] = update.isShowLyricTranslation
        prefs[KEY_IS_SHOW_LYRIC_ROMA] = update.isShowLyricRoma
        prefs[KEY_IS_SHOW_NOTIFICATION_IMAGE] = update.isShowNotificationImage
        prefs[KEY_IS_S2T] = update.isS2t
        prefs[KEY_IS_SHOW_BLUETOOTH_LYRIC] = update.isShowBluetoothLyric
        prefs[KEY_IS_SHOW_BLUETOOTH_FULL_LYRIC] = update.isShowBluetoothFullLyric

        prefs[KEY_PLAYDETAIL_STYLE_ALIGN] = update.playDetailStyleAlign
        prefs[KEY_PLAYDETAIL_VERTICAL_LRC_FONT_SIZE] = update.playDetailVerticalLrcFontSize
        prefs[KEY_PLAYDETAIL_HORIZONTAL_LRC_FONT_SIZE] = update.playDetailHorizontalLrcFontSize
        prefs[KEY_PLAYDETAIL_IS_SHOW_LYRIC_PROGRESS] = update.playDetailIsShowLyricProgressSetting
        prefs[KEY_PLAYDETAIL_LYRIC_FONT_SIZE] = update.lyricFontSize
        prefs[KEY_PLAYDETAIL_TRANS_FONT_SIZE] = update.transFontSize
        prefs[KEY_PLAYDETAIL_LYRIC_ALIGN] = update.lyricAlign
        prefs[KEY_PLAYDETAIL_LYRIC_SEEK] = update.lyricSeekEnabled
        prefs[KEY_PLAYDETAIL_DUET_LYRIC] = update.showDuetLyric
        prefs[KEY_PLAYDETAIL_WORD_HIGHLIGHT] = update.showWordHighlight

        prefs[KEY_DESKTOP_LYRIC_ENABLE] = update.desktopLyricEnable
        prefs[KEY_DESKTOP_LYRIC_IS_LOCK] = update.desktopLyricIsLock
        prefs[KEY_DESKTOP_LYRIC_WIDTH] = update.desktopLyricWidth
        prefs[KEY_DESKTOP_LYRIC_MAX_LINE_NUM] = update.desktopLyricMaxLineNum
        prefs[KEY_DESKTOP_LYRIC_IS_SINGLE_LINE] = update.desktopLyricIsSingleLine
        prefs[KEY_DESKTOP_LYRIC_SHOW_TOGGLE_ANIMA] = update.desktopLyricShowToggleAnima
        prefs[KEY_DESKTOP_LYRIC_FONT_SIZE] = update.desktopLyricFontSize
        prefs[KEY_DESKTOP_LYRIC_OPACITY] = update.desktopLyricOpacity
        prefs[KEY_DESKTOP_LYRIC_UNPLAY_COLOR] = update.desktopLyricUnplayColor
        prefs[KEY_DESKTOP_LYRIC_PLAYED_COLOR] = update.desktopLyricPlayedColor
        prefs[KEY_DESKTOP_LYRIC_SHADOW_COLOR] = update.desktopLyricShadowColor

        prefs[KEY_SEARCH_IS_SHOW_HOT] = update.searchIsShowHotSearch
        prefs[KEY_SEARCH_IS_SHOW_HISTORY] = update.searchIsShowHistorySearch
        prefs[KEY_SEARCH_HISTORY_LIST] = update.searchHistoryList
        prefs[KEY_SEARCH_SOURCE] = update.searchSource

        prefs[KEY_LIST_IS_CLICK_PLAY] = update.listIsClickPlayList
        prefs[KEY_LIST_IS_SHOW_SOURCE] = update.listIsShowSource
        prefs[KEY_LIST_IS_SHOW_ALBUM_NAME] = update.listIsShowAlbumName
        prefs[KEY_LIST_IS_SHOW_INTERVAL] = update.listIsShowInterval
        prefs[KEY_LIST_IS_SAVE_SCROLL] = update.listIsSaveScrollLocation
        prefs[KEY_LIST_ADD_MUSIC_LOCATION] = update.listAddMusicLocationType

        prefs[KEY_DOWNLOAD_FILE_NAME] = update.downloadFileName
        prefs[KEY_SYNC_ENABLE] = update.syncEnable
    }
}
