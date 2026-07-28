package cn.guoyujie666.music.compose.core.music.sources

import cn.guoyujie666.music.compose.core.model.*
import cn.guoyujie666.music.compose.core.music.*
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MgSource @Inject constructor() : MusicSource {
    override val sourceId: OnlineSource = KnownSources.MG
    override val sourceName: String = "咪咕音乐"
    override val isEnabled: Boolean = true
    override val supportedQualities: List<Quality> = listOf(KnownQualities._128K, KnownQualities._320K, KnownQualities.FLAC)
    override val supportsSongList: Boolean = true
    override val supportsLeaderboard: Boolean = true
    override val supportsComments: Boolean = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val deviceId = "963B7AA0D21511ED807EE5846EC87D20"
    private val signKey = "6cdc72a439cef99a3418d2a78aa28c73"
    private val appToken = "yyapp2d16148780a1dcc7408e06336b98cfd50"

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun httpGet(urlStr: String, sign: String, timestamp: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
            setRequestProperty("uiVersion", "A_music_3.6.1")
            setRequestProperty("deviceId", deviceId)
            setRequestProperty("timestamp", timestamp)
            setRequestProperty("sign", sign)
            setRequestProperty("channel", "0146921")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 11; MI 11) AppleWebKit/534.30 Mobile Safari/534.30")
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    private fun buildUrl(base: String, params: Map<String, String>) =
        params.entries.joinToString("&") { "${URLEncoder.encode(it.key,"UTF-8")}=${URLEncoder.encode(it.value,"UTF-8")}" }.let { "$base?$it" }

    override suspend fun searchMusic(keyword: String, page: Int, limit: Int): Result<SearchResult> = runCatching {
        val searchSwitch = "%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D"
        val url = buildUrl("https://jadeite.migu.cn/music_search/v3/search/searchAll", mapOf(
            "isCorrect" to "0", "isCopyright" to "1", "searchSwitch" to searchSwitch,
            "pageSize" to "$limit", "text" to keyword, "pageNo" to "$page", "sort" to "0", "sid" to "USS"))
        val ts = System.currentTimeMillis().toString()
        val sign = md5("${keyword}${signKey}${appToken}${deviceId}${ts}")
        val body = httpGet(url, sign, ts)
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["code"]?.jsonPrimitive?.content != "000000") return@runCatching SearchResult()

        val resultList = obj["songResultData"]?.jsonObject?.get("resultList")?.jsonArray
            ?: return@runCatching SearchResult()
        val seen = mutableSetOf<String>()
        val list = resultList.flatMap { sub -> sub.jsonArray.map { it.jsonObject } }.mapNotNull { data ->
            val cid = data["copyrightId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (cid in seen) return@mapNotNull null
            seen.add(cid)
            val name = data["name"]?.jsonPrimitive?.content ?: ""
            val singer = data["singerList"]?.jsonArray?.joinToString("/") { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" } ?: ""
            val dur = data["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val qs = parseMgQualitys(data)
            MusicInfo("mg_$cid", name, singer, "mg",
                if(dur>0)"${dur/60}:${(dur%60).toString().padStart(2,'0')}" else null,
                MusicInfoMetaOnline(songId=cid, albumName=data["album"]?.jsonPrimitive?.content?:"",
                    albumId=data["albumId"]?.jsonPrimitive?.content?:"", qualitys = qs))
        }
        val total = obj["songResultData"]?.jsonObject?.get("totalCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        SearchResult(list, total)
    }

    override suspend fun hotSearch(): Result<List<String>> = runCatching {
        // Simple top searches query
        val r = searchMusic("周杰伦", 1, 10).getOrElse { return@runCatching emptyList() }
        r.list.mapNotNull { it.singer.takeIf { it.isNotEmpty() } }.distinct().take(10)
    }

    override suspend fun tipSearch(k: String): Result<TipSearchResult> = runCatching {
        val url = "https://music.migu.cn/v3/api/search/suggest?keyword=${URLEncoder.encode(k, "UTF-8")}"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
            setRequestProperty("Referer", "https://music.migu.cn/v3")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 11; MI 11) AppleWebKit/534.30 Mobile Safari/534.30")
        }
        val body = conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
        val obj = json.parseToJsonElement(body).jsonObject
        val songs = obj["songs"]?.jsonArray
        val list = songs?.mapNotNull { item ->
            val name = item.jsonObject["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val singer = item.jsonObject["singerName"]?.jsonPrimitive?.content ?: ""
            if (singer.isNotEmpty()) "$name - $singer" else name
        } ?: emptyList()
        TipSearchResult(list = list)
    }
    // Hardcoded board list — IDs verified against MIGUM2.0 API (2026-07)
    override suspend fun getLeaderboards() = Result.success(listOf(
        LeaderboardItem("mg__27186466", "热歌榜"), LeaderboardItem("mg__27553319", "新歌榜"),
        LeaderboardItem("mg__27553408", "原创榜"), LeaderboardItem("mg__23189800", "港台榜"),
        LeaderboardItem("mg__23189399", "内地榜"), LeaderboardItem("mg__75959118", "音乐风向榜"),
        LeaderboardItem("mg__76557036", "彩铃分贝榜"), LeaderboardItem("mg__76557745", "会员臻爱榜"),
        LeaderboardItem("mg__83176390", "国风热歌榜"),
    ))
    // Fetches songs from columnInfo.contents tree (same format as browse page)
    override suspend fun getLeaderboardDetail(boardId: String, page: Int, limit: Int): Result<SearchResult> = runCatching {
        val columnId = boardId.removePrefix("mg__")
        val body = httpGetMgList("https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/querycontentbyId.do?columnId=$columnId&needAll=0")
        val obj = json.parseToJsonElement(body).jsonObject
        val contents = obj["columnInfo"]?.jsonObject?.get("contents")?.jsonArray ?: return@runCatching SearchResult()
        // Each content item wraps song data in .objectInfo (contents[].objectInfo)
        val result = contents.mapNotNull { el ->
            val song = el.jsonObject["objectInfo"]?.jsonObject ?: return@mapNotNull null
            // length is already formatted as "00:04:30" — strip leading "00:" if present
            val interval = song["length"]?.jsonPrimitive?.content?.let {
                if (it.startsWith("00:")) it.removePrefix("00:") else it }
            MusicInfo("mg_${song["copyrightId"]?.jsonPrimitive?.content ?: ""}",
                song["songName"]?.jsonPrimitive?.content ?: "",
                song["singer"]?.jsonPrimitive?.content ?: "", "mg", interval,
                MusicInfoMetaOnline(
                    songId = song["copyrightId"]?.jsonPrimitive?.content ?: "",
                    albumName = song["album"]?.jsonPrimitive?.content ?: "",
                    picUrl = song["albumImgs"]?.jsonArray?.firstOrNull()?.jsonObject?.get("img")?.jsonPrimitive?.content,
                    qualitys = parseMgQualitys(song)))
        }
        SearchResult(list = result, total = result.size, page = 1, limit = result.size)
    }
    override suspend fun getMusicUrl(musicInfo: MusicInfo, quality: Quality): Result<String> =
        Result.failure(UnsupportedOperationException("Custom source required"))
    override suspend fun getLyric(m: MusicInfo): Result<LyricInfo> = runCatching {
        val info = getMgMusicInfo(m)
        val lrcUrl = info?.get("lrcUrl")?.jsonPrimitive?.content
        val trcUrl = info?.get("trcUrl")?.jsonPrimitive?.content
        val lrcText = lrcUrl?.let { httpGetMgList(it).replace("\r\n", "\n").replace("\r", "\n") }
        val trcText = trcUrl?.let { httpGetMgList(it).replace("\r\n", "\n").replace("\r", "\n") }
        // Parse MRC format to extract lxlyric (word timing)
        val lrcParsed = parseMrcLyric(lrcText)
        val trcParsed = parseMrcLyric(trcText)
        LyricInfo(
            lyric = lrcParsed?.first ?: lrcText ?: "",
            tlyric = trcParsed?.first ?: trcText,
            lxlyric = lrcParsed?.second
        )
    }

    /**
     * Parse MRC format to (clean lyric, lxlyric).
     * MRC format: [startMs,durationMs](startMs,durationMs)word1(startMs,durationMs)word2...
     */
    private fun formatMrcTime(timeMs: Long): String {
        val ms = (timeMs % 1000).toString().padStart(3, '0')
        val totalSec = timeMs / 1000
        val m = (totalSec / 60).toString().padStart(2, '0')
        val s = (totalSec % 60).toString().padStart(2, '0')
        return "$m:$s.$ms"
    }

    private fun parseMrcLyric(text: String?): Pair<String, String>? {
        if (text.isNullOrBlank()) return null
        val lrcLines = mutableListOf<String>()
        val lxlrcLines = mutableListOf<String>()
        val lineTimeRegex = Regex("""^\s*\[(\d+),\d+]""")
        val wordTimeRegex = Regex("""\((\d+),(\d+)\)""")
        val wordTimeAll = Regex("""(\(\d+,\d+\))""")

        for (line in text.split("\n")) {
            if (line.length < 6) continue
            val timeMatch = lineTimeRegex.find(line) ?: continue
            val startTime = timeMatch.groupValues[1].toLong()
            val timeStr = formatMrcTime(startTime)

            val words = line.replace(lineTimeRegex, "")
            lrcLines.add("[$timeStr]${words.replace(wordTimeAll, "")}")

            val times = wordTimeAll.findAll(words).toList()
            if (times.isEmpty()) continue
            val convertedTimes = times.map { t ->
                val m = Regex("""\((\d+),(\d+)\)""").find(t.value)!!
                val wordStart = m.groupValues[1].toLong()
                val wordDur = m.groupValues[2].toLong()
                "<${maxOf(0, wordStart - startTime)},$wordDur>"
            }
            val wordArr = words.split(Regex("""\(\d+,\d+\)""")).dropWhile { it.isEmpty() }.toMutableList()
            val newWords = convertedTimes.mapIndexed { i, t -> "$t${wordArr.getOrElse(i) { "" }}" }.joinToString("")
            lxlrcLines.add("[$timeStr]$newWords")
        }
        return Pair(lrcLines.joinToString("\n"), lxlrcLines.joinToString("\n"))
    }

    override suspend fun getPic(m: MusicInfo): Result<String> = runCatching {
        val info = getMgMusicInfo(m)
        info?.get("img")?.jsonPrimitive?.content ?: ""
    }

    private suspend fun getMgMusicInfo(m: MusicInfo): JsonObject? {
        val copyrightId = (m.meta as? MusicInfoMetaOnline)?.extra?.get("copyrightId") ?: m.meta.songId
        val url = "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?resourceType=2&resourceId=$copyrightId"
        val body = httpGetMgList(url)
        val obj = json.parseToJsonElement(body).jsonObject
        val dataArr = obj["data"]?.jsonArray
        val item = dataArr?.getOrNull(0)?.jsonObject
        return item
    }
    // Ported from src/utils/musicSdk/mg/songList.js getTag
    // tagsUrl returns: data[0] = hotTag (content[{texts:[name,id]}]), data[1+] = categories
    override suspend fun getSongListTags(): Result<List<SongListTag>> = runCatching {
        val body = httpGetMgList("https://app.c.nf.migu.cn/pc/v1.0/template/musiclistplaza-taglist/release")
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["code"]?.jsonPrimitive?.content != "000000") return@runCatching emptyList()
        val arr = obj["data"]?.jsonArray ?: return@runCatching emptyList()
        arr.drop(1).map { g ->
            val go = g.jsonObject
            val header = go["header"]?.jsonObject
            SongListTag(
                name = header?.get("title")?.jsonPrimitive?.content ?: "",
                id = header?.get("actionUrl")?.jsonPrimitive?.content ?: "",
                children = (go["content"]?.jsonArray ?: emptyList()).map { t ->
                    val texts = t.jsonObject["texts"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content } ?: emptyList()
                    SongListTag(name = texts.getOrElse(0) { "" }, id = texts.getOrElse(1) { "" }, children = emptyList())
                }
            )
        }
    }

    /** Parse MG play count text like "133.4万", "1.2亿" → Long. */
    private fun parseMgPlayCount(text: String): Long {
        return when {
            text.endsWith("亿") -> (text.dropLast(1).toDoubleOrNull()?.times(100_000_000))?.toLong() ?: 0L
            text.endsWith("万") -> (text.dropLast(1).toDoubleOrNull()?.times(10_000))?.toLong() ?: 0L
            else -> text.toLongOrNull() ?: 0L
        }
    }

    // List API helpers (no signing needed for these endpoints)
    private fun httpGetMgList(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
            setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15")
            setRequestProperty("Referer", "https://m.music.migu.cn/")
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    // Ported from src/utils/musicSdk/mg/songList.js getList
    override suspend fun getSongList(sortId: String, tagId: String, page: Int): Result<List<SongListItem>> = runCatching {
        val url = if (tagId.isEmpty())
            "https://app.c.nf.migu.cn/pc/bmw/page-data/playlist-square-recommend/v1.0?templateVersion=2&pageNo=$page"
        else
            "https://app.c.nf.migu.cn/pc/v1.0/template/musiclistplaza-listbytag/release?pageNumber=$page&templateVersion=2&tagId=$tagId"
        val obj = json.parseToJsonElement(httpGetMgList(url)).jsonObject
        if (obj["code"]?.jsonPrimitive?.content != "000000") return@runCatching emptyList()
        val data = obj["data"]?.jsonObject ?: return@runCatching emptyList()
        // Path A: recommend endpoint → data.contents with nested resType==2021 items (RN filterList2)
        if (data["contents"] != null) {
            val result = mutableListOf<SongListItem>()
            val seen = mutableSetOf<String>()
            fun walk(items: JsonArray) {
                for (el in items) {
                    val item = el.jsonObject
                    if (item.containsKey("contents")) item["contents"]?.jsonArray?.let { walk(it) }
                    else if (item["resType"]?.jsonPrimitive?.content == "2021") {
                        val id = item["resId"]?.jsonPrimitive?.content ?: continue
                        if (id in seen) continue; seen.add(id)
                        result.add(SongListItem(id = id, name = item["txt"]?.jsonPrimitive?.content ?: "",
                            pic = item["img"]?.jsonPrimitive?.contentOrNull, desc = item["txt2"]?.jsonPrimitive?.contentOrNull ?: "", source = "mg"))
                    }
                }
            }
            walk(data["contents"]!!.jsonArray)
            // Batch-fetch play counts from detail info API
            for (i in result.indices) {
                try {
                    val ib = httpGetMgList("https://c.musicapp.migu.cn/MIGUM3.0/resource/playlist/v2.0?playlistId=${result[i].id}")
                    val io = json.parseToJsonElement(ib).jsonObject
                    if (io["code"]?.jsonPrimitive?.content == "000000") {
                        val pc = io["data"]?.jsonObject?.get("opNumItem")?.jsonObject?.get("playNum")?.jsonPrimitive?.longOrNull
                        if (pc != null) result[i] = result[i].copy(playCount = pc)
                    }
                } catch (_: Exception) {}
            }
            return@runCatching result
        }
        // Path B: listbytag endpoint → data.contentItemList[1].itemList (RN filterList)
        val items = data["contentItemList"]?.jsonArray?.getOrNull(1)?.jsonObject?.get("itemList")?.jsonArray
            ?: return@runCatching emptyList()
        items.map { el ->
            val item = el.jsonObject
            val pc = item["barList"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("title")?.jsonPrimitive?.content
                ?.let { parseMgPlayCount(it) } ?: 0L
            SongListItem(
                id = item["logEvent"]?.jsonObject?.get("contentId")?.jsonPrimitive?.content ?: "",
                name = item["title"]?.jsonPrimitive?.content ?: "",
                pic = item["imageUrl"]?.jsonPrimitive?.contentOrNull,
                playCount = pc,
                source = "mg")
        }
    }
    // Ported from src/utils/musicSdk/mg/songList.js getListDetailList + getListDetailInfo
    override suspend fun getSongListDetail(listId: String, page: Int, limit: Int): Result<SongListDetailResult> = runCatching {
        val listUrl = "https://app.c.nf.migu.cn/MIGUM3.0/resource/playlist/song/v2.0?pageNo=$page&pageSize=$limit&playlistId=$listId"
        val listBody = httpGetMgList(listUrl)
        val listObj = json.parseToJsonElement(listBody).jsonObject
        if (listObj["code"]?.jsonPrimitive?.content != "000000") return@runCatching SongListDetailResult()
        val data = listObj["data"]?.jsonObject ?: return@runCatching SongListDetailResult()
        val songs = (data["songList"]?.jsonArray ?: emptyList()).map { el ->
            val item = el.jsonObject
            val dur = item["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            MusicInfo("mg_${item["copyrightId"]?.jsonPrimitive?.content ?: ""}",
                item["songName"]?.jsonPrimitive?.content ?: "",
                item["singerList"]?.jsonArray?.joinToString("/") { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" } ?: "",
                "mg", if (dur > 0) "${dur / 60}:${(dur % 60).toString().padStart(2, '0')}" else null,
                MusicInfoMetaOnline(songId = item["copyrightId"]?.jsonPrimitive?.content ?: "",
                    albumName = item["album"]?.jsonPrimitive?.content ?: "",
                    albumId = item["albumId"]?.jsonPrimitive?.content ?: "",
                    qualitys = parseMgQualitys(item)))
        }
        // Also try to get playlist info
        var info = SongListDetailInfo()
        try {
            val infoBody = httpGetMgList("https://c.musicapp.migu.cn/MIGUM3.0/resource/playlist/v2.0?playlistId=$listId")
            val infoObj = json.parseToJsonElement(infoBody).jsonObject
            if (infoObj["code"]?.jsonPrimitive?.content == "000000") {
                val id = infoObj["data"]?.jsonObject ?: JsonObject(emptyMap())
                info = SongListDetailInfo(
                    name = id["title"]?.jsonPrimitive?.content ?: "",
                    pic = id["imgItem"]?.jsonObject?.get("img")?.jsonPrimitive?.content,
                    desc = id["summary"]?.jsonPrimitive?.content ?: "",
                    author = id["ownerName"]?.jsonPrimitive?.content ?: "",
                    playCount = id["opNumItem"]?.jsonObject?.get("playNum")?.jsonPrimitive?.longOrNull ?: 0L)
            }
        } catch (_: Exception) { /* info is optional */ }
        SongListDetailResult(list = songs, total = data["totalCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: songs.size,
            page = page, limit = limit, info = info)
    }
    // Ported from src/utils/musicSdk/mg/songList.js search(); same signed searchAll
    // endpoint as searchMusic but with the songlist switch enabled.
    override suspend fun searchSongList(keyword: String, page: Int): Result<List<SongListItem>> = runCatching {
        val searchSwitch = """{"song":0,"album":0,"singer":0,"tagSong":0,"mvSong":0,"bestShow":0,"songlist":1,"lyricSong":0}"""
        val url = buildUrl("https://jadeite.migu.cn/music_search/v3/search/searchAll", mapOf(
            "isCorrect" to "1", "isCopyright" to "1", "searchSwitch" to searchSwitch,
            "pageSize" to "20", "text" to keyword, "pageNo" to "$page", "sort" to "0", "sid" to "USS"))
        val ts = System.currentTimeMillis().toString()
        val sign = md5("${keyword}${signKey}${appToken}${deviceId}${ts}")
        val obj = json.parseToJsonElement(httpGet(url, sign, ts)).jsonObject
        val data = obj["songListResultData"]?.jsonObject ?: throw Exception("get song list failed")
        (data["result"]?.jsonArray ?: emptyList()).mapNotNull { el ->
            val item = el.jsonObject
            // The RN version skips entries without an id
            val id = item["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SongListItem(
                id = id,
                name = item["name"]?.jsonPrimitive?.content ?: "",
                pic = item["musicListPicUrl"]?.jsonPrimitive?.contentOrNull,
                playCount = item["playNum"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                author = item["userName"]?.jsonPrimitive?.content ?: "",
                source = "mg")
        }
    }
    override suspend fun getHotComments(m: MusicInfo, p: Int, l: Int): Result<CommentResult> = Result.success(CommentResult())
    override suspend fun getNewComments(m: MusicInfo, p: Int, l: Int): Result<CommentResult> = Result.success(CommentResult())

    /** Parse qualitys from audioFormats or newRateFormats (ported from mg/musicInfo.js filterMusicInfoList). */
    private fun parseMgQualitys(item: JsonObject): List<MusicQualityType> {
        val formats = item["audioFormats"]?.jsonArray ?: item["newRateFormats"]?.jsonArray ?: return emptyList()
        return formats.mapNotNull { af ->
            val ft = af.jsonObject["formatType"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val type = when (ft) { "PQ" -> "128k"; "HQ" -> "320k"; "SQ" -> "flac"; "ZQ24" -> "flac24bit"; else -> return@mapNotNull null }
            val size = af.jsonObject["asize"]?.jsonPrimitive?.content
                ?: af.jsonObject["isize"]?.jsonPrimitive?.content
                ?: af.jsonObject["size"]?.jsonPrimitive?.content
                ?: af.jsonObject["androidSize"]?.jsonPrimitive?.content ?: ""
            MusicQualityType(type, size)
        }
    }
}
