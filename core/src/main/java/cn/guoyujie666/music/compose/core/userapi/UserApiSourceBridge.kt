package cn.guoyujie666.music.compose.core.userapi

import cn.guoyujie666.music.compose.core.model.KnownSources
import cn.guoyujie666.music.compose.core.model.LyricInfo
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.model.OnlineSource
import cn.guoyujie666.music.compose.core.model.Quality
import cn.guoyujie666.music.compose.core.model.UserApiInfo
import cn.guoyujie666.music.compose.core.model.UserApiSourceInfo
import cn.guoyujie666.music.compose.core.music.CommentItem
import cn.guoyujie666.music.compose.core.music.CommentResult
import cn.guoyujie666.music.compose.core.music.LeaderboardItem
import cn.guoyujie666.music.compose.core.music.SearchResult
import cn.guoyujie666.music.compose.core.music.SongListDetailResult
import cn.guoyujie666.music.compose.core.music.SongListItem
import cn.guoyujie666.music.compose.core.music.SongListTag
import cn.guoyujie666.music.compose.core.music.TipSearchResult
import cn.guoyujie666.music.compose.core.music.UserApiSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Bridges a QuickJS-backed user API script into the MusicSource interface.
 *
 * Protocol (ported from RN's preload script):
 * - Native → Script: sendAction("request", { requestKey, data: { source, action, info } })
 * - Script processes via lx.on('request', handler) → returns URL/lyric/pic
 * - Script → Native: nativeCall("response", { requestKey, status, result })
 *
 * The preload script's jsCall() only handles actions: "request", "response",
 * "__run_error__", "__set_timeout__". All music queries must use "request".
 */
class UserApiSourceBridge(
    override val sourceId: OnlineSource,
    override val sourceName: String,
    private val sourceInfo: UserApiSourceInfo,
    override val apiInfo: UserApiInfo,
    private val apiId: String,
    private val sendRawAction: (String, String) -> Boolean
) : UserApiSource {

    override val isEnabled: Boolean = true
    override val supportedQualities: List<Quality> = sourceInfo.qualitys
    override val supportsSongList: Boolean = false
    override val supportsLeaderboard: Boolean = false
    override val supportsComments: Boolean = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Store pending requests for response mapping
    private val pendingRequests = mutableMapOf<String, kotlinx.coroutines.CompletableDeferred<String>>()
    private var requestCounter = 0L

    // === MusicSearch (stubs — user APIs typically only handle musicUrl/lyric/pic) ===
    override suspend fun searchMusic(keyword: String, page: Int, limit: Int): Result<SearchResult> =
        Result.success(SearchResult())
    override suspend fun tipSearch(keyword: String): Result<TipSearchResult> =
        Result.success(TipSearchResult())
    override suspend fun hotSearch(): Result<List<String>> =
        Result.success(emptyList())
    override suspend fun getSongListTags(): Result<List<SongListTag>> =
        Result.success(emptyList())
    override suspend fun getSongList(sortId: String, tagId: String, page: Int): Result<List<SongListItem>> =
        Result.success(emptyList())
    override suspend fun getSongListDetail(listId: String, page: Int, limit: Int): Result<SongListDetailResult> =
        Result.success(SongListDetailResult())
    override suspend fun searchSongList(keyword: String, page: Int): Result<List<SongListItem>> =
        Result.success(emptyList())
    override suspend fun getLeaderboards(): Result<List<LeaderboardItem>> =
        Result.success(emptyList())
    override suspend fun getLeaderboardDetail(boardId: String, page: Int, limit: Int): Result<SearchResult> =
        Result.success(SearchResult())

    // === Core Music Operations ===

    /**
     * Get music URL from the script.
     * Sends "request" action with { source, action: "musicUrl", info: { type, ...musicInfo } }
     */
    override suspend fun getMusicUrl(musicInfo: MusicInfo, quality: Quality): Result<String> {
        return sendRequest("musicUrl", musicInfo.source, mapOf(
            "type" to quality,
            "musicInfo" to buildMusicInfoJson(musicInfo)
        )) { responseObj ->
            responseObj["url"]?.jsonPrimitive?.content ?: ""
        }
    }

    override suspend fun getLyric(musicInfo: MusicInfo): Result<LyricInfo> {
        if ("lyric" !in sourceInfo.actions) return Result.failure(UnsupportedOperationException("Not supported"))
        return sendRequest("lyric", musicInfo.source, mapOf(
            "musicInfo" to buildMusicInfoJson(musicInfo)
        )) { responseObj ->
            LyricInfo(
                lyric = responseObj["lyric"]?.jsonPrimitive?.content ?: "",
                tlyric = responseObj["tlyric"]?.jsonPrimitive?.content,
                rlyric = responseObj["rlyric"]?.jsonPrimitive?.content
            )
        }
    }

    override suspend fun getPic(musicInfo: MusicInfo): Result<String> {
        if ("pic" !in sourceInfo.actions) return Result.failure(UnsupportedOperationException("Not supported"))
        return sendRequest("pic", musicInfo.source, mapOf(
            "musicInfo" to buildMusicInfoJson(musicInfo)
        )) { responseObj ->
            responseObj["url"]?.jsonPrimitive?.content ?: ""
        }
    }

    override suspend fun getHotComments(musicInfo: MusicInfo, page: Int, limit: Int): Result<CommentResult> =
        Result.success(CommentResult())
    override suspend fun getNewComments(musicInfo: MusicInfo, page: Int, limit: Int): Result<CommentResult> =
        Result.success(CommentResult())

    // === QuickJS-specific ===

    override suspend fun initialize(): Result<Unit> {
        return Result.success(Unit) // Init handled via lx.send('inited', ...) flow
    }

    override fun destroy() {
        pendingRequests.values.forEach { it.completeExceptionally(Exception("Script destroyed")) }
        pendingRequests.clear()
    }

    override suspend fun sendAction(action: String, data: String): Result<Boolean> {
        return try {
            Result.success(sendRawAction(action, data))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Called from UserApiManager when the script sends a "response" action.
     * Completes the matching pending request.
     */
    fun onScriptResponse(reqKey: String, data: String) {
        if (reqKey.isNotEmpty()) {
            pendingRequests[reqKey]?.complete(data)
            return
        }
        // Only fallback if there's exactly one pending request (no ambiguity)
        if (pendingRequests.size == 1) {
            pendingRequests.values.first().complete(data)
        }
    }

    /**
     * Build a JSON representation of MusicInfo for the script.
     * MUST match the EXACT structure from RN's MusicInfoOnline for kw/wy sources.
     * RN reference: src/types/music.d.ts — MusicInfo_online_common
     *
     * Top-level: id, name, singer, source, interval, meta
     * meta: songId, albumName, picUrl?, qualitys[], _qualitys{}, albumId?, extra?
     *
     * NO hash fields for kw/wy — those are kg-specific.
     */
    private fun buildMusicInfoJson(musicInfo: MusicInfo): String {
        val meta = musicInfo.meta
        val metaJson = org.json.JSONObject().apply {
            put("songId", meta.songId)
            put("albumName", meta.albumName)
            meta.picUrl?.let { put("picUrl", it) }
            if (meta is cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline) {
                meta.albumId?.let { put("albumId", it) }
                if (meta.qualitys.isNotEmpty()) {
                    val arr = org.json.JSONArray()
                    meta.qualitys.forEach { q ->
                        arr.put(org.json.JSONObject().apply {
                            put("type", q.type)
                            q.size?.let { put("size", it) }
                            put("hash", meta.songId)
                        })
                    }
                    put("qualitys", arr)
                    val qMap = org.json.JSONObject()
                    meta.qualitys.forEach { q ->
                        qMap.put(q.type, org.json.JSONObject().apply {
                            q.size?.let { put("size", it) }
                            put("hash", meta.songId)  // Required by many scripts
                        })
                    }
                    put("_qualitys", qMap)
                }
                if (meta.extra.isNotEmpty()) {
                    val extraObj = org.json.JSONObject()
                    meta.extra.forEach { (k, v) -> extraObj.put(k, v) }
                    put("extra", extraObj)
                }
            }
        }

        val topJson = org.json.JSONObject().apply {
            put("id", musicInfo.id)
            put("name", musicInfo.name)
            put("singer", musicInfo.singer)
            put("source", musicInfo.source)
            musicInfo.interval?.let { put("interval", it) }
            // Old-style fields — many scripts access these directly
            put("songmid", meta.songId)   // kw/tx/wy use songmid
            // kg/mg use hash — prefer extra["hash"] if the source stored one
            val hashValue = if (meta is cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline) meta.extra["hash"] ?: meta.songId else meta.songId
            put("hash", hashValue)
            put("meta", metaJson)
            // Also expose extra fields at top level for direct JS access (e.g. strMediaMid, copyrightId)
            if (meta is cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline) {
                for (entry in meta.extra) {
                    if (!has(entry.key)) put(entry.key, entry.value)
                }
            }
        }
        return topJson.toString()
    }

    // === Private helpers ===

    /**
     * Send a request to the script and wait for response.
     * Uses the preload script's "request" action protocol:
     *   { requestKey, data: { source, action, info: { ... } } }
     */
    private suspend fun <T> sendRequest(
        action: String,
        source: String,
        info: Map<String, String>,
        timeoutMs: Long = 15000,
        parser: (kotlinx.serialization.json.JsonObject) -> T
    ): Result<T> {
        val requestKey = "req_${requestCounter++}"
        val deferred = kotlinx.coroutines.CompletableDeferred<String>()
        pendingRequests[requestKey] = deferred

        // Build info object — values starting with '{' or '[' are embedded as JSON objects/arrays
        val infoObj = org.json.JSONObject()
        info.forEach { (k, v) ->
            val trimmed = v.trim()
            infoObj.put(k, when {
                trimmed.startsWith("{") -> org.json.JSONObject(trimmed)
                trimmed.startsWith("[") -> org.json.JSONArray(trimmed)
                else -> v
            })
        }

        val payload = org.json.JSONObject().apply {
            put("requestKey", requestKey)
            put("data", org.json.JSONObject().apply {
                put("source", source)
                put("action", action)
                put("info", infoObj)
            })
        }.toString()

        sendRawAction("request", payload)

        return try {
            val rawResult = kotlinx.coroutines.withTimeout(timeoutMs) { deferred.await() }
            parseScriptResponse(rawResult, parser)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pendingRequests.remove(requestKey)
        }
    }

    /**
     * Parse the script's response.
     * Script sends: { requestKey, status: true/false, result: { source, action, data: ... } }
     */
    private fun <T> parseScriptResponse(
        raw: String,
        parser: (kotlinx.serialization.json.JsonObject) -> T
    ): Result<T> {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val status = root["status"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            if (!status) {
                val errorMsg = root["errorMessage"]?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() }
                    ?: "Script returned error (no details)"
                android.util.Log.e("UserApi", "Script response error: $errorMsg, raw=${raw.take(500)}")
                return Result.failure(Exception(errorMsg))
            }
            val result = root["result"]?.jsonObject
                ?: return Result.failure(Exception("Missing result in script response"))
            val data = result["data"]?.jsonObject
                ?: result["data"]?.jsonPrimitive?.content?.let { json.parseToJsonElement(it).jsonObject }

            if (data != null) {
                Result.success(parser(data))
            } else {
                // For pic action, result.data might be a direct string (URL)
                val directStr = result["data"]?.jsonPrimitive?.content
                if (directStr != null) {
                    // Wrap it so the parser can handle it
                    val wrapped = json.parseToJsonElement("""{"url":"$directStr"}""").jsonObject
                    Result.success(parser(wrapped))
                } else {
                    Result.failure(Exception("Cannot parse response data"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
