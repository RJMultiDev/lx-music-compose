package cn.guoyujie666.music.compose.core.music

import cn.guoyujie666.music.compose.core.model.LyricInfo
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.model.MusicQualityType
import cn.guoyujie666.music.compose.core.model.OnlineSource
import cn.guoyujie666.music.compose.core.model.Quality
import cn.guoyujie666.music.compose.core.model.UserApiInfo

/**
 * Unified interface for all music data sources.
 *
 * Each music platform (kw, kg, tx, wy, mg, etc.) implements this interface.
 * Ported from the JavaScript music SDK pattern in src/utils/musicSdk/.
 * All methods are suspend functions returning Result types.
 */

// === Search Results ===

data class SearchResult(
    val list: List<MusicInfo> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 30
)

data class TipSearchResult(
    val list: List<String> = emptyList()
)

// === Song List Types ===

data class SongListTag(
    val name: String,
    val id: String,
    val children: List<SongListTag> = emptyList()
)

data class SongListSortInfo(
    val name: String,
    val id: String
)

data class SongListItem(
    val id: String,
    val name: String,
    val pic: String? = null,
    val playCount: Long = 0,
    val author: String = "",
    val desc: String = "",
    val source: String = ""
)

// === Song List Detail Result ===

data class SongListDetailResult(
    val list: List<MusicInfo> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 100,
    val info: SongListDetailInfo = SongListDetailInfo()
)

data class SongListDetailInfo(
    val name: String = "",
    val pic: String? = null,
    val desc: String = "",
    val author: String = "",
    val playCount: Long = 0
)

// === Leaderboard Types ===

data class LeaderboardItem(
    val id: String,
    val name: String,
    val pic: String? = null
)

// === Comment Types ===

data class CommentItem(
    val id: String,
    val userName: String,
    val avatar: String? = null,
    val content: String = "",
    val time: String = "",
    val likes: Int = 0
)

data class CommentResult(
    val list: List<CommentItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val hasMore: Boolean = false
)

// === Music Source Interface ===

interface MusicSource {
    /** Unique source identifier (e.g., "kw", "kg", "tx") */
    val sourceId: OnlineSource

    /** Human-readable source name */
    val sourceName: String

    /** Whether this source is currently enabled */
    val isEnabled: Boolean

    // --- Music Search ---

    /** Search for songs by keyword */
    suspend fun searchMusic(keyword: String, page: Int = 1, limit: Int = 30): Result<SearchResult>

    /** Get search suggestions/autocomplete */
    suspend fun tipSearch(keyword: String): Result<TipSearchResult>

    /** Get hot search terms */
    suspend fun hotSearch(): Result<List<String>>

    // --- Song List Browse ---

    /** Get available genre tags */
    suspend fun getSongListTags(): Result<List<SongListTag>>

    /** Browse song lists by sort and tag */
    suspend fun getSongList(sortId: String, tagId: String = "", page: Int = 1): Result<List<SongListItem>>

    /** Get songs in a specific song list */
    suspend fun getSongListDetail(listId: String, page: Int = 1, limit: Int = 100): Result<SongListDetailResult>

    /** Search song lists */
    suspend fun searchSongList(keyword: String, page: Int = 1): Result<List<SongListItem>>

    // --- Leaderboard ---

    /** Get available leaderboards */
    suspend fun getLeaderboards(): Result<List<LeaderboardItem>>

    /** Get songs in a leaderboard */
    suspend fun getLeaderboardDetail(boardId: String, page: Int = 1, limit: Int = 100): Result<SearchResult>

    // --- Music Playback ---

    /** Get playable URL for a song at the specified quality */
    suspend fun getMusicUrl(musicInfo: MusicInfo, quality: Quality): Result<String>

    /** Get lyrics for a song */
    suspend fun getLyric(musicInfo: MusicInfo): Result<LyricInfo>

    /** Get album art URL for a song */
    suspend fun getPic(musicInfo: MusicInfo): Result<String>

    // --- Comments ---

    /** Get hot comments for a song */
    suspend fun getHotComments(
        musicInfo: MusicInfo,
        page: Int = 1,
        limit: Int = 20
    ): Result<CommentResult>

    /** Get new comments for a song */
    suspend fun getNewComments(
        musicInfo: MusicInfo,
        page: Int = 1,
        limit: Int = 20
    ): Result<CommentResult>

    // --- Capabilities ---

    /** Supported audio qualities */
    val supportedQualities: List<Quality>

    /** Whether this source supports song list browsing */
    val supportsSongList: Boolean

    /** Whether this source supports leaderboards */
    val supportsLeaderboard: Boolean

    /** Whether this source supports comments */
    val supportsComments: Boolean
}

// === User API Source (QuickJS-backed) ===

/**
 * A music source backed by a user-defined JavaScript script running in QuickJS.
 * The script is loaded via QuickJsEngine and implements the music source API.
 */
interface UserApiSource : MusicSource {
    val apiInfo: UserApiInfo

    /** Load/initialize the script */
    suspend fun initialize(): Result<Unit>

    /** Send a custom action to the script */
    suspend fun sendAction(action: String, data: String): Result<Boolean>

    /** Destroy the script engine */
    fun destroy()
}
