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
class KgSource @Inject constructor() : MusicSource {
    override val sourceId: OnlineSource = KnownSources.KG
    override val sourceName: String = "酷狗音乐"
    override val isEnabled: Boolean = true
    override val supportedQualities: List<Quality> = listOf(KnownQualities._128K, KnownQualities._320K, KnownQualities.FLAC, KnownQualities.FLAC_24BIT)
    override val supportsSongList: Boolean = true
    override val supportsLeaderboard: Boolean = true
    override val supportsComments: Boolean = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private fun httpGet(urlStr: String, headers: Map<String, String> = emptyMap()): String {
        val c = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
            setRequestProperty("User-Agent", "Mozilla/5.0")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }; return c.inputStream.bufferedReader().readText().also { c.disconnect() }
    }
    private fun buildUrl(b: String, p: Map<String, String>) = p.entries.joinToString("&") { "${URLEncoder.encode(it.key,"UTF-8")}=${URLEncoder.encode(it.value,"UTF-8")}" }.let { "$b?$it" }

    override suspend fun searchMusic(keyword: String, page: Int, limit: Int): Result<SearchResult> = runCatching {
        val body = httpGet(buildUrl("https://songsearch.kugou.com/song_search_v2", mapOf(
            "keyword" to keyword, "page" to "$page", "pagesize" to "$limit",
            "userid" to "0", "clientver" to "", "platform" to "WebFilter",
            "filter" to "2", "iscorrection" to "1", "privilege_filter" to "0", "area_code" to "1")))
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["error_code"]?.jsonPrimitive?.content != "0") return@runCatching SearchResult()
        val list = obj["data"]?.jsonObject?.get("lists")?.jsonArray?.map { i ->
            val it = i.jsonObject
            val dur = it["Duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val name = it["OriSongName"]?.jsonPrimitive?.content ?: it["SongName"]?.jsonPrimitive?.content ?: ""
            val singer = it["SingerName"]?.jsonPrimitive?.content ?: ""
            MusicInfo("kg_${it["Audioid"]?.jsonPrimitive?.content}", name, singer, "kg",
                interval = if (dur > 0) "${dur / 60}:${(dur % 60).toString().padStart(2, '0')}" else null,
                meta = MusicInfoMetaOnline(songId = it["FileHash"]?.jsonPrimitive?.content ?: "",
                    albumName = it["AlbumName"]?.jsonPrimitive?.content ?: "",
                    albumId = it["AlbumID"]?.jsonPrimitive?.content ?: "",
                    qualitys = parseKgSearchQualitys(it)))
        } ?: emptyList()
        SearchResult(list = list, total = obj["data"]?.jsonObject?.get("total")?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
    }

    override suspend fun hotSearch(): Result<List<String>> = runCatching {
        val headers = mapOf(
            "dfid" to "1ssiv93oVqMp27cirf2CvoF1",
            "mid" to "156798703528610303473757548878786007104",
            "clienttime" to "${System.currentTimeMillis() / 1000}",
            "x-router" to "msearch.kugou.com",
            "User-Agent" to "Android9-AndroidPhone-10020-130-0-searchrecommendprotocol-wifi",
            "kg-rc" to "1"
        )
        val body = httpGet("http://gateway.kugou.com/api/v3/search/hot_tab?signature=ee44edb9d7155821412d220bcaf509dd&appid=1005&clientver=10026&plat=0", headers)
        val o = json.parseToJsonElement(body).jsonObject
        if (o["errcode"]?.jsonPrimitive?.content?.toIntOrNull() != 0) return@runCatching emptyList()
        o["data"]?.jsonObject?.get("list")?.jsonArray?.flatMap { item ->
            item.jsonObject["keywords"]?.jsonArray?.mapNotNull { k -> k.jsonObject["keyword"]?.jsonPrimitive?.content } ?: emptyList()
        } ?: emptyList()
    }

    override suspend fun tipSearch(k: String): Result<TipSearchResult> = runCatching {
        val url = "https://searchtip.kugou.com/getSearchTip?MusicTipCount=10&keyword=${URLEncoder.encode(k, "UTF-8")}"
        val body = httpGet(url, mapOf("referer" to "https://www.kugou.com/"))
        val arr = json.parseToJsonElement(body).jsonArray
        val list = arr.firstOrNull()?.jsonObject?.get("RecordDatas")?.jsonArray?.mapNotNull {
            it.jsonObject["HintInfo"]?.jsonPrimitive?.content
        } ?: emptyList()
        TipSearchResult(list = list)
    }
    // Hardcoded board list (ported from kg/leaderboard.js boardList)
    override suspend fun getLeaderboards() = Result.success(listOf(
        LeaderboardItem("kg__8888", "TOP500"), LeaderboardItem("kg__6666", "飙升榜"),
        LeaderboardItem("kg__59703", "蜂鸟流行音乐榜"), LeaderboardItem("kg__52144", "抖音热歌榜"),
        LeaderboardItem("kg__52767", "快手热歌榜"), LeaderboardItem("kg__24971", "DJ热歌榜"),
        LeaderboardItem("kg__23784", "网络红歌榜"), LeaderboardItem("kg__44412", "说唱先锋榜"),
        LeaderboardItem("kg__31308", "内地榜"), LeaderboardItem("kg__33160", "电音榜"),
        LeaderboardItem("kg__31313", "香港地区榜"), LeaderboardItem("kg__51341", "民谣榜"),
        LeaderboardItem("kg__54848", "台湾地区榜"), LeaderboardItem("kg__31310", "欧美榜"),
        LeaderboardItem("kg__33162", "ACG新歌榜"), LeaderboardItem("kg__31311", "韩国榜"),
        LeaderboardItem("kg__31312", "日本榜"), LeaderboardItem("kg__49225", "80后热歌榜"),
        LeaderboardItem("kg__49223", "90后热歌榜"), LeaderboardItem("kg__49224", "00后热歌榜"),
        LeaderboardItem("kg__33165", "粤语金曲榜"), LeaderboardItem("kg__33166", "欧美金曲榜"),
        LeaderboardItem("kg__33163", "影视金曲榜"), LeaderboardItem("kg__51340", "伤感榜"),
        LeaderboardItem("kg__35811", "会员专享榜"), LeaderboardItem("kg__37361", "雷达榜"),
        LeaderboardItem("kg__21101", "分享榜"), LeaderboardItem("kg__46910", "综艺新歌榜"),
        LeaderboardItem("kg__30972", "酷狗音乐人原创榜"), LeaderboardItem("kg__60170", "闽南语榜"),
        LeaderboardItem("kg__65234", "儿歌榜"), LeaderboardItem("kg__4681", "美国BillBoard榜"),
        LeaderboardItem("kg__25028", "Beatport电子舞曲榜"), LeaderboardItem("kg__4680", "英国单曲榜"),
        LeaderboardItem("kg__38623", "韩国Melon音乐榜"), LeaderboardItem("kg__42807", "joox本地热歌榜"),
        LeaderboardItem("kg__36107", "小语种热歌榜"), LeaderboardItem("kg__4673", "日本公信榜"),
        LeaderboardItem("kg__46868", "日本SPACE SHOWER榜"), LeaderboardItem("kg__42808", "KKBOX风云榜"),
        LeaderboardItem("kg__60171", "越南语榜"), LeaderboardItem("kg__60172", "泰语榜"),
        LeaderboardItem("kg__59895", "R&B榜"), LeaderboardItem("kg__59896", "摇滚榜"),
        LeaderboardItem("kg__59897", "爵士榜"), LeaderboardItem("kg__59898", "乡村音乐榜"),
        LeaderboardItem("kg__59900", "纯音乐榜"), LeaderboardItem("kg__59899", "古典榜"),
        LeaderboardItem("kg__22603", "5sing音乐榜"), LeaderboardItem("kg__21335", "繁星音乐榜"),
        LeaderboardItem("kg__33161", "古风新歌榜"),
    ))
    // Ported from kg/leaderboard.js getUrl: mobilecdnbj.kugou.com
    override suspend fun getLeaderboardDetail(boardId: String, page: Int, limit: Int): Result<SearchResult> = runCatching {
        val bangid = boardId.removePrefix("kg__")
        val url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108&ranktype=1&plat=0&pagesize=$limit&area_code=1&page=$page&rankid=$bangid&with_res_tag=0&show_portrait_mv=1"
        val obj = json.parseToJsonElement(httpGet(url)).jsonObject
        if (obj["errcode"]?.jsonPrimitive?.intOrNull != 0) return@runCatching SearchResult()
        val d = obj["data"]?.jsonObject ?: return@runCatching SearchResult()
        val songs = (d["info"]?.jsonArray ?: emptyList()).map { el ->
            val item = el.jsonObject
            val dur = item["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            MusicInfo("kg_${item["audio_id"]?.jsonPrimitive?.content}",
                item["songname"]?.jsonPrimitive?.content ?: "",
                item["authors"]?.jsonArray?.joinToString("/") { it.jsonObject["author_name"]?.jsonPrimitive?.content ?: "" } ?: "", "kg",
                if (dur > 0) "${dur / 60}:${(dur % 60).toString().padStart(2, '0')}" else null,
                MusicInfoMetaOnline(songId = item["hash"]?.jsonPrimitive?.content ?: "",
                    albumName = "",
                    qualitys = parseKgLbQualitys(item)))
        }
        SearchResult(list = songs, total = d["total"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0, page = page, limit = limit)
    }
    override suspend fun getMusicUrl(musicInfo: MusicInfo, quality: Quality): Result<String> =
        Result.failure(UnsupportedOperationException("Custom source required"))
    override suspend fun getLyric(m: MusicInfo): Result<LyricInfo> = runCatching {
        val hash = (m.meta as? MusicInfoMetaOnline)?.extra?.get("hash") ?: m.meta.songId
        val name = m.name; val time = (m.interval?.let { parseInterval(it) } ?: 0) * 1000
        val searchUrl = "http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${java.net.URLEncoder.encode(name, "utf-8")}&hash=$hash&timelength=$time&lrctxt=1"
        val searchBody = httpGet(searchUrl, mapOf("KG-RC" to "1", "KG-THash" to "expand_search_manager.cpp:852736169:451", "User-Agent" to "KuGou2012-9020-ExpandSearchManager"))
        val candidates = json.parseToJsonElement(searchBody).jsonObject["candidates"]?.jsonArray
        if (candidates.isNullOrEmpty()) return@runCatching LyricInfo()
        val first = candidates[0].jsonObject; val id = first["id"]?.jsonPrimitive?.content ?: return@runCatching LyricInfo()
        val accessKey = first["accesskey"]?.jsonPrimitive?.content ?: return@runCatching LyricInfo()
        // Compute fmt like RN: krc if krctype==1 && contenttype!=1, else lrc
        val krctype = first["krctype"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val contenttype = first["contenttype"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val fmt = if (krctype == 1 && contenttype != 1) "krc" else "lrc"
        android.util.Log.d("KgLyric", "krctype=$krctype contenttype=$contenttype fmt=$fmt")
        val dlUrl = "http://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=$fmt&charset=utf8"
        val dlBody = httpGet(dlUrl, mapOf("KG-RC" to "1", "KG-THash" to "expand_search_manager.cpp:852736169:451", "User-Agent" to "KuGou2012-9020-ExpandSearchManager"))
        val dlObj = json.parseToJsonElement(dlBody).jsonObject
        val content = dlObj["content"]?.jsonPrimitive?.content ?: return@runCatching LyricInfo()
        if (fmt == "krc") {
            val raw = android.util.Base64.decode(content, android.util.Base64.DEFAULT)
            val data = if (raw.size > 4) raw.sliceArray(4 until raw.size) else raw
            val key = byteArrayOf(0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69)
            for (i in data.indices) data[i] = (data[i].toInt() xor key[i % 16].toInt()).toByte()
            val inflater = java.util.zip.Inflater(); inflater.setInput(data)
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(4096)
            while (!inflater.finished()) { val n = inflater.inflate(buf); out.write(buf, 0, n) }; inflater.end()
            val decoded = out.toString("UTF-8")
            android.util.Log.d("KgLyric", "decoded krc (first 300): ${decoded.take(300)}")
            parseKrcLyric(decoded)
        } else {
            val lrcText = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), Charsets.UTF_8)
            android.util.Log.d("KgLyric", "lrc text (first 200): ${lrcText.take(200)}")
            LyricInfo(lyric = lrcText)
        }
    }

    /**
     * Parse KRC format lyrics — ported from src/utils/musicSdk/kg/lyric.js parseLyric().
     * Extracts lxlyric (word timing), tlyric (translation), rlyric (romaji) from KRC data.
     */
    private fun parseKrcLyric(raw: String): LyricInfo {
        return try {
            parseKrcLyricImpl(raw)
        } catch (e: Exception) {
            android.util.Log.e("KgLyric", "parseKrcLyric error: ${e.message}", e)
            // Fall back to clean text without lxlyric
            LyricInfo(lyric = raw.replace(Regex("""<\d+,\d+,\d+>"""), "").replace(Regex("""\[id:\$\w+\]\n"""), ""))
        }
    }

    private fun parseKrcLyricImpl(raw: String): LyricInfo {
        var str = raw.replace("\r", "")
        // Remove [id:$...] header
        val headRegex = Regex("""^.*\[id:\$\w+\]\n""")
        if (headRegex.containsMatchIn(str)) str = str.replace(headRegex, "")

        // Extract [language:...] block containing translation/romaji
        val langRegex = Regex("""\[language:([\w=/+]++)]""")
        val langMatch = langRegex.find(str)
        var rlyric: String? = null
        var tlyric: String? = null
        if (langMatch != null) {
            str = str.replace(Regex("""\[language:[\w=/+]++]\n"""), "")
            try {
                val jsonBytes = android.util.Base64.decode(langMatch.groupValues[1], android.util.Base64.DEFAULT)
                val langObj = json.parseToJsonElement(String(jsonBytes, Charsets.UTF_8)).jsonObject
                val contentArr = langObj["content"]?.jsonArray
                if (contentArr != null) {
                    for (i in 0 until contentArr.size) {
                        val item = contentArr[i].jsonObject
                        val lines = item["lyricContent"]?.jsonArray?.map { it.jsonArray?.getOrNull(0)?.jsonPrimitive?.content ?: "" } ?: emptyList()
                        val joined = lines.joinToString("\n")
                        when (item["type"]?.jsonPrimitive?.content?.toIntOrNull()) {
                            0 -> rlyric = joined // romaji
                            1 -> tlyric = joined // translation
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Parse word timing: convert [startMs,durationMs] → [MM:SS.ms]
        val timeRegex = Regex("""\[(\d+),(\d+)]""")
        val lines = str.split("\n").toMutableList()
        val rlyricLines = rlyric?.split("\n")?.toMutableList() ?: mutableListOf()
        val tlyricLines = tlyric?.split("\n")?.toMutableList() ?: mutableListOf()

        var idx = 0
        for (i in lines.indices) {
            val match = timeRegex.find(lines[i]) ?: continue
            val timeMs = match.groupValues[1].toLong()
            val formattedTime = formatKrcTime(timeMs)

            // Add time tag prefix to translation/romaji lines
            val prefix = "[$formattedTime]"
            if (idx < rlyricLines.size) rlyricLines[idx] = prefix + rlyricLines[idx]
            if (idx < tlyricLines.size) tlyricLines[idx] = prefix + tlyricLines[idx]

            idx++
            // Replace KRC time [startMs,durationMs] with standard LRC time [MM:SS.ms]
            lines[i] = lines[i].replace(match.value, "[$formattedTime]")
        }

        var lxlyric = lines.joinToString("\n")
        // Strip third number from word tags: <a,b,c> → <a,b>
        lxlyric = lxlyric.replace(Regex("""<(\d+,\d+),\d+>"""), "<$1>")
        // Decode HTML entities without destroying word timing tags
        lxlyric = lxlyric.split("\n").joinToString("\n") { decodeHtmlEntities(it) }
        // Clean lyric: remove word timing tags
        val lyric = lxlyric.replace(Regex("""<\d+,\d+>"""), "")

        val rlyricText = if (rlyricLines.isNotEmpty()) rlyricLines.joinToString("\n") { decodeHtmlEntities(it) } else null
        val tlyricText = if (tlyricLines.isNotEmpty()) tlyricLines.joinToString("\n") { decodeHtmlEntities(it) } else null

        val result = LyricInfo(lyric = lyric, tlyric = tlyricText, rlyric = rlyricText, lxlyric = lxlyric)
        android.util.Log.d("KgLyric", "parseKrcLyric result: lyric.len=${lyric.length} lxlyric.len=${lxlyric.length}")
        android.util.Log.d("KgLyric", "lyric sample: ${lyric.split("\n").find { it.contains("<") } ?: lyric.take(100)}")
        android.util.Log.d("KgLyric", "lxlyric sample: ${lxlyric.split("\n").find { it.contains("<") } ?: lxlyric.take(100)}")
        return result
    }

    /** Decode common HTML entities without destroying angle brackets used for word timing. */
    private fun decodeHtmlEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#039;", "'")
        .replace("&#39;", "'")

    private fun formatKrcTime(ms: Long): String {
        val m = (ms / 60000).toString().padStart(2, '0')
        val s = ((ms % 60000) / 1000).toString().padStart(2, '0')
        val msPart = (ms % 1000).toString().padStart(3, '0')
        return "$m:$s.$msPart"
    }

    override suspend fun getPic(m: MusicInfo): Result<String> = runCatching {
        val hash = (m.meta as? MusicInfoMetaOnline)?.extra?.get("hash") ?: m.meta.songId
        val audioId = (m.meta as? MusicInfoMetaOnline)?.extra?.get("audioId") ?: ""
        // Old version behavior: if hash is 32-char, use audioId's first segment ("" if missing),
        // otherwise use hash directly as album_audio_id (should be numeric)
        val albumAudioId = if (hash.length == 32) audioId.split("_").getOrNull(0) ?: "" else hash
        val albumId = (m.meta as? MusicInfoMetaOnline)?.albumId ?: ""
        val picBody = """{"appid":1001,"area_code":"1","behavior":"play","clientver":"9020","need_hash_offset":1,"relate":1,"resource":[{"album_audio_id":"$albumAudioId","album_id":"$albumId","hash":"$hash","id":0,"name":"${m.singer} - ${m.name}.mp3","type":"audio"}],"token":"","userid":2626431536,"vip":1}"""
        android.util.Log.d("KgPic", "hash=$hash audioId=$audioId albumAudioId=$albumAudioId body=$picBody")
        val resp = httpPostKg("http://media.store.kugou.com/v1/get_res_privilege", picBody,
            mapOf("KG-RC" to "1", "KG-THash" to "expand_search_manager.cpp:852736169:451",
                  "User-Agent" to "KuGou2012-9020-ExpandSearchManager"))
        android.util.Log.d("KgPic", "resp=${resp.take(500)}")
        val obj = json.parseToJsonElement(resp).jsonObject
        if (obj["error_code"]?.jsonPrimitive?.content?.toIntOrNull() != 0) {
            android.util.Log.e("KgPic", "error_code=${obj["error_code"]}")
            return@runCatching ""
        }
        val info = obj["data"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("info")?.jsonObject
        android.util.Log.d("KgPic", "info=$info")
        val image = info?.get("image")?.jsonPrimitive?.content ?: ""
        val size = info?.get("imgsize")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.content
        android.util.Log.d("KgPic", "image=$image size=$size")
        if (image.isNotBlank()) {
            if (size != null) image.replace("{size}", size) else image
        } else ""
    }

    private fun parseInterval(interval: String): Int {
        val parts = interval.split(":"); if (parts.size < 2) return 0
        return ((parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0))
    }

    // Ported from src/utils/musicSdk/kg/songList.js getTags
    override suspend fun getSongListTags(): Result<List<SongListTag>> = runCatching {
        val body = httpGet("http://www2.kugou.kugou.com/yueku/v9/special/getSpecial?is_smarty=1&",
            mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36"))
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["status"]?.jsonPrimitive?.intOrNull != 1) return@runCatching emptyList()
        (obj["data"]?.jsonObject?.get("tagids")?.jsonObject ?: JsonObject(emptyMap())).map { (name, value) ->
            val children = (value.jsonObject["data"]?.jsonArray ?: emptyList()).map { tag ->
                val to = tag.jsonObject
                SongListTag(name = to["name"]?.jsonPrimitive?.content ?: "", id = to["id"]?.jsonPrimitive?.content ?: "")
            }
            SongListTag(name = name, id = "", children = children)
        }
    }

    // Ported from src/utils/musicSdk/kg/songList.js getSongList
    // sortId: 5=hot, 6=new, 7=hot_collect, 8=rise; tagId: child tag id
    override suspend fun getSongList(sortId: String, tagId: String, page: Int): Result<List<SongListItem>> = runCatching {
        val url = "http://www2.kugou.kugou.com/yueku/v9/special/getSpecial?is_ajax=1&cdn=cdn&t=$sortId&c=$tagId&p=$page"
        val body = httpGet(url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36"))
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["status"]?.jsonPrimitive?.intOrNull != 1) return@runCatching emptyList()
        (obj["special_db"]?.jsonArray ?: emptyList()).map { el ->
            val item = el.jsonObject
            SongListItem(
                id = "id_${item["specialid"]?.jsonPrimitive?.content ?: ""}",
                name = item["specialname"]?.jsonPrimitive?.content ?: "",
                pic = item["img"]?.jsonPrimitive?.contentOrNull ?: item["imgurl"]?.jsonPrimitive?.contentOrNull,
                playCount = parseKgPlayCount(item["total_play_count"]?.jsonPrimitive?.content)
                    ?: item["play_count"]?.jsonPrimitive?.longOrNull ?: 0L,
                author = item["nickname"]?.jsonPrimitive?.content ?: "",
                desc = item["intro"]?.jsonPrimitive?.contentOrNull ?: "",
                source = "kg")
        }
    }
    /** Parse KG play count strings like "1698.7万", "1.2亿" → Long. */
    private fun parseKgPlayCount(text: String?): Long {
        if (text == null) return 0L
        return when {
            text.endsWith("亿") -> (text.dropLast(1).toDoubleOrNull()?.times(100_000_000))?.toLong() ?: 0L
            text.endsWith("万") -> (text.dropLast(1).toDoubleOrNull()?.times(10_000))?.toLong() ?: 0L
            else -> text.toLongOrNull() ?: 0L
        }
    }

    // ── KG signature (ported from src/utils/musicSdk/kg/util.js signatureParams) ──
    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }
    private fun kgSign(params: String, platform: String = "android", body: String = ""): String {
        val key = if (platform == "web") "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt" else "OIlwieks28dk2k092lksi2UIkp"
        val sorted = params.split("&").sorted().joinToString("")
        return md5("$key$sorted$body$key")
    }
    private fun httpPostKg(urlStr: String, body: String, headers: Map<String, String> = emptyMap()): String {
        val c = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
            requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; HUAWEI HMA-AL00) AppleWebKit/537.36")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            outputStream.write(body.toByteArray())
        }
        return c.inputStream.bufferedReader().readText().also { c.disconnect() }
    }

    // ── KG detail helpers (ported from songList.js getUserListDetail2 flow) ──

    /** Decode gcid -> global_collection_id (songList.js decodeGcid). */
    private suspend fun decodeGcid(gcid: String): String? {
        val params = "dfid=-&appid=1005&mid=0&clientver=20109&clienttime=640612895&uuid=-"
        val reqBody = """{"ret_info":1,"data":[{"id":"$gcid","id_type":2}]}"""
        val r = json.parseToJsonElement(httpPostKg(
            "https://t.kugou.com/v1/songlist/batch_decode?$params&signature=${kgSign(params, "android", reqBody)}",
            reqBody, mapOf("Referer" to "https://m.kugou.com/"))).jsonObject
        return r["data"]?.jsonObject?.get("list")?.jsonArray?.getOrNull(0)?.jsonObject?.get("global_collection_id")?.jsonPrimitive?.content
    }

    /** Fetch playlist info + all song hashes (songList.js getUserListDetail2). */
    private suspend fun kgPlaylistDetail(globalCollectionId: String): Pair<JsonObject, List<JsonObject>> {
        val infoParams = "appid=1058&specialid=0&global_specialid=$globalCollectionId&format=jsonp&srcappid=2919&clientver=20000&clienttime=1586163242519&mid=1586163242519&uuid=1586163242519&dfid=-"
        val rawInfo = json.parseToJsonElement(httpGet(
            "https://mobiles.kugou.com/api/v5/special/info_v2?$infoParams&signature=${kgSign(infoParams, "web")}",
            mapOf("mid" to "1586163242519", "Referer" to "https://m3ws.kugou.com/share/index.php",
                "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 11_0 like Mac OS X) AppleWebKit/604.1.38",
                "dfid" to "-", "clienttime" to "1586163242519"))).jsonObject
        // RN createHttp unwraps .data if present
        val info = rawInfo["data"]?.jsonObject ?: rawInfo
        val songCount = info["songcount"]?.jsonPrimitive?.content?.toIntOrNull() ?: return Pair(info, emptyList())
        // Fetch songs in batches of 300
        val hashes = mutableListOf<JsonObject>()
        var remaining = songCount
        var pg = 1
        while (remaining > 0) {
            val batchSize = minOf(remaining, 300)
            val songParams = "appid=1058&global_specialid=$globalCollectionId&specialid=0&plat=0&version=8000&page=$pg&pagesize=$batchSize&srcappid=2919&clientver=20000&clienttime=1586163263991&mid=1586163263991&uuid=1586163263991&dfid=-"
            val batchRaw = json.parseToJsonElement(httpGet(
                "https://mobiles.kugou.com/api/v5/special/song_v2?$songParams&signature=${kgSign(songParams, "web")}",
                mapOf("mid" to "1586163263991", "Referer" to "https://m3ws.kugou.com/share/index.php",
                    "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 11_0 like Mac OS X) AppleWebKit/604.1.38",
                    "dfid" to "-", "clienttime" to "1586163263991"))).jsonObject
            // RN createHttp unwraps .data
            val batch = batchRaw["data"]?.jsonObject ?: batchRaw
            batch["info"]?.jsonArray?.forEach { hashes.add(it.jsonObject) }
            remaining -= batchSize; pg++
        }
        return Pair(info, hashes)
    }

    /** Fetch full song data from hashes (songList.js createTask + getMusicInfos). */
    private suspend fun fetchKgSongs(hashes: List<JsonObject>): List<MusicInfo> {
        if (hashes.isEmpty()) return emptyList()
        val unique = hashes.mapNotNull { it.jsonObject["hash"]?.jsonPrimitive?.content }.distinct()
        // Batch by 100
        val audioHeaders = mapOf(
            "KG-THash" to "13a3164", "KG-RC" to "1", "KG-Fake" to "0", "KG-RF" to "00869891",
            "User-Agent" to "Android712-AndroidPhone-11451-376-0-FeeCacheUpdate-wifi",
            "x-router" to "kmr.service.kugou.com")
        return unique.chunked(100).flatMap { batch ->
            val hashesJson = batch.joinToString(",", "[", "]") { """{"hash":"$it"}""" }
            val body = """{"area_code":"1","show_privilege":1,"show_album_info":"1","is_publish":"","appid":1005,"clientver":11451,"mid":"1","dfid":"-","clienttime":${System.currentTimeMillis()},"key":"OIlwieks28dk2k092lksi2UIkp","fields":"album_info,author_name,audio_info,ori_audio_name,base,songname,classification","data":$hashesJson}"""
            val r = json.parseToJsonElement(httpPostKg("http://gateway.kugou.com/v3/album_audio/audio", body, audioHeaders)).jsonObject
            // RN createHttpFetch returns body.data; then .then(data => data.map(s => s[0]))
            // Response: { data: [ [songObj], [songObj], ... ] }
            val dataArr = r["data"]?.jsonArray
            android.util.Log.d("KgDetail", "audio resp: dataArr size=${dataArr?.size}, first element class=${dataArr?.getOrNull(0)?.javaClass?.simpleName}")
            dataArr?.getOrNull(0)?.let { android.util.Log.d("KgDetail", "first[0] class=${(it as? JsonArray)?.getOrNull(0)?.javaClass?.simpleName}") }
            // v3 response: { data: [ [songObj], [songObj], ... ] }
            // RN: createHttpFetch returns body.data, then .then(d => d.map(s => s[0]))
            val songs = r["data"]?.jsonArray?.mapNotNull { (it as? JsonArray)?.getOrNull(0)?.jsonObject } ?: emptyList()
            songs.mapNotNull { item ->
                val audio = item["audio_info"]?.jsonObject ?: return@mapNotNull null
                // v3 API returns timelength in milliseconds (v2 was seconds)
                val durMs = audio["timelength"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val durSec = durMs / 1000
                MusicInfo("kg_${audio["audio_id"]?.jsonPrimitive?.content}",
                    item["songname"]?.jsonPrimitive?.content ?: "",
                    item["singername"]?.jsonPrimitive?.content ?: item["author_name"]?.jsonPrimitive?.content ?: "",
                    "kg", if (durSec > 0) "${durSec / 60}:${(durSec % 60).toString().padStart(2, '0')}" else null,
                    MusicInfoMetaOnline(
                        songId = audio["hash"]?.jsonPrimitive?.content ?: "",
                        albumName = item["album_name"]?.jsonPrimitive?.content
                            ?: item["album_info"]?.jsonObject?.get("album_name")?.jsonPrimitive?.content ?: "",
                        qualitys = parseKgDetailQualitys(audio)))
            }
        }
    }

    override suspend fun getSongListDetail(listId: String, page: Int, limit: Int): Result<SongListDetailResult> = runCatching {
        if (page > 1) return@runCatching SongListDetailResult()  // KG fetches all songs at once
        val id = listId.trim()
        // Handle gcid links: https://m.kugou.com/songlist/gcid_xxx/
        val gcidMatch = Regex("""(gcid_\w+)""").find(id)
        if (gcidMatch != null) {
            val gcid = gcidMatch.groupValues[1]
            val globalCollectionId = decodeGcid(gcid) ?: return@runCatching SongListDetailResult()
            val (info, hashes) = kgPlaylistDetail(globalCollectionId)
            val songs = fetchKgSongs(hashes)
            return@runCatching SongListDetailResult(list = songs, total = songs.size, page = 1, limit = songs.size,
                info = SongListDetailInfo(
                    name = info["specialname"]?.jsonPrimitive?.content ?: "",
                    pic = info["imgurl"]?.jsonPrimitive?.content?.replace("{size}", "240"),
                    desc = info["intro"]?.jsonPrimitive?.content ?: "",
                    author = info["nickname"]?.jsonPrimitive?.content ?: "",
                    playCount = info["playcount"]?.jsonPrimitive?.longOrNull ?: 0L))
        }
        // Handle id_ prefix (from songlist browse) — ported from getListDetailBySpecialId
        val cleanId = id.removePrefix("id_")
        if (cleanId.all { it.isDigit() }) {
            for (retry in 0..2) {
                val html = httpGet("http://www2.kugou.kugou.com/yueku/v9/special/single/$cleanId-5-9999.html")
                val listDataMatch = Regex("""global\.data\s*=\s*(\[.+?\]);""").find(html)
                if (listDataMatch == null) continue
                val listInfoMatch = Regex("""global\s*=\s*\{[\s\S]+?name:\s*"(.+?)"[\s\S]+?pic:\s*"(.+?)"[\s\S]+?\};""").find(html)
                val hashes = json.parseToJsonElement(listDataMatch.groupValues[1]).jsonArray
                val songs = fetchKgSongs(hashes.map { it.jsonObject })
                return@runCatching SongListDetailResult(list = songs, total = songs.size, page = 1, limit = songs.size,
                    info = SongListDetailInfo(
                        name = listInfoMatch?.groupValues?.getOrNull(1) ?: "",
                        pic = listInfoMatch?.groupValues?.getOrNull(2)?.replace("{size}", "240"),
                        desc = parseKgHtmlDesc(html) ?: ""))
            }
        }
        SongListDetailResult()
    }

    /** Parse description from special single HTML page. */
    private fun parseKgHtmlDesc(html: String): String? {
        val prefix = "<div class=\"pc_specail_text pc_singer_tab_content\" id=\"specailIntroduceWrap\">"
        val start = html.indexOf(prefix)
        if (start < 0) return null
        val after = html.substring(start + prefix.length)
        val end = after.indexOf("</div>")
        if (end < 0) return null
        return cn.guoyujie666.music.compose.core.music.sources.HttpUtils.htmlDecode(after.substring(0, end))
    }
    // Ported from src/utils/musicSdk/kg/songList.js search()
    override suspend fun searchSongList(keyword: String, page: Int): Result<List<SongListItem>> = runCatching {
        val body = httpGet(buildUrl("http://msearchretry.kugou.com/api/v3/search/special", mapOf(
            "keyword" to keyword, "page" to "$page", "pagesize" to "20",
            "showtype" to "10", "filter" to "0", "version" to "7910", "sver" to "2")))
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["errcode"]?.jsonPrimitive?.intOrNull != 0) throw Exception("搜索失败")
        (obj["data"]?.jsonObject?.get("info")?.jsonArray ?: emptyList()).map { el ->
            val item = el.jsonObject
            SongListItem(
                id = "id_${item["specialid"]?.jsonPrimitive?.content ?: ""}",
                name = item["specialname"]?.jsonPrimitive?.content ?: "",
                pic = item["imgurl"]?.jsonPrimitive?.content,
                playCount = item["playcount"]?.jsonPrimitive?.longOrNull ?: 0L,
                author = item["nickname"]?.jsonPrimitive?.content ?: "",
                desc = item["intro"]?.jsonPrimitive?.content ?: "",
                source = "kg")
        }
    }
    override suspend fun getHotComments(m: MusicInfo, p: Int, l: Int) = Result.success(CommentResult())
    override suspend fun getNewComments(m: MusicInfo, p: Int, l: Int) = Result.success(CommentResult())

    // ── Quality helpers (ported from kg/musicSearch.js filterData, kg/leaderboard.js filterData, kg/songList.js filterData2) ──

    /** Parse qualitys from search API (uppercase fields: FileSize, HQFileSize, SQFileSize, ResFileSize). */
    private fun parseKgSearchQualitys(item: JsonObject): List<MusicQualityType> {
        val qs = mutableListOf<MusicQualityType>()
        item["ResFileSize"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("flac24bit", it.toString())) }
        item["SQFileSize"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("flac", it.toString())) }
        item["HQFileSize"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("320k", it.toString())) }
        item["FileSize"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("128k", it.toString())) }
        return qs
    }

    /** Parse qualitys from leaderboard API (fields: filesize_high, sqfilesize, 320filesize, filesize). */
    private fun parseKgLbQualitys(item: JsonObject): List<MusicQualityType> {
        val qs = mutableListOf<MusicQualityType>()
        item["filesize_high"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("flac24bit", it.toString())) }
        item["sqfilesize"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("flac", it.toString())) }
        item["320filesize"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("320k", it.toString())) }
        item["filesize"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("128k", it.toString())) }
        return qs
    }

    /** Parse qualitys from detail/songlist audio_info (fields: filesize_high, filesize_flac, filesize_320, filesize). */
    private fun parseKgDetailQualitys(audio: JsonObject): List<MusicQualityType> {
        val qs = mutableListOf<MusicQualityType>()
        audio["filesize_high"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("flac24bit", it.toString())) }
        audio["filesize_flac"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("flac", it.toString())) }
        audio["filesize_320"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("320k", it.toString())) }
        audio["filesize"]?.jsonPrimitive?.content?.toLongOrNull()?.let { if (it > 0) qs.add(MusicQualityType("128k", it.toString())) }
        return qs
    }
}
