package cn.guoyujie666.music.compose.core.model

import kotlinx.serialization.Serializable

// === Source Types ===

typealias OnlineSource = String  // "kw" | "kg" | "tx" | "wy" | "mg"
typealias Source = String       // OnlineSource | "local" | "all" | custom user_api_*

object KnownSources {
    const val KW = "kw"
    const val KG = "kg"
    const val TX = "tx"
    const val WY = "wy"
    const val MG = "mg"
    const val BD = "bd"
    const val XM = "xm"
    const val LOCAL = "local"
    const val ALL = "all"

    val allOnline: List<OnlineSource> = listOf(KW, KG, TX, WY, MG)
    val all: List<Source> = allOnline + LOCAL
}

// === Quality Types ===
typealias Quality = String

object KnownQualities {
    const val _128K = "128k"
    const val _192K = "192k"
    const val _320K = "320k"
    const val FLAC = "flac"
    const val FLAC_24BIT = "flac24bit"
    const val APE = "ape"
    const val WAV = "wav"

    val all: List<Quality> = listOf(_128K, _192K, _320K, FLAC, FLAC_24BIT, APE, WAV)
}

typealias QualityList = Map<Source, List<Quality>>

// === Share Type ===
typealias ShareType = String  // "system" | "clipboard"

// === Add Music Location Type ===
typealias AddMusicLocationType = String  // "top" | "bottom"

// === Version Info ===
@Serializable
data class VersionInfo(
    val version: String,
    val desc: String
)

// === Update Status ===
typealias UpdateStatus = String  // "downloaded" | "downloading" | "error" | "checking" | "idle"

// === Nav IDs ===
typealias NavId = String  // "nav_search" | "nav_songlist" | "nav_top" | "nav_love" | "nav_setting"

object NavIds {
    const val SEARCH = "nav_search"
    const val SONGLIST = "nav_songlist"
    const val LEADERBOARD = "nav_top"
    const val FAVORITES = "nav_love"
    const val SETTINGS = "nav_setting"

    val all: List<NavId> = listOf(SEARCH, SONGLIST, LEADERBOARD, FAVORITES, SETTINGS)
}

// === Music Toggle Mode ===
typealias MusicToggleMode = String

object MusicToggleModes {
    const val LIST_LOOP = "listLoop"
    const val RANDOM = "random"
    const val LIST = "list"
    const val SINGLE_LOOP = "singleLoop"
    const val NONE = "none"

    val all: List<String> = listOf(LIST_LOOP, RANDOM, LIST, SINGLE_LOOP, NONE)

    fun next(current: String): String {
        val idx = all.indexOf(current).takeIf { it >= 0 } ?: 0
        return all[(idx + 1) % all.size]
    }

    fun label(mode: String): String = when (mode) {
        LIST_LOOP -> "列表循环"
        RANDOM -> "随机播放"
        LIST -> "顺序播放"
        SINGLE_LOOP -> "单曲循环"
        NONE -> "禁用切换"
        else -> mode
    }
}

// === Component IDs ===
enum class ComponentId {
    home,
    playDetail,
    songlistDetail,
    comment
}

// === List IDs ===
object ListIds {
    const val DEFAULT = "default"
    const val LOVE = "love"
    const val TEMP = "temp"
    const val DOWNLOAD = "download"
    const val PLAY_LATER = "play_later"
}
