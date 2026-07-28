package cn.guoyujie666.music.compose.core.music.sources

import cn.guoyujie666.music.compose.core.model.KnownQualities
import cn.guoyujie666.music.compose.core.model.KnownSources
import cn.guoyujie666.music.compose.core.model.LyricInfo
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline
import cn.guoyujie666.music.compose.core.model.MusicQualityType
import cn.guoyujie666.music.compose.core.model.OnlineSource
import cn.guoyujie666.music.compose.core.model.Quality
import cn.guoyujie666.music.compose.core.music.CommentResult
import cn.guoyujie666.music.compose.core.music.LeaderboardItem
import cn.guoyujie666.music.compose.core.music.MusicSource
import cn.guoyujie666.music.compose.core.music.SearchResult
import cn.guoyujie666.music.compose.core.music.SongListDetailInfo
import cn.guoyujie666.music.compose.core.music.SongListDetailResult
import cn.guoyujie666.music.compose.core.music.SongListItem
import cn.guoyujie666.music.compose.core.music.SongListTag
import cn.guoyujie666.music.compose.core.music.TipSearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KwSource @Inject constructor() : MusicSource {
    override val sourceId: OnlineSource = KnownSources.KW
    override val sourceName: String = "酷我音乐"
    override val isEnabled: Boolean = true
    override val supportedQualities: List<Quality> = listOf(
        KnownQualities._128K, KnownQualities._320K, KnownQualities.FLAC, KnownQualities.FLAC_24BIT
    )
    override val supportsSongList: Boolean = true
    override val supportsLeaderboard: Boolean = true
    override val supportsComments: Boolean = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    // ── helpers ──────────────────────────────────────────────────

    private fun httpGet(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    private fun httpGetBinary(urlStr: String): ByteArray {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
        return conn.inputStream.readBytes().also { conn.disconnect() }
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val sb = StringBuilder(base).append("?")
        params.forEach { (k, v) ->
            sb.append(URLEncoder.encode(k, "UTF-8")).append("=")
                .append(URLEncoder.encode(v, "UTF-8")).append("&")
        }
        return sb.toString().trimEnd('&')
    }

    private fun htmlDecode(s: String) = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")

    /** Check if a string contains CJK characters (used for lyric language detection) */
    private fun containsCJK(s: String): Boolean {
        return s.any { c ->
            c in '一'..'鿿' || c in '㐀'..'䶿' ||
            c in '豈'..'﫿' || c in '぀'..'ヿ'  // also Japanese kana
        }
    }

    private fun fmtSinger(s: String) = s.replace("&", "、")

    // Ported from src/utils/musicSdk/kw/util.js objStr2JSON: the r.s playlist endpoint
    // answers with pseudo-JSON using single quotes ({'key':'value'}); convert only the
    // quotes in structural positions to double quotes so apostrophes inside values
    // survive. Java requires bounded look-behind, so the JS \s* becomes \s{0,6}.
    private val objStrQuoteRegex = Regex("('(?=(,\\s*')))|('(?=:))|((?<=[:,]\\s{0,6})')|((?<=\\{)')|('(?=\\}))")
    private fun objStr2Json(str: String): String = str.replace(objStrQuoteRegex, "\"")

    private fun parseMusic(item: kotlinx.serialization.json.JsonObject): MusicInfo {
        val name = item["NAME"]?.jsonPrimitive?.content?.let { htmlDecode(it) }
            ?: item["SONGNAME"]?.jsonPrimitive?.content?.let { htmlDecode(it) }
            ?: item["name"]?.jsonPrimitive?.content ?: ""
        val singer = item["ARTIST"]?.jsonPrimitive?.content?.let { fmtSinger(it) }
            ?: item["artist"]?.jsonPrimitive?.content ?: ""
        val rid = item["MUSICRID"]?.jsonPrimitive?.content?.removePrefix("MUSIC_")
            ?: item["musicrid"]?.jsonPrimitive?.content?.removePrefix("MUSIC_") ?: ""
        val dur = (item["DURATION"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: item["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
        val album = item["ALBUM"]?.jsonPrimitive?.content?.let { htmlDecode(it) }
            ?: item["album"]?.jsonPrimitive?.content?.let { htmlDecode(it) } ?: ""
        val albumId = item["ALBUMID"]?.jsonPrimitive?.content
            ?: item["albumid"]?.jsonPrimitive?.content ?: ""

        val minfo = (item["N_MINFO"]?.jsonPrimitive?.content
            ?: item["MINFO"]?.jsonPrimitive?.content
            ?: item["n_minfo"]?.jsonPrimitive?.content ?: "")
        // Filter to standard bitrates only (skip mgg/mflac proprietary formats)
        val qualitys = mutableListOf<MusicQualityType>()
        Regex("level:(\\w+),bitrate:(\\d+),format:(\\w+),size:([\\w.]+)").findAll(minfo).forEach { m ->
            val format = m.groupValues[3]
            if (format in setOf("flac", "mp3", "ogg", "aac")) {
                val br = m.groupValues[2].toIntOrNull() ?: 128
                qualitys.add(MusicQualityType(type = when (br) {
                    2000 -> "flac"; 320 -> "320k"; 192 -> "192k"; 128 -> "128k"; else -> "${br}k"
                }, size = m.groupValues[4]))
            }
        }

        return MusicInfo(
            id = "kw_$rid", name = name, singer = singer, source = "kw",
            interval = if (dur > 0) "${dur / 60}:${(dur % 60).toString().padStart(2, '0')}" else null,
            meta = MusicInfoMetaOnline(songId = rid, albumName = album, picUrl = null, qualitys = qualitys, albumId = albumId)
        )
    }

    // ── Search ───────────────────────────────────────────────────

    override suspend fun searchMusic(keyword: String, page: Int, limit: Int): Result<SearchResult> = runCatching {
        val url = buildUrl("http://search.kuwo.cn/r.s", mapOf(
            "client" to "kt", "all" to keyword, "pn" to "${page - 1}", "rn" to "$limit",
            "uid" to "794762570", "ver" to "kwplayer_ar_9.2.2.1", "vipver" to "1",
            "show_copyright_off" to "1", "newver" to "1", "ft" to "music",
            "cluster" to "0", "strategy" to "2012", "encoding" to "utf8",
            "rformat" to "json", "vergerge" to "1", "mobi" to "1", "issubtitle" to "1"
        ))
        val body = httpGet(url)
        val obj = json.parseToJsonElement(body).jsonObject
        val list = obj["abslist"]?.jsonArray?.map { parseMusic(it.jsonObject) } ?: emptyList()
        SearchResult(list = list, total = obj["TOTAL"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0, page = page, limit = limit)
    }

    override suspend fun tipSearch(keyword: String): Result<TipSearchResult> = runCatching {
        val url = buildUrl("https://tips.kuwo.cn/t.s", mapOf(
            "corp" to "kuwo", "newver" to "3", "p2p" to "1", "notrace" to "0",
            "c" to "mbox", "w" to keyword, "encoding" to "utf8", "rformat" to "json"
        ))
        val body = httpGet(url)
        val obj = json.parseToJsonElement(body).jsonObject
        TipSearchResult(list = obj["WORDITEMS"]?.jsonArray?.mapNotNull {
            it.jsonObject["RELWORD"]?.jsonPrimitive?.content
        } ?: emptyList())
    }

    override suspend fun hotSearch(): Result<List<String>> = runCatching {
        val url = buildUrl("http://hotword.kuwo.cn/hotword.s", mapOf(
            "prod" to "kwplayer_ar_9.3.0.1", "corp" to "kuwo", "newver" to "2",
            "vipver" to "9.3.0.1", "source" to "kwplayer_ar_9.3.0.1_40.apk",
            "p2p" to "1", "notrace" to "0", "uid" to "0", "plat" to "kwplayer_ar",
            "rformat" to "json", "encoding" to "utf8", "tabid" to "1"
        ))
        val body = httpGet(url)
        json.parseToJsonElement(body).jsonObject["tagvalue"]?.jsonArray?.mapNotNull {
            it.jsonObject["key"]?.jsonPrimitive?.content
        } ?: emptyList()
    }

    // ── Leaderboard ───────────────────────────────────────────────
    // Hardcoded board list (ported from kw/leaderboard.js)
    private val kwBoards = listOf(
        LeaderboardItem("kw__93", "飙升榜"), LeaderboardItem("kw__17", "新歌榜"),
        LeaderboardItem("kw__16", "热歌榜"), LeaderboardItem("kw__158", "抖音热歌榜"),
        LeaderboardItem("kw__292", "铃声榜"), LeaderboardItem("kw__284", "热评榜"),
        LeaderboardItem("kw__290", "ACG新歌榜"), LeaderboardItem("kw__286", "台湾KKBOX榜"),
        LeaderboardItem("kw__279", "冬日暖心榜"), LeaderboardItem("kw__281", "巴士随身听榜"),
        LeaderboardItem("kw__255", "KTV点唱榜"), LeaderboardItem("kw__280", "家务进行曲榜"),
        LeaderboardItem("kw__282", "熬夜修仙榜"), LeaderboardItem("kw__283", "枕边轻音乐榜"),
        LeaderboardItem("kw__278", "古风音乐榜"), LeaderboardItem("kw__264", "Vlog音乐榜"),
        LeaderboardItem("kw__242", "电音榜"), LeaderboardItem("kw__187", "流行趋势榜"),
        LeaderboardItem("kw__204", "现场音乐榜"), LeaderboardItem("kw__186", "ACG神曲榜"),
        LeaderboardItem("kw__185", "最强翻唱榜"), LeaderboardItem("kw__26", "经典怀旧榜"),
        LeaderboardItem("kw__104", "华语榜"), LeaderboardItem("kw__182", "粤语榜"),
        LeaderboardItem("kw__22", "欧美榜"), LeaderboardItem("kw__184", "韩语榜"),
        LeaderboardItem("kw__183", "日语榜"), LeaderboardItem("kw__145", "会员畅听榜"),
        LeaderboardItem("kw__153", "网红新歌榜"), LeaderboardItem("kw__64", "影视金曲榜"),
        LeaderboardItem("kw__176", "DJ嗨歌榜"), LeaderboardItem("kw__12", "Billboard榜"),
        LeaderboardItem("kw__49", "iTunes音乐榜"), LeaderboardItem("kw__13", "英国UK榜"),
        LeaderboardItem("kw__164", "百大DJ榜"), LeaderboardItem("kw__265", "韩国Genie榜"),
        LeaderboardItem("kw__14", "韩国M-net榜"), LeaderboardItem("kw__8", "香港电台榜"),
        LeaderboardItem("kw__15", "日本公信榜"), LeaderboardItem("kw__151", "腾讯音乐人原创榜"),
    )
    override suspend fun getLeaderboards() = Result.success(kwBoards)

    // Uses kbangserver.kuwo.cn (unencrypted) — the RN WBD encrypted API
    // fails when JSON body length is not a multiple of 16 (ECB/NoPadding).
    // kbangserver API: stable, returns duration in seconds.
    // Note: a few boards (飙升榜 etc.) may have inaccurate short durations — same as RN.
    override suspend fun getLeaderboardDetail(boardId: String, page: Int, limit: Int) = runCatching {
        val bangid = boardId.removePrefix("kw__")
        val url = "http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&pn=${page - 1}&rn=$limit&type=bang&data=content&id=$bangid&show_copyright_off=0&pcmp4=1&isbang=1"
        val raw = json.parseToJsonElement(httpGet(url)).jsonObject
        val songs = (raw["musiclist"]?.jsonArray ?: emptyList()).map { el ->
            val item = el.jsonObject
            // kbangserver returns two duration fields:
            //   "duration"       — short/inaccurate (e.g. 4s), NOT the real song length
            //   "song_duration"  — actual full song duration in seconds (e.g. 205s)
            val dur = item["song_duration"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: item["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val rid = (item["id"]?.jsonPrimitive?.content ?: "")
            // kbangserver uses "formats" pipe-delimited codes (not n_minfo)
            val formats = item["formats"]?.jsonPrimitive?.content ?: ""
            val qs = parseKwFormats(formats)
            MusicInfo("kw_$rid", htmlDecode(item["name"]?.jsonPrimitive?.content ?: ""),
                fmtSinger(htmlDecode(item["artist"]?.jsonPrimitive?.content ?: "")), "kw",
                if (dur > 0) HttpUtils.formatPlayTime(dur) else null,
                MusicInfoMetaOnline(songId = rid, albumName = htmlDecode(item["album"]?.jsonPrimitive?.content ?: ""),
                    albumId = item["albumid"]?.jsonPrimitive?.content ?: "",
                    picUrl = item["pic"]?.jsonPrimitive?.content, qualitys = qs))
        }
        SearchResult(list = songs, total = raw["num"]?.jsonPrimitive?.content?.toIntOrNull() ?: songs.size, page = page, limit = limit)
    }

    // ── Music URL / Lyric / Pic ───────────────────────────────────

    override suspend fun getMusicUrl(musicInfo: MusicInfo, quality: Quality): Result<String> =
        Result.failure(UnsupportedOperationException("Custom source required"))

    override suspend fun getLyric(musicInfo: MusicInfo): Result<LyricInfo> = runCatching {
        val songId = musicInfo.meta.songId
        // Build XOR-encrypted params (simplified from RN's kw/lyric.js buildParams)
        // lrcx=1 requests word-timing (lxlrc) data
        val rawParams = "user=12345,web,web,web&requester=localhost&req=1&lrcx=1&rid=MUSIC_$songId"
        val key = "yeelion".toByteArray()
        val encrypted = rawParams.toByteArray().mapIndexed { i, b -> (b.toInt() xor key[i % key.size].toInt()).toByte() }.toByteArray()
        val encoded = android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
        val url = "http://newlyric.kuwo.cn/newlyric.lrc?$encoded"
        val resp = httpGetBinary(url)
        // Find \r\n\r\n separator between header and body
        val sep = "\r\n\r\n".toByteArray()
        var bodyStart = -1
        for (i in 0..resp.size - sep.size) {
            if (resp.sliceArray(i until i + sep.size).contentEquals(sep)) { bodyStart = i + sep.size; break }
        }
        if (bodyStart > 0) {
            val compressed = resp.sliceArray(bodyStart until resp.size)
            // Inflate (zlib decompress)
            val inflater = java.util.zip.Inflater()
            inflater.setInput(compressed)
            val inflatedOut = java.io.ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) { val n = inflater.inflate(buf); inflatedOut.write(buf, 0, n) }
            inflater.end()
            val inflatedBytes = inflatedOut.toByteArray()

            // With lrcx=1, the inflated data is base64-encoded and XOR-encrypted again.
            // Decode base64 → XOR decrypt with key → GB18030 text.
            val base64Str = String(inflatedBytes, Charsets.UTF_8).trim()
            val xorInput = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            val xorOutput = ByteArray(xorInput.size)
            for (i in xorInput.indices) {
                xorOutput[i] = (xorInput[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            val lrc = String(xorOutput, java.nio.charset.Charset.forName("GB18030"))
            // Parse KW interleaved LRC format using sortLrcArr logic (ported from RN).
            //
            // The KW API returns an interleaved format where translations are tagged
            // at the NEXT timestamp — not their own:
            //   [00:31.39]                          ← empty placeholder
            //   [00:31.39]When I was young...       ← English main
            //   [00:34.05]年轻犯浑时                  ← Chinese trans (for English at 31.39!)
            //   [00:34.05]My love left to be...     ← English main
            //
            // sortLrcArr: when a duplicate time is found, pop the first occurrence
            // from main, shift its time to the previous main line, and add to translations.
            val rawLines = lrc.split("\n")
            val tagRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{2,3}))?\]""")

            // Build ordered list of (timeStr, timeMs, text) preserving original order
            data class LrcItem(val timeStr: String, val timeMs: Long, val text: String)
            val items = mutableListOf<LrcItem>()
            for (line in rawLines) {
                val match = tagRegex.find(line) ?: continue
                val tag = match.value
                val text = line.substring(match.range.last + 1).trim()
                val min = match.groupValues[1].toInt()
                val sec = match.groupValues[2].toInt()
                val ms = match.groupValues[3].takeIf { it.isNotEmpty() }?.let {
                    val value = it.toInt()
                    if (it.length == 2) value * 10 else value
                } ?: 0
                val timeMs = (min * 60_000L) + (sec * 1_000L) + ms
                items.add(LrcItem(tag, timeMs, text))
            }

            // sortLrcArr: separate main lyrics from translations with time adjustment.
            // RN port: when a duplicate time tag is found, pop the first occurrence,
            // shift its time to the previous main line's time, and add to translations.
            val lrcList = mutableListOf<LrcItem>()   // main lyrics
            val lrcTList = mutableListOf<LrcItem>()  // translations
            val seenTags = mutableSetOf<String>()

            for (item in items) {
                if (seenTags.add(item.timeStr)) {
                    // First occurrence of this time tag → goes to main
                    lrcList.add(item)
                } else {
                    // Duplicate time tag → pop first, adjust time to previous, add to trans
                    if (lrcList.size < 2) continue
                    // If first was an empty placeholder (only word tags / whitespace),
                    // replace it with the second instead of popping
                    val firstItem = lrcList.last()
                    val strippedFirst = firstItem.text.replace(Regex("""<[^>]*>"""), "").trim()
                    if (strippedFirst.isEmpty()) {
                        // First was empty placeholder → replace with second (real text)
                        lrcList[lrcList.size - 1] = item
                    } else {
                        val tItem = lrcList.removeAt(lrcList.size - 1)
                        val adjustedTime = lrcList[lrcList.size - 1].timeMs
                        lrcTList.add(tItem.copy(timeMs = adjustedTime))
                        lrcList.add(item)
                    }
                }
            }

            // Build lxlyric from decoded word timing (before stripping tags from main lyric)
            // Extract [kuwo:XXX] from raw LRC for offset/offset2
            val kuwoRegex = Regex("""\[kuwo:([^\]]+)\]""")
            val kuwoMatch = kuwoRegex.find(lrc)
            val kuwoValue = kuwoMatch?.groupValues?.get(1)?.trim()?.substringBefore("][")?.toIntOrNull(8)
            val lxOffset = kuwoValue?.let { it / 10 } ?: 2
            val lxOffset2 = kuwoValue?.let { it % 10 } ?: 4
            val wordTagAny = Regex("""<[^>]*>""")
            val lxlyricInput = lrcList
                .filter { it.text.replace(wordTagAny, "").trim().isNotEmpty() }
                .joinToString("\n") { "${it.timeStr}${it.text}" }
            val lxlyric = decodeKwLxLyric(lxlyricInput, lxOffset, lxOffset2)

            // Build clean main lyric (strip word tags, filter empty lines)
            val cleanLyric = lrcList
                .map { "${it.timeStr}${it.text.replace(wordTagAny, "")}" }
                .filter { it.substringAfter("]").trim().isNotEmpty() }
                .joinToString("\n")

            // Build translation lyric (filter out empty lines, use centiseconds to match original LRC format)
            val tlyric = lrcTList
                .filter { it.text.replace(Regex("""<[^>]*>"""), "").trim().isNotEmpty() }
                .joinToString("\n") { t ->
                    val totalCs = t.timeMs / 10  // centiseconds
                    val min = totalCs / 6000
                    val sec = (totalCs % 6000) / 100
                    val cs = totalCs % 100
                    val tag = "[%02d:%02d.%02d]".format(min, sec, cs)
                    // Strip word timing tags from translation text
                    val cleanText = t.text.replace(Regex("""<[^>]*>"""), "")
                    "$tag${cleanText}"
                }

            LyricInfo(lyric = cleanLyric, tlyric = tlyric.ifEmpty { null }, lxlyric = lxlyric)
        } else {
            LyricInfo()
        }
    }

    override suspend fun getPic(musicInfo: MusicInfo): Result<String> = runCatching {
        val url = buildUrl("http://artistpicserver.kuwo.cn/pic.web", mapOf(
            "corp" to "kuwo", "type" to "rid_pic", "pictype" to "500", "size" to "500",
            "rid" to musicInfo.meta.songId
        ))
        val r = httpGet(url); if (r.startsWith("http")) r else ""
    }

    // ── SongList ──────────────────────────────────────────────────

    // Ported from src/utils/musicSdk/kw/songList.js getTag + getHotTag
    override suspend fun getSongListTags(): Result<List<SongListTag>> = runCatching {
        val tagsUrl = "http://wapi.kuwo.cn/api/pc/classify/playlist/getTagList?cmd=rcm_keyword_playlist&user=0&prod=kwplayer_pc_9.0.5.0&vipver=9.0.5.0&source=kwplayer_pc_9.0.5.0&loginUid=0&loginSid=0&appUid=76039576"
        val body = httpGet(tagsUrl)
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["code"]?.jsonPrimitive?.intOrNull != 200) return@runCatching emptyList()
        (obj["data"]?.jsonArray ?: emptyList()).map { g ->
            val go = g.jsonObject
            SongListTag(
                name = go["name"]?.jsonPrimitive?.content ?: "",
                id = go["id"]?.jsonPrimitive?.content ?: "",
                children = (go["data"]?.jsonArray ?: emptyList()).map { t ->
                    val to = t.jsonObject
                    SongListTag(
                        name = to["name"]?.jsonPrimitive?.content ?: "",
                        id = "${to["id"]?.jsonPrimitive?.content ?: ""}-${to["digest"]?.jsonPrimitive?.content ?: ""}",
                        children = emptyList())
                }
            )
        }
    }

    // Ported from src/utils/musicSdk/kw/songList.js getList — three URL paths
    // based on the tag's digest (the second part of tagId after '-'):
    //   no tagId        → getRcmPlayList
    //   digest="10000"  → getTagPlayList  (standard API, filterList)
    //   digest="43"     → er.s            (mobile API, raw array, filterList2)
    override suspend fun getSongList(sortId: String, tagId: String, page: Int): Result<List<SongListItem>> = runCatching {
        val (tId, tType) = if (tagId.isNotEmpty()) {
            val parts = tagId.split("-", limit = 2)
            Pair(parts.getOrElse(0) { "" }.ifEmpty { null }, parts.getOrElse(1) { "" }.takeIf { it.isNotEmpty() })
        } else Pair(null, null)

        // Path 3: "专区" tags (digest=43, e.g. "经典老歌专区") — uses the er.s mobile API
        // The er.s API returns ALL items at once (no pn/rn params), so only page 1 works.
        if (tId != null && tType == "43") {
            if (page > 1) return@runCatching emptyList()  // er.s doesn't paginate
            val body = httpGet("http://mobileinterfaces.kuwo.cn/er.s?type=get_pc_qz_data&f=web&id=$tId&prod=pc")
            val rawArray = json.parseToJsonElement(body).jsonArray ?: return@runCatching emptyList()
            val allowedTypes = setOf("songlist", "list", "album")
            val result = rawArray.flatMap { outer ->
                val outerObj = outer.jsonObject
                (outerObj["list"]?.jsonArray ?: emptyList()).mapNotNull { subEl ->
                    val item = subEl.jsonObject
                    val itemType = item["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    if (itemType !in allowedTypes) return@mapNotNull null
                    // Use inner item's digest (RN filterList2 uses item.digest from inner loop)
                    val innerDigest = item["digest"]?.jsonPrimitive?.content ?: ""
                    SongListItem(
                        id = "digest-${innerDigest}__${item["id"]?.jsonPrimitive?.content ?: ""}",
                        name = htmlDecode(item["name"]?.jsonPrimitive?.content ?: ""),
                        pic = item["img"]?.jsonPrimitive?.contentOrNull,
                        playCount = 0L,
                        author = htmlDecode(item["uname"]?.jsonPrimitive?.content ?: ""),
                        desc = htmlDecode(item["desc"]?.jsonPrimitive?.contentOrNull ?: ""),
                        source = "kw")
                }
            }.toMutableList()
            // Batch-fetch play counts from detail API for 专区 items
            for (i in result.indices) {
                try {
                    val realId = result[i].id.substringAfterLast("__").ifEmpty { result[i].id }
                    val db = httpGet("http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=$realId&pn=0&rn=1&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1")
                    val dobj = json.parseToJsonElement(db).jsonObject
                    if (dobj["result"]?.jsonPrimitive?.content == "ok") {
                        val pc = dobj["playnum"]?.jsonPrimitive?.longOrNull
                        if (pc != null && pc > 0) result[i] = result[i].copy(playCount = pc)
                    }
                } catch (_: Exception) {}
            }
            return@runCatching result
        }

        // Path 1 (no tag) / Path 2 (digest=10000, regular tags): standard wapi API
        val url = if (tId == null)
            "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?loginUid=0&loginSid=0&appUid=76039576&&pn=$page&rn=36&order=$sortId"
        else
            "http://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList?loginUid=0&loginSid=0&appUid=76039576&pn=$page&id=$tId&rn=36"
        val body = httpGet(url)
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["code"]?.jsonPrimitive?.intOrNull != 200) return@runCatching emptyList()
        val listData = obj["data"]?.jsonObject?.get("data")?.jsonArray ?: return@runCatching emptyList()
        listData.map { el ->
            val item = el.jsonObject
            SongListItem(
                id = "digest-${item["digest"]?.jsonPrimitive?.content ?: ""}__${item["id"]?.jsonPrimitive?.content ?: ""}",
                name = htmlDecode(item["name"]?.jsonPrimitive?.content ?: ""),
                pic = item["img"]?.jsonPrimitive?.contentOrNull,
                playCount = item["listencnt"]?.jsonPrimitive?.longOrNull ?: 0L,
                author = htmlDecode(item["uname"]?.jsonPrimitive?.content ?: ""),
                desc = htmlDecode(item["desc"]?.jsonPrimitive?.contentOrNull ?: ""),
                source = "kw")
        }
    }
    // Ported from src/utils/musicSdk/kw/songList.js getListDetail — routes by digest
    override suspend fun getSongListDetail(listId: String, page: Int, limit: Int): Result<SongListDetailResult> = runCatching {
        // Parse digest from compound id: "digest-{digest}__{id}" or plain link/ID
        val realId: String; val digest: String
        if (listId.startsWith("digest-")) {
            val parts = listId.removePrefix("digest-").split("__", limit = 2)
            digest = parts.getOrElse(0) { "" }; realId = parts.getOrElse(1) { listId }
        } else if (Regex("""/playlist_detail/(\d+)""").containsMatchIn(listId)) {
            digest = "8"; realId = Regex("""/playlist_detail/(\d+)""").find(listId)!!.groupValues[1]
        } else if (listId.all { it.isDigit() }) {
            digest = "8"; realId = listId
        } else { digest = "8"; realId = listId.substringAfterLast("__").ifEmpty { listId } }

        // digest=13: album (kw/album.js getAlbumListDetail)
        if (digest == "13") {
            val url = "http://search.kuwo.cn/r.s?pn=${page - 1}&rn=$limit&stype=albuminfo&albumid=$realId&show_copyright_off=0&encoding=utf&vipver=MUSIC_9.1.0"
            val body = objStr2Json(httpGet(url))
            val obj = json.parseToJsonElement(body).jsonObject
            if (obj["musiclist"] == null) return@runCatching SongListDetailResult()
            val songs = (obj["musiclist"]?.jsonArray ?: emptyList()).map { el ->
                val item = el.jsonObject; val dur = item["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val rid = (item["id"]?.jsonPrimitive?.content ?: item["rid"]?.jsonPrimitive?.content ?: "")
                val qs = parseKwFormats(item["formats"]?.jsonPrimitive?.content ?: "")
                MusicInfo("kw_$rid", htmlDecode(item["name"]?.jsonPrimitive?.content ?: ""),
                    fmtSinger(item["artist"]?.jsonPrimitive?.content ?: ""), "kw",
                    if (dur > 0) "${dur / 60}:${(dur % 60).toString().padStart(2, '0')}" else null,
                    MusicInfoMetaOnline(songId = rid, albumName = htmlDecode(item["album"]?.jsonPrimitive?.content ?: ""),
                        albumId = item["albumid"]?.jsonPrimitive?.content ?: "", qualitys = qs))
            }
            return@runCatching SongListDetailResult(list = songs, total = obj["songnum"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                page = page, limit = limit,
                info = SongListDetailInfo(name = htmlDecode(obj["name"]?.jsonPrimitive?.content ?: ""),
                    pic = obj["img"]?.jsonPrimitive?.contentOrNull ?: obj["hts_img"]?.jsonPrimitive?.contentOrNull,
                    desc = htmlDecode(obj["info"]?.jsonPrimitive?.content ?: ""),
                    author = htmlDecode(obj["artist"]?.jsonPrimitive?.content ?: ""),
                    playCount = obj["playnum"]?.jsonPrimitive?.longOrNull ?: 0L))
        }

        // digest=8 (default): nplserver playlist
        val url = "http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=$realId&pn=${page - 1}&rn=$limit&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1"
        val body = httpGet(url)
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["result"]?.jsonPrimitive?.content != "ok") return@runCatching SongListDetailResult()
        val songs = (obj["musiclist"]?.jsonArray ?: emptyList()).map { el ->
            val item = el.jsonObject; val dur = item["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val rid = (item["id"]?.jsonPrimitive?.content ?: item["rid"]?.jsonPrimitive?.content ?: "")
            // nplserver returns quality in formats or n_minfo
            val qs = parseKwFormats(item["formats"]?.jsonPrimitive?.content ?: "")
                .ifEmpty { parseKwMinfo(item["n_minfo"]?.jsonPrimitive?.content ?: item["N_MINFO"]?.jsonPrimitive?.content ?: "") }
            MusicInfo("kw_$rid", htmlDecode(item["name"]?.jsonPrimitive?.content ?: ""),
                fmtSinger(item["artist"]?.jsonPrimitive?.content ?: ""), "kw",
                if (dur > 0) "${dur / 60}:${(dur % 60).toString().padStart(2, '0')}" else null,
                MusicInfoMetaOnline(songId = rid, albumName = htmlDecode(item["album"]?.jsonPrimitive?.content ?: ""),
                    albumId = item["albumid"]?.jsonPrimitive?.content ?: "", qualitys = qs))
        }
        SongListDetailResult(list = songs, total = obj["total"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            page = page, limit = limit,
            info = SongListDetailInfo(name = htmlDecode(obj["title"]?.jsonPrimitive?.content ?: ""),
                pic = obj["pic"]?.jsonPrimitive?.contentOrNull,
                desc = htmlDecode(obj["info"]?.jsonPrimitive?.content ?: ""),
                author = htmlDecode(obj["uname"]?.jsonPrimitive?.content ?: ""),
                playCount = obj["playnum"]?.jsonPrimitive?.longOrNull ?: 0L))
    }
    // Ported from src/utils/musicSdk/kw/songList.js search()
    override suspend fun searchSongList(keyword: String, page: Int): Result<List<SongListItem>> = runCatching {
        val url = buildUrl("http://search.kuwo.cn/r.s", mapOf(
            "all" to keyword, "pn" to "${page - 1}", "rn" to "20",
            "rformat" to "json", "encoding" to "utf8", "ver" to "mbox",
            "vipver" to "MUSIC_8.7.7.0_BCS37", "plat" to "pc", "devid" to "28156413",
            "ft" to "playlist", "pay" to "0", "needliveshow" to "0"
        ))
        val obj = json.parseToJsonElement(objStr2Json(httpGet(url))).jsonObject
        (obj["abslist"]?.jsonArray ?: emptyList()).map { el ->
            val item = el.jsonObject
            SongListItem(
                id = item["playlistid"]?.jsonPrimitive?.content ?: "",
                name = htmlDecode(item["name"]?.jsonPrimitive?.content ?: ""),
                pic = item["pic"]?.jsonPrimitive?.content,
                playCount = item["playcnt"]?.jsonPrimitive?.longOrNull ?: 0L,
                author = htmlDecode(item["nickname"]?.jsonPrimitive?.content ?: ""),
                desc = htmlDecode(item["intro"]?.jsonPrimitive?.content ?: ""),
                source = "kw")
        }
    }

    // ── Comments ──────────────────────────────────────────────────

    override suspend fun getHotComments(musicInfo: MusicInfo, page: Int, limit: Int) = Result.success(CommentResult())
    override suspend fun getNewComments(musicInfo: MusicInfo, page: Int, limit: Int) = Result.success(CommentResult())

    /** Parse quality from kbangserver "formats" pipe-delimited codes (e.g. "MP3128|MP3H|ALFLAC"). */
    private fun parseKwFormats(formats: String): List<MusicQualityType> {
        if (formats.isBlank()) return emptyList()
        val qs = mutableListOf<MusicQualityType>()
        val codes = formats.split("|").toSet()
        if (codes.any { it.contains("HIRFLAC") || it.contains("FLAC24") }) qs.add(MusicQualityType("flac24bit", ""))
        if (codes.any { it.contains("ALFLAC") || it.contains("FLAC") }) qs.add(MusicQualityType("flac", ""))
        if (codes.any { it == "MP3H" || it == "MP3H128" || it == "MP3H192" }) qs.add(MusicQualityType("320k", ""))
        if (codes.any { it == "MP3128" || it.contains("MP3") }) qs.add(MusicQualityType("128k", ""))
        return qs
    }

    /** Parse quality from n_minfo format string (regex, same as parseMusic). */
    /**
     * Decode KW word-level timing from encoded &lt;offset1,offset2&gt; format
     * to standard lxlrc &lt;startMs,durationMs&gt; format.
     *
     * Ported from RN src/utils/musicSdk/kw/util.js lrcTools.parse().
     * The [kuwo:XXX] tag (XXX parsed as octal) provides global offset/offset2
     * that are used to decode each word's encoded offset pair into real timing.
     */
    private fun decodeKwLxLyric(lrc: String, offset: Int, offset2: Int): String? {
        if (offset == 0 || offset2 == 0) return null

        // Regex patterns
        val wordLineRegex = Regex("""^(\[\d{1,2}:.*\d{1,4}\])\s*(\S+(?:\s+\S+)*)?\s*""")
        val wordTimeAllRegex = Regex("""<(-?\d+),(-?\d+)(?:,-?\d+)?>""")
        val tagLineRegex = Regex("""\[(ver|ti|ar|al|offset|by|kuwo):\s*(\S+(?:\s+\S+)*)\s*\]""")

        val tags = mutableListOf<String>()
        val resultLines = mutableListOf<String>()

        for (line in lrc.lines()) {
            val trimmed = line.trim()
            if (trimmed.length < 6) continue

            // Try to parse as word-timed line
            val wordLineMatch = wordLineRegex.find(trimmed)
            if (wordLineMatch != null) {
                val time = wordLineMatch.groupValues[1]
                var words = wordLineMatch.groupValues[2]

                val wordTimes = wordTimeAllRegex.findAll(words).toList()
                if (wordTimes.isEmpty()) continue

                data class WordInfo(
                    var startTime: Int, var endTime: Int,
                    val timeStr: String, var newTimeStr: String? = null
                )
                var preWord: WordInfo? = null

                for (wt in wordTimes) {
                    val o1 = wt.groupValues[1].toInt()
                    val o2 = wt.groupValues[2].toInt()
                    val startTime = Math.abs((o1 + o2) / (offset * 2))
                    var endTime = Math.abs((o1 - o2) / (offset2 * 2)) + startTime

                    // Adjust previous word if current starts before previous ends
                    if (preWord != null && startTime < preWord.endTime) {
                        preWord.endTime = startTime
                        if (preWord.startTime > preWord.endTime) {
                            preWord.startTime = preWord.endTime
                        }
                        preWord.newTimeStr = "<${preWord.startTime},${preWord.endTime - preWord.startTime}>"
                    }

                    val wordInfo = WordInfo(
                        startTime = startTime, endTime = endTime,
                        timeStr = "<$startTime,${endTime - startTime}>"
                    )

                    // Replace encoded tag with decoded tag in text
                    words = words.replace(wt.value, wordInfo.timeStr)
                    if (preWord?.newTimeStr != null) {
                        words = words.replace(preWord.timeStr, preWord.newTimeStr!!)
                    }
                    preWord = wordInfo
                }
                resultLines.add(time + words)
                continue
            }

            // Check for tag lines (collect non-kuwo tags)
            val tagMatch = tagLineRegex.find(trimmed)
            if (tagMatch != null && tagMatch.groupValues[1] != "kuwo") {
                tags.add(trimmed)
            }
        }

        if (resultLines.isEmpty()) return null
        var lrcs = resultLines.joinToString("\n")
        if (tags.isNotEmpty()) lrcs = "${tags.joinToString("\n")}\n$lrcs"
        return lrcs
    }

    private fun parseKwMinfo(minfo: String): List<MusicQualityType> {
        if (minfo.isBlank()) return emptyList()
        val qs = mutableListOf<MusicQualityType>()
        Regex("level:(\\w+),bitrate:(\\d+),format:(\\w+),size:([\\w.]+)").findAll(minfo).forEach { m ->
            val br = m.groupValues[2].toIntOrNull() ?: 128
            val type = when { br >= 4000 -> "flac24bit"; br >= 2000 -> "flac"; br >= 320 -> "320k"; else -> "128k" }
            if (qs.none { it.type == type }) qs.add(MusicQualityType(type, m.groupValues[4]))
        }
        return qs
    }
}
