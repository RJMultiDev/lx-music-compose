package cn.guoyujie666.music.compose.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Full application settings schema.
 * Ported from src/types/app_setting.d.ts and src/config/defaultSetting.ts.
 * Uses a flat key structure matching the original app for data migration compatibility.
 */
@Serializable
data class AppSetting(
    // Version tracking
    val version: String = "2.0.0",

    // === Common ===
    @SerialName("common.isAutoTheme")
    val isAutoTheme: Boolean = false,

    @SerialName("common.langId")
    val langId: String? = null,

    @SerialName("common.apiSource")
    val apiSource: String = KnownSources.KW,

    @SerialName("common.sourceNameType")
    val sourceNameType: String = "real",  // "alias" | "real"

    @SerialName("common.shareType")
    val shareType: String = "system",  // "system" | "clipboard"

    @SerialName("common.isAgreePact")
    val isAgreePact: Boolean = false,

    @SerialName("common.autoHidePlayBar")
    val autoHidePlayBar: Boolean = true,

    @SerialName("common.drawerLayoutPosition")
    val drawerLayoutPosition: String = "left",  // "left" | "right"

    @SerialName("common.homePageScroll")
    val homePageScroll: Boolean = true,

    @SerialName("common.allowProgressBarSeek")
    val allowProgressBarSeek: Boolean = true,

    @SerialName("common.showBackBtn")
    val showBackBtn: Boolean = false,

    @SerialName("common.showExitBtn")
    val showExitBtn: Boolean = false,

    @SerialName("common.useSystemFileSelector")
    val useSystemFileSelector: Boolean = false,

    @SerialName("common.alwaysKeepStatusbarHeight")
    val alwaysKeepStatusbarHeight: Boolean = false,

    // === Theme ===
    @SerialName("theme.id")
    val themeId: String = "green",

    @SerialName("theme.lightId")
    val themeLightId: String = "green",

    @SerialName("theme.darkId")
    val themeDarkId: String = "black",

    @SerialName("theme.hideBgDark")
    val themeHideBgDark: Boolean = false,

    @SerialName("theme.dynamicBg")
    val themeDynamicBg: Boolean = false,

    @SerialName("theme.fontShadow")
    val themeFontShadow: Boolean = false,

    // === Player ===
    @SerialName("player.startupAutoPlay")
    val startupAutoPlay: Boolean = false,

    @SerialName("player.startupPushPlayDetailScreen")
    val startupPushPlayDetailScreen: Boolean = false,

    @SerialName("player.togglePlayMethod")
    val togglePlayMethod: String = MusicToggleModes.LIST_LOOP,

    @SerialName("player.playQuality")
    val playQuality: Quality = KnownQualities._128K,

    @SerialName("player.isSavePlayTime")
    val isSavePlayTime: Boolean = false,

    @SerialName("player.volume")
    val volume: Float = 1f,

    @SerialName("player.playbackRate")
    val playbackRate: Float = 1f,

    @SerialName("player.cacheSize")
    val cacheSize: String = "500",

    @SerialName("player.timeoutExit")
    val timeoutExit: String = "",

    @SerialName("player.timeoutExitPlayed")
    val timeoutExitPlayed: Boolean = false,

    @SerialName("player.isAutoCleanPlayedList")
    val isAutoCleanPlayedList: Boolean = false,

    @SerialName("player.isHandleAudioFocus")
    val isHandleAudioFocus: Boolean = true,

    @SerialName("player.isEnableAudioOffload")
    val isEnableAudioOffload: Boolean = false,

    @SerialName("player.isShowLyricTranslation")
    val isShowLyricTranslation: Boolean = false,

    @SerialName("player.isShowLyricRoma")
    val isShowLyricRoma: Boolean = false,

    @SerialName("player.isShowNotificationImage")
    val isShowNotificationImage: Boolean = true,

    @SerialName("player.isS2t")
    val isS2t: Boolean = false,

    @SerialName("player.isShowBluetoothLyric")
    val isShowBluetoothLyric: Boolean = false,

    @SerialName("player.isShowBluetoothFullLyric")
    val isShowBluetoothFullLyric: Boolean = false,

    // === Play Detail ===
    @SerialName("playDetail.style.align")
    val playDetailStyleAlign: String = "center",  // "center" | "left" | "right"

    @SerialName("playDetail.vertical.style.lrcFontSize")
    val playDetailVerticalLrcFontSize: Int = 14,

    @SerialName("playDetail.horizontal.style.lrcFontSize")
    val playDetailHorizontalLrcFontSize: Int = 18,

    @SerialName("playDetail.isShowLyricProgressSetting")
    val playDetailIsShowLyricProgressSetting: Boolean = true,

    @SerialName("playDetail.lyricFontSize")
    val lyricFontSize: Int = 16,

    @SerialName("playDetail.transFontSize")
    val transFontSize: Int = 12,

    @SerialName("playDetail.lyricAlign")
    val lyricAlign: String = "center",

    @SerialName("playDetail.lyricSeek")
    val lyricSeekEnabled: Boolean = false,

    @SerialName("playDetail.duetLyric")
    val showDuetLyric: Boolean = true,

    @SerialName("playDetail.wordHighlight")
    val showWordHighlight: Boolean = true,

    // === Desktop Lyric ===
    @SerialName("desktopLyric.enable")
    val desktopLyricEnable: Boolean = false,

    @SerialName("desktopLyric.isLock")
    val desktopLyricIsLock: Boolean = false,

    @SerialName("desktopLyric.width")
    val desktopLyricWidth: Int = 300,

    @SerialName("desktopLyric.maxLineNum")
    val desktopLyricMaxLineNum: Int = 5,

    @SerialName("desktopLyric.isSingleLine")
    val desktopLyricIsSingleLine: Boolean = false,

    @SerialName("desktopLyric.showToggleAnima")
    val desktopLyricShowToggleAnima: Boolean = true,

    @SerialName("desktopLyric.position.x")
    val desktopLyricPositionX: Float = 0f,

    @SerialName("desktopLyric.position.y")
    val desktopLyricPositionY: Float = 0f,

    @SerialName("desktopLyric.textPosition.x")
    val desktopLyricTextPositionX: String = "center",  // "left" | "center" | "right"

    @SerialName("desktopLyric.textPosition.y")
    val desktopLyricTextPositionY: String = "center",  // "top" | "center" | "bottom"

    @SerialName("desktopLyric.style.fontSize")
    val desktopLyricFontSize: Int = 24,

    @SerialName("desktopLyric.style.opacity")
    val desktopLyricOpacity: Float = 1f,

    @SerialName("desktopLyric.style.lyricUnplayColor")
    val desktopLyricUnplayColor: String = "#FFFFFF",

    @SerialName("desktopLyric.style.lyricPlayedColor")
    val desktopLyricPlayedColor: String = "#4CAF50",

    @SerialName("desktopLyric.style.lyricShadowColor")
    val desktopLyricShadowColor: String = "#000000",

    // === Search ===
    @SerialName("search.isShowHotSearch")
    val searchIsShowHotSearch: Boolean = false,

    @SerialName("search.isShowHistorySearch")
    val searchIsShowHistorySearch: Boolean = false,

    @SerialName("search.historyList")
    val searchHistoryList: String = "",  // JSON array of search history

    @SerialName("search.source")
    val searchSource: String = "all",  // persisted search source across tabs

    // === List ===
    @SerialName("list.isClickPlayList")
    val listIsClickPlayList: Boolean = false,

    @SerialName("list.isShowSource")
    val listIsShowSource: Boolean = true,

    @SerialName("list.isShowAlbumName")
    val listIsShowAlbumName: Boolean = false,

    @SerialName("list.isShowInterval")
    val listIsShowInterval: Boolean = true,

    @SerialName("list.isSaveScrollLocation")
    val listIsSaveScrollLocation: Boolean = true,

    @SerialName("list.addMusicLocationType")
    val listAddMusicLocationType: String = "top",  // "top" | "bottom"

    // === Download ===
    @SerialName("download.fileName")
    val downloadFileName: String = "歌名 - 歌手",  // "歌名 - 歌手" | "歌手 - 歌名" | "歌名"

    // === Sync ===
    @SerialName("sync.enable")
    val syncEnable: Boolean = false
) {
    companion object {
        /** Create default settings (matching the original app's defaults) */
        fun defaults(): AppSetting = AppSetting()
    }
}
