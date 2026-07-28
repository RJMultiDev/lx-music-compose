package cn.guoyujie666.music.compose.core.music.sources

import cn.guoyujie666.music.compose.core.model.*
import cn.guoyujie666.music.compose.core.music.*
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TxSource @Inject constructor() : MusicSource {
    override val sourceId: OnlineSource = KnownSources.TX
    override val sourceName: String = "QQ音乐"
    override val isEnabled: Boolean = true
    override val supportedQualities: List<Quality> = listOf(KnownQualities._128K, KnownQualities._320K, KnownQualities.FLAC, KnownQualities.FLAC_24BIT)
    override val supportsSongList: Boolean = true
    override val supportsLeaderboard: Boolean = true
    override val supportsComments: Boolean = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val compactJson = Json { encodeDefaults = true; prettyPrint = false }

    private fun httpPost(url: String, body: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "QQMusic 14090508(android 12)")
            outputStream.write(body.toByteArray())
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    private fun httpPostHotSearch(url: String, body: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "QQMusic 14090508(android 12)")
            setRequestProperty("Referer", "https://y.qq.com/portal/player.html")
            outputStream.write(body.toByteArray())
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    private fun httpGet(urlStr: String, headers: Map<String, String> = emptyMap()): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    private fun sha1(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun zzcSign(data: String): String {
        val hash = sha1(data)
        // NOTE: index 40 is intentionally out of range for the 40-char sha1 hex string.
        // The RN implementation (src/utils/musicSdk/tx/utils/crypto.js) relies on JS
        // returning undefined -> '' for out-of-range indexes, so part1 is actually only
        // 7 chars — and that is exactly the signature the server accepts. Replicate the
        // JS behavior with getOrNull instead of throwing StringIndexOutOfBoundsException.
        val part1 = listOf(23,14,6,36,16,40,7,19).mapNotNull { hash.getOrNull(it) }.joinToString("")
        val part2 = listOf(16,1,32,12,19,27,8,5).mapNotNull { hash.getOrNull(it) }.joinToString("")
        val xorVals = listOf(89,39,179,150,218,82,58,252,177,52,186,123,120,64,242,133,143,161,121,179)
        val xorBytes = ByteArray(20)
        for (i in 0 until 20) {
            val h = hash.substring(i*2, i*2+2).toInt(16)
            xorBytes[i] = (h xor xorVals[i]).toByte()
        }
        val b64 = Base64.getEncoder().encodeToString(xorBytes).replace("/","").replace("+","").replace("=","")
        return "zzc${part1}${b64}${part2}".lowercase()
    }

    /** Ported from src/utils/common.ts sizeFormate: bytes -> "3.53 MiB" */
    private fun sizeFormate(size: Long): String {
        if (size <= 0L) return "0 B"
        val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
        val idx = (Math.log(size.toDouble()) / Math.log(1024.0)).toInt().coerceIn(0, units.size - 1)
        return "%.2f %s".format(size.toDouble() / Math.pow(1024.0, idx.toDouble()), units[idx])
    }

    // Ported from src/utils/musicSdk/tx/musicSearch.js. Built via kotlinx JSON so the
    // keyword gets properly escaped; the signed string and the sent body are identical.
    private fun buildSearchPayload(keyword: String, page: Int, limit: Int): String {
        val searchId = (1..16).map { ('0'..'9').random() }.joinToString("")
        val payload = buildJsonObject {
            putJsonObject("comm") {
                put("ct", "11"); put("cv", "14090508"); put("v", "14090508"); put("tmeAppID", "qqmusic")
                put("phonetype", "EBG-AN10"); put("deviceScore", "553.47"); put("devicelevel", "50"); put("newdevicelevel", "20")
                put("rom", "HuaWei/EMOTION/EmotionUI_14.2.0"); put("os_ver", "12")
                put("OpenUDID", "0"); put("OpenUDID2", "0"); put("QIMEI36", "0"); put("udid", "0")
                put("chid", "0"); put("aid", "0"); put("oaid", "0"); put("taid", "0"); put("tid", "0"); put("wid", "0")
                put("uid", "0"); put("sid", "0"); put("modeSwitch", "6"); put("teenMode", "0")
                put("ui_mode", "2"); put("nettype", "1020"); put("v4ip", "")
            }
            putJsonObject("req") {
                put("module", "music.search.SearchCgiService")
                put("method", "DoSearchForQQMusicMobile")
                putJsonObject("param") {
                    put("search_type", 0)
                    put("searchid", searchId)
                    put("query", keyword)
                    put("page_num", page)
                    put("num_per_page", limit)
                    put("highlight", 0); put("nqc_flag", 0); put("multi_zhida", 0)
                    put("cat", 2); put("grp", 1); put("sin", 0); put("sem", 0)
                }
            }
        }
        return compactJson.encodeToString(JsonObject.serializer(), payload)
    }

    /** Ported from musicSearch.js handleResult */
    private fun parseSearchResult(data: JsonObject, page: Int, limit: Int): SearchResult {
        val items = data["body"]?.jsonObject?.get("item_song")?.jsonArray ?: JsonArray(emptyList())
        val list = items.mapNotNull { el ->
            val item = el.jsonObject
            val file = item["file"]?.jsonObject ?: return@mapNotNull null
            // The RN version skips entries without file.media_mid
            val mediaMid = file["media_mid"]?.jsonPrimitive?.content
            if (mediaMid.isNullOrEmpty()) return@mapNotNull null
            val mid = item["mid"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val title = item["title"]?.jsonPrimitive?.content ?: ""
            val singers = item["singer"]?.jsonArray?.map { it.jsonObject }
            val singer = HttpUtils.htmlDecode(singers.orEmpty()
                .mapNotNull { s -> s["name"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } }
                .joinToString("、"))
            val dur = item["interval"]?.jsonPrimitive?.intOrNull ?: 0
            val album = item["album"]?.jsonObject
            val albumName = album?.get("name")?.jsonPrimitive?.content ?: ""
            val albumId = album?.get("mid")?.jsonPrimitive?.content ?: ""

            // QQ search API returns quality sizes at song top level: size128, size320, sizeflac, sizeape
            val qs = parseTxQuality(item)

            val firstSingerMid = singers?.firstOrNull()?.get("mid")?.jsonPrimitive?.content
            val picUrl = if (albumId.isEmpty() || albumId == "空") {
                firstSingerMid?.let { "https://y.gtimg.cn/music/photo_new/T001R500x500M000$it.jpg" }
            } else {
                "https://y.gtimg.cn/music/photo_new/T002R500x500M000$albumId.jpg"
            }

            MusicInfo("tx_$mid", title, singer, "tx",
                if (dur > 0) "${dur / 60}:${(dur % 60).toString().padStart(2, '0')}" else null,
                MusicInfoMetaOnline(songId = mid, albumName = albumName, picUrl = picUrl,
                    qualitys = qs, albumId = albumId,
                    extra = mapOf("strMediaMid" to mediaMid, "albumMid" to albumId)))
        }
        val total = data["meta"]?.jsonObject?.get("estimate_sum")?.jsonPrimitive?.intOrNull ?: 0
        return SearchResult(list, total, page, limit)
    }

    override suspend fun searchMusic(keyword: String, page: Int, limit: Int): Result<SearchResult> = runCatching {
        // The search API intermittently answers with req.code != 0 (e.g. 2001) and an
        // empty body; retry like the RN version does (up to 5 retries).
        for (retryNum in 0..5) {
            val payload = buildSearchPayload(keyword, page, limit)
            val body = httpPost("https://u.y.qq.com/cgi-bin/musics.fcg?sign=${zzcSign(payload)}", payload)
            val obj = json.parseToJsonElement(body).jsonObject
            val req = obj["req"]?.jsonObject
            if (obj["code"]?.jsonPrimitive?.intOrNull != 0 || req?.get("code")?.jsonPrimitive?.intOrNull != 0) continue
            val data = req?.get("data")?.jsonObject ?: continue
            return@runCatching parseSearchResult(data, page, limit)
        }
        throw Exception("搜索失败")
    }

    override suspend fun hotSearch(): Result<List<String>> = runCatching {
        val payloadObj = buildJsonObject {
            putJsonObject("comm") {
                put("ct","19"); put("cv","1803"); put("guid","0"); put("patch","118")
                put("psrf_access_token_expiresAt", 0); put("psrf_qqaccess_token", ""); put("psrf_qqopenid", "")
                put("psrf_qqunionid", ""); put("tmeAppID", "qqmusic"); put("tmeLoginType", 0)
                put("uin","0"); put("wid","0")
            }
            putJsonObject("hotkey") {
                put("method", "GetHotkeyForQQMusicPC")
                put("module", "tencent_musicsoso_hotkey.HotkeyService")
                putJsonObject("param") { put("search_id", ""); put("uin", 0) }
            }
        }
        val payload = compactJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payloadObj)
        // Hot search uses musicu.fcg WITHOUT signing
        val o = json.parseToJsonElement(httpPostHotSearch("https://u.y.qq.com/cgi-bin/musicu.fcg", payload)).jsonObject
        o["hotkey"]?.jsonObject?.get("data")?.jsonObject?.get("vec_hotkey")?.jsonArray?.mapNotNull { it.jsonObject["query"]?.jsonPrimitive?.content }?:emptyList()
    }

    override suspend fun tipSearch(k: String): Result<TipSearchResult> = runCatching {
        val url = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?is_xml=0&format=json&key=${URLEncoder.encode(k, "UTF-8")}&loginUin=0&hostUin=0&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq&needNewCode=0"
        val body = httpGet(url, mapOf("Referer" to "https://y.qq.com/portal/player.html"))
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["code"]?.jsonPrimitive?.intOrNull != 0) return@runCatching TipSearchResult()
        val list = obj["data"]?.jsonObject?.get("song")?.jsonObject?.get("itemlist")?.jsonArray?.mapNotNull { item ->
            val name = item.jsonObject["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val singer = item.jsonObject["singer"]?.jsonPrimitive?.content ?: ""
            if (singer.isNotEmpty()) "$name - $singer" else name
        } ?: emptyList()
        TipSearchResult(list = list)
    }
    override suspend fun getLeaderboards() = Result.success(listOf(
        LeaderboardItem("tx__4", "流行指数榜"), LeaderboardItem("tx__26", "热歌榜"),
        LeaderboardItem("tx__28", "网络歌曲榜"), LeaderboardItem("tx__60", "抖音热歌榜"),
        LeaderboardItem("tx__3", "欧美榜"), LeaderboardItem("tx__5", "内地榜"),
        LeaderboardItem("tx__6", "香港榜"), LeaderboardItem("tx__16", "韩国榜"),
        LeaderboardItem("tx__17", "日本榜"), LeaderboardItem("tx__27", "新歌榜"),
        LeaderboardItem("tx__62", "说唱榜"),
    ))
    override suspend fun getLeaderboardDetail(boardId: String, page: Int, limit: Int): Result<SearchResult> = runCatching {
        val topid = boardId.removePrefix("tx__").toIntOrNull() ?: return@runCatching SearchResult()
        val payload = """{"comm":{"uin":0,"format":"json","ct":20,"cv":1859},"toplist":{"module":"musicToplist.ToplistInfoServer","method":"GetDetail","param":{"topid":$topid,"num":$limit,"page":$page}}}"""
        val sign = zzcSign(payload)
        val body = httpPost("https://u.y.qq.com/cgi-bin/musics.fcg?sign=$sign", payload)
        val obj = json.parseToJsonElement(body).jsonObject
        val data = obj["toplist"]?.jsonObject?.get("data")?.jsonObject ?: return@runCatching SearchResult()
        val songs = parseMusicSearchItems(data["songInfoList"]?.jsonArray ?: JsonArray(emptyList()))
        SearchResult(list = songs, total = songs.size, page = page, limit = limit)
    }
    override suspend fun getMusicUrl(musicInfo: MusicInfo, quality: Quality): Result<String> =
        Result.failure(UnsupportedOperationException("Custom source required"))
    override suspend fun getLyric(m: MusicInfo): Result<LyricInfo> = runCatching {
        val songmid = m.meta.songId
        // Step 1: Get songId from songmid
        val songId = getTxSongId(songmid) ?: return@runCatching LyricInfo()
        // Step 2: Fetch QRC lyrics with word timing
        val qrcBody = """{"comm":{"ct":"19","cv":"1859","uin":"0"},"req":{"method":"GetPlayLyricInfo","module":"music.musichallSong.PlayLyricInfo","param":{"format":"json","crypt":1,"ct":19,"cv":1873,"interval":0,"lrc_t":0,"qrc":1,"qrc_t":0,"roma":1,"roma_t":0,"songID":$songId,"trans":1,"trans_t":0,"type":-1}}}"""
        val resp = httpPostJson("https://u.y.qq.com/cgi-bin/musicu.fcg", qrcBody,
            mapOf("Referer" to "https://y.qq.com", "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36"))
        val obj = json.parseToJsonElement(resp).jsonObject
        android.util.Log.d("TxLyric", "qrc resp: code=${obj["code"]} reqCode=${obj["req"]?.jsonObject?.get("code")} lyricLen=${obj["req"]?.jsonObject?.get("data")?.jsonObject?.get("lyric")?.jsonPrimitive?.content?.length}")
        if (obj["code"]?.jsonPrimitive?.content?.toIntOrNull() != 0 || obj["req"]?.jsonObject?.get("code")?.jsonPrimitive?.content?.toIntOrNull() != 0) return@runCatching LyricInfo()
        val data = obj["req"]?.jsonObject?.get("data")?.jsonObject ?: return@runCatching LyricInfo()
        // Step 3: Decode QRC lyrics
        val qrcText = QrcDecoder.decode(data["lyric"]?.jsonPrimitive?.content ?: "")
        val transHex = data["trans"]?.jsonPrimitive?.content ?: ""
        val romaHex = data["roma"]?.jsonPrimitive?.content ?: ""
        val transText = if (transHex.length % 2 == 0) QrcDecoder.decode(transHex) else transHex
        val romaText = if (romaHex.length % 2 == 0) QrcDecoder.decode(romaHex) else romaHex
        android.util.Log.d("TxLyric", "lyricLen=${qrcText.length} transLen=${transText.length} romaLen=${romaText.length} transRaw=${transText.take(300)}")
        // Step 4: Parse QRC to extract lxlyric and clean lyric
        parseTxQrcLyric(qrcText, transText, romaText)
    }

    /** Get TX songId (numeric) from songmid via get_song_detail_yqq */
    private suspend fun getTxSongId(songmid: String): String? {
        val body = """{"comm":{"ct":"19","cv":"1859","uin":"0"},"req":{"module":"music.pf_song_detail_svr","method":"get_song_detail_yqq","param":{"song_type":0,"song_mid":"$songmid"}}}"""
        val resp = httpPostJson("https://u.y.qq.com/cgi-bin/musicu.fcg", body,
            mapOf("User-Agent" to "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)"))
        val obj = json.parseToJsonElement(resp).jsonObject
        android.util.Log.d("TxLyric", "getSongId resp track_info=${obj["req"]?.jsonObject?.get("data")?.jsonObject?.get("track_info")?.toString()?.take(500)}")
        if (obj["code"]?.jsonPrimitive?.content?.toIntOrNull() != 0) return null
        val songId = obj["req"]?.jsonObject?.get("data")?.jsonObject?.get("track_info")?.jsonObject?.get("id")?.jsonPrimitive?.content
        android.util.Log.d("TxLyric", "songmid=$songmid -> songId=$songId")
        return songId
    }

    private fun httpPostJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String {
        val c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            outputStream.write(body.toByteArray())
        }
        return c.inputStream.bufferedReader().readText().also { c.disconnect() }
    }

    /** Parse QRC format to LyricInfo with lxlyric. QRC uses [startMs,durationMs](start,dur)word format.
     *  trans/roma text may be hex-encoded QRC (same as lyric) or plain LRC. Handle both. */
    private fun parseTxQrcLyric(qrcText: String, transText: String, romaText: String): LyricInfo {
        val lrcInfo = parseQrcLines(qrcText)
        // trans/roma: plain LRC with [mm:ss.xx] time format, not QRC format
        // fixTimeTag aligns their timestamps to match the main lyric
        val tlyric = if (transText.isNotBlank()) fixTimeTag(transText, lrcInfo?.first ?: "") else null
        val rlyric = if (romaText.isNotBlank()) fixTimeTag(romaText, lrcInfo?.first ?: "") else null

        return LyricInfo(
            lyric = lrcInfo?.first ?: "",
            tlyric = tlyric?.ifBlank { null },
            rlyric = rlyric?.ifBlank { null },
            lxlyric = lrcInfo?.second
        )
    }

    /** Parse QRC lyric lines into (clean lyric, lxlyric). QRC line format: [startMs,durationMs](start,dur)word... */
    private fun parseQrcLines(text: String): Pair<String, String>? {
        if (text.isBlank()) return null
        val lrcLines = mutableListOf<String>()
        val lxlrcLines = mutableListOf<String>()
        val lineTimeRegex = Regex("""^\[(\d+),\d+]""")
        val wordTimeRegex = Regex("""\((\d+),(\d+)\)""")
        val wordTimeSplit = Regex("""\(\d+,\d+\)""")  // non-capturing for split
        val wordTimeAll = Regex("""(\(\d+,\d+\))""")

        for (line in text.split("\n")) {
            val trimmed = line.trim().replace("\r", "")
            if (trimmed.isEmpty()) continue
            val timeMatch = lineTimeRegex.find(trimmed) ?: run {
                if (trimmed.startsWith("[offset]")) { lxlrcLines.add(trimmed); lrcLines.add(trimmed) }
                continue
            }
            val startMs = timeMatch.groupValues[1].toLong()
            val timeStr = formatQrcTime(startMs)
            if (timeStr.isEmpty()) continue
            var words = trimmed.replace(lineTimeRegex, "")
            lrcLines.add("${timeStr}${words.replace(wordTimeAll, "")}")
            val times = wordTimeAll.findAll(words).toList()
            if (times.isEmpty()) continue
            val convertedTimes = times.map { t ->
                val m = Regex("""\((\d+),(\d+)\)""").find(t.value)!!
                "<${maxOf(0, m.groupValues[1].toLong() - startMs)},${m.groupValues[2]}"
            }
            val wordArr = words.split(wordTimeSplit).toMutableList()
            if (wordArr.isNotEmpty() && wordArr[0].isEmpty()) wordArr.removeFirst()
            val newWords = convertedTimes.zip(wordArr).joinToString("") { (t, w) -> "$t>$w" }
            lxlrcLines.add("${timeStr}${newWords}")
        }
        return Pair(lrcLines.joinToString("\n"), lxlrcLines.joinToString("\n"))
    }

    /** Fix time tags in translation/romaji to match main lyric timestamps. */
    private fun fixTimeTag(tlrcText: String, lrcText: String): String {
        val tlrcLines = tlrcText.split("\n").toMutableList()
        val lrcLines = lrcText.split("\n").toMutableList()
        val newLrc = mutableListOf<String>()
        val lineTimeRegex = Regex("""^\[(\d+:\d+\.\d+)]""")

        for (line in tlrcLines) {
            val result = lineTimeRegex.find(line) ?: continue
            val words = line.replace(lineTimeRegex, "").trim()
            if (words.isEmpty() || words == "//") continue
            val t1 = parseTimeMs(result.groupValues[1])
            while (lrcLines.isNotEmpty()) {
                val lrcLine = lrcLines.removeFirst()
                val lrcResult = lineTimeRegex.find(lrcLine) ?: continue
                val t2 = parseTimeMs(lrcResult.groupValues[1])
                if (kotlin.math.abs(t1 - t2) < 100) {
                    newLrc.add(line.replace(lineTimeRegex, lrcResult.value))
                    break
                }
            }
        }
        return newLrc.joinToString("\n")
    }

    private fun formatQrcTime(timeMs: Long): String {
        val ms = (timeMs % 1000).toString().padStart(3, '0')
        val totalSec = timeMs / 1000
        val m = (totalSec / 60).toString().padStart(2, '0')
        val s = (totalSec % 60).toString().padStart(2, '0')
        return "[$m:$s.$ms]"
    }

    private fun parseTimeMs(time: String): Long {
        val parts = time.split(":", ".")
        val m = parts.getOrNull(0)?.toLongOrNull() ?: 0
        val s = parts.getOrNull(1)?.toLongOrNull() ?: 0
        val msRaw = parts.getOrNull(2)?.take(3) ?: "0"
        val ms = (msRaw.padEnd(3, '0')).toLongOrNull() ?: 0
        return m * 60000 + s * 1000 + ms
    }

    override suspend fun getPic(m: MusicInfo): Result<String> = runCatching {
        val albumId = (m.meta as? MusicInfoMetaOnline)?.albumId ?: ""
        if (albumId.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumId}.jpg" else ""
    }
    // Ported from src/utils/musicSdk/tx/songList.js getTag + getHotTag
    override suspend fun getSongListTags(): Result<List<SongListTag>> = runCatching {
        val tagsUrl = "https://u.y.qq.com/cgi-bin/musicu.fcg?loginUin=0&hostUin=0&format=json&inCharset=utf-8&outCharset=utf-8&notice=0&platform=wk_v15.json&needNewCode=0&data=%7B%22tags%22%3A%7B%22method%22%3A%22get_all_categories%22%2C%22param%22%3A%7B%22qq%22%3A%22%22%7D%2C%22module%22%3A%22playlist.PlaylistAllCategoriesServer%22%7D%7D"
        val body = httpGet(tagsUrl)
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["code"]?.jsonPrimitive?.intOrNull != 0) return@runCatching emptyList()
        val groups = obj["tags"]?.jsonObject?.get("data")?.jsonObject?.get("v_group")?.jsonArray ?: return@runCatching emptyList()
        // Hot tags — HTML page parsing, skip complexity; just return regular tags
        groups.map { g ->
            val go = g.jsonObject
            SongListTag(
                name = go["group_name"]?.jsonPrimitive?.content ?: "",
                id = go["group_id"]?.jsonPrimitive?.content ?: "",
                children = (go["v_item"]?.jsonArray ?: emptyList()).map { i ->
                    val io = i.jsonObject
                    SongListTag(name = io["name"]?.jsonPrimitive?.content ?: "",
                        id = io["id"]?.jsonPrimitive?.content ?: "",
                        children = emptyList())
                }
            )
        }
    }

    // Ported from src/utils/musicSdk/tx/songList.js getList
    // sortId: 5=hot, 2=new; tagId: category id (empty = plaza)
    override suspend fun getSongList(sortId: String, tagId: String, page: Int): Result<List<SongListItem>> = runCatching {
        val dataParam = if (tagId.isNotEmpty())
            "{\"comm\":{\"cv\":1602,\"ct\":20},\"playlist\":{\"method\":\"get_category_content\",\"param\":{\"titleid\":${tagId.toIntOrNull() ?: 0},\"caller\":\"0\",\"category_id\":${tagId.toIntOrNull() ?: 0},\"size\":36,\"page\":${page - 1},\"use_page\":1},\"module\":\"playlist.PlayListCategoryServer\"}}"
        else
            "{\"comm\":{\"cv\":1602,\"ct\":20},\"playlist\":{\"method\":\"get_playlist_by_tag\",\"param\":{\"id\":10000000,\"sin\":${36 * (page - 1)},\"size\":36,\"order\":${sortId.toIntOrNull() ?: 5},\"cur_page\":$page},\"module\":\"playlist.PlayListPlazaServer\"}}"
        val url = "https://u.y.qq.com/cgi-bin/musicu.fcg?loginUin=0&hostUin=0&format=json&inCharset=utf-8&outCharset=utf-8&notice=0&platform=wk_v15.json&needNewCode=0&data=${URLEncoder.encode(dataParam, "UTF-8")}"
        for (retryNum in 0..2) {
            val obj = json.parseToJsonElement(httpGet(url)).jsonObject
            if (obj["code"]?.jsonPrimitive?.intOrNull != 0) continue
            val data = obj["playlist"]?.jsonObject?.get("data") ?: continue
            val items = (if (tagId.isNotEmpty())
                (data.jsonObject["content"]?.jsonObject?.get("v_item")?.jsonArray ?: emptyList()).map { el ->
                    val basic = el.jsonObject["basic"]?.jsonObject ?: return@map null
                    val playCnt = basic["play_cnt"]?.jsonPrimitive?.longOrNull
                    SongListItem(id = basic["tid"]?.jsonPrimitive?.content ?: "",
                        name = basic["title"]?.jsonPrimitive?.content ?: "",
                        pic = basic["cover"]?.jsonObject?.get("medium_url")?.jsonPrimitive?.content
                            ?: basic["cover"]?.jsonObject?.get("default_url")?.jsonPrimitive?.content,
                        playCount = playCnt ?: 0L,
                        author = basic["creator"]?.jsonObject?.get("nick")?.jsonPrimitive?.content ?: "",
                        desc = HttpUtils.htmlDecode(basic["desc"]?.jsonPrimitive?.content ?: "").replace("<br>", "\n"),
                        source = "tx")
                }.filterNotNull()
            else
                (data.jsonObject["v_playlist"]?.jsonArray ?: emptyList()).map { el ->
                    val item = el.jsonObject
                    SongListItem(id = item["tid"]?.jsonPrimitive?.content ?: "",
                        name = item["title"]?.jsonPrimitive?.content ?: "",
                        pic = item["cover_url_medium"]?.jsonPrimitive?.content,
                        playCount = item["access_num"]?.jsonPrimitive?.longOrNull ?: 0L,
                        author = item["creator_info"]?.jsonObject?.get("nick")?.jsonPrimitive?.content ?: "",
                        desc = HttpUtils.htmlDecode(item["desc"]?.jsonPrimitive?.content ?: "").replace("<br>", "\n"),
                        source = "tx")
                })
            return@runCatching items
        }
        throw Exception("获取列表失败")
    }
    // Ported from src/utils/musicSdk/tx/songList.js getListDetail
    override suspend fun getSongListDetail(listId: String, page: Int, limit: Int): Result<SongListDetailResult> = runCatching {
        if (page > 1) return@runCatching SongListDetailResult()  // TX API returns all songs at once
        val id = listId
        val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=$id&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
        val body = httpGet(url, mapOf("Origin" to "https://y.qq.com", "Referer" to "https://y.qq.com/n/yqq/playsquare/$id.html"))
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["code"]?.jsonPrimitive?.intOrNull != 0) return@runCatching SongListDetailResult()
        val cd = (obj["cdlist"]?.jsonArray?.getOrNull(0)?.jsonObject) ?: return@runCatching SongListDetailResult()
        val songs = parseMusicSearchItems(cd["songlist"]?.jsonArray ?: JsonArray(emptyList()))
        SongListDetailResult(
            list = songs, total = songs.size, page = 1, limit = songs.size + 1,
            info = SongListDetailInfo(
                name = cd["dissname"]?.jsonPrimitive?.content ?: "",
                pic = cd["logo"]?.jsonPrimitive?.content,
                desc = HttpUtils.htmlDecode(cd["desc"]?.jsonPrimitive?.content ?: "").replace("<br>", "\n"),
                author = cd["nickname"]?.jsonPrimitive?.content ?: "",
                playCount = cd["visitnum"]?.jsonPrimitive?.longOrNull ?: 0L))
    }

    /** Shared parser for tx songlist items (used by searchMusic and getSongListDetail). */
    private fun parseMusicSearchItems(rawList: JsonArray): List<MusicInfo> = rawList.mapNotNull { el ->
        val item = el.jsonObject
        val file = item["file"]?.jsonObject ?: return@mapNotNull null
        val mediaMid = file["media_mid"]?.jsonPrimitive?.content
        if (mediaMid.isNullOrEmpty()) return@mapNotNull null
        val mid = item["mid"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val title = item["title"]?.jsonPrimitive?.content ?: ""
        val singers = item["singer"]?.jsonArray?.map { it.jsonObject }
        val singer = HttpUtils.htmlDecode(singers.orEmpty()
            .mapNotNull { s -> s["name"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } }
            .joinToString("、"))
        val dur = item["interval"]?.jsonPrimitive?.intOrNull ?: 0
        val album = item["album"]?.jsonObject
        val albumName = album?.get("name")?.jsonPrimitive?.content ?: ""
        val albumId = album?.get("mid")?.jsonPrimitive?.content ?: ""
        // QQ leaderboard API returns quality sizes at song top level: size128, size320, sizeflac
        val qs = parseTxQuality(item)
        val firstSingerMid = singers?.firstOrNull()?.get("mid")?.jsonPrimitive?.content
        val picUrl = if (albumId.isEmpty() || albumId == "空")
            firstSingerMid?.let { "https://y.gtimg.cn/music/photo_new/T001R500x500M000$it.jpg" }
        else "https://y.gtimg.cn/music/photo_new/T002R500x500M000$albumId.jpg"
        MusicInfo("tx_$mid", title, singer, "tx",
            if (dur > 0) "${dur / 60}:${(dur % 60).toString().padStart(2, '0')}" else null,
            MusicInfoMetaOnline(songId = mid, albumName = albumName, picUrl = picUrl, qualitys = qs, albumId = albumId,
                extra = mapOf("strMediaMid" to mediaMid, "albumMid" to albumId)))
    }
    // Ported from src/utils/musicSdk/tx/songList.js search()
    override suspend fun searchSongList(keyword: String, page: Int): Result<List<SongListItem>> = runCatching {
        val limit = 20
        for (retryNum in 0..5) {
            val body = httpGet(
                "http://c.y.qq.com/soso/fcgi-bin/client_music_search_songlist?page_no=${page - 1}&num_per_page=$limit&format=json&query=${URLEncoder.encode(keyword, "UTF-8")}&remoteplace=txt.yqq.playlist&inCharset=utf8&outCharset=utf-8",
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)",
                    "Referer" to "http://y.qq.com/portal/search.html"))
            val obj = json.parseToJsonElement(body).jsonObject
            if (obj["code"]?.jsonPrimitive?.intOrNull != 0) continue
            return@runCatching (obj["data"]?.jsonObject?.get("list")?.jsonArray ?: JsonArray(emptyList())).map { el ->
                val item = el.jsonObject
                SongListItem(
                    id = item["dissid"]?.jsonPrimitive?.content ?: "",
                    name = HttpUtils.htmlDecode(item["dissname"]?.jsonPrimitive?.content ?: ""),
                    pic = item["imgurl"]?.jsonPrimitive?.content,
                    playCount = item["listennum"]?.jsonPrimitive?.longOrNull ?: 0L,
                    author = HttpUtils.htmlDecode(item["creator"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""),
                    desc = HttpUtils.htmlDecode(HttpUtils.htmlDecode(item["introduction"]?.jsonPrimitive?.content ?: "")).replace("<br>", "\n"),
                    source = "tx")
            }
        }
        throw Exception("搜索失败")
    }

    override suspend fun getHotComments(m: MusicInfo, p: Int, l: Int): Result<CommentResult> = Result.success(CommentResult())
    override suspend fun getNewComments(m: MusicInfo, p: Int, l: Int): Result<CommentResult> = Result.success(CommentResult())

    /** Parse quality from QQ songs. Two API formats:
     *  Client search (c.y.qq.com): song-level keys — size128, size320, sizeflac, sizeape
     *  Internal API (u.y.qq.com): file-level keys — size_128mp3, size_320mp3, size_flac, size_hires */
    private fun parseTxQuality(item: JsonObject): List<MusicQualityType> {
        val file = item["file"]?.jsonObject
        val qs = mutableListOf<MusicQualityType>()
        // Try both key formats for each quality level
        fun sz(songKey: String, fileKey: String) =
            (item[songKey]?.jsonPrimitive?.longOrNull ?: 0L).let { if (it > 0) it else file?.get(fileKey)?.jsonPrimitive?.longOrNull ?: 0L }
        // flac24bit: song-level has "size_hires" only in some versions; file-level has "size_hires"
        val hires = item["size_hires"]?.jsonPrimitive?.longOrNull ?: file?.get("size_hires")?.jsonPrimitive?.longOrNull ?: 0L
        if (hires > 0) qs.add(MusicQualityType("flac24bit", sizeFormate(hires)))
        // flac: song="sizeflac", file="size_flac"; also ape
        listOf("sizeflac" to "size_flac", "sizeape" to "size_flac").forEach { (sk, fk) ->
            sz(sk, fk).let { if (it > 0) qs.add(MusicQualityType("flac", sizeFormate(it))) }
        }
        // 320k: song="size320", file="size_320mp3"
        sz("size320", "size_320mp3").let { if (it > 0) qs.add(MusicQualityType("320k", sizeFormate(it))) }
        // 128k: song="size128", file="size_128mp3"
        sz("size128", "size_128mp3").let { if (it > 0) qs.add(MusicQualityType("128k", sizeFormate(it))) }
        return qs
    }
}
