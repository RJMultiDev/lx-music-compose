package cn.guoyujie666.music.compose.core.music.sources

import cn.guoyujie666.music.compose.core.model.*
import cn.guoyujie666.music.compose.core.music.*
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WySource @Inject constructor() : MusicSource {
    override val sourceId: OnlineSource = KnownSources.WY
    override val sourceName: String = "网易云音乐"
    override val isEnabled: Boolean = true
    override val supportedQualities: List<Quality> = listOf(KnownQualities._128K, KnownQualities._320K, KnownQualities.FLAC, KnownQualities.FLAC_24BIT)
    override val supportsSongList: Boolean = true
    override val supportsLeaderboard: Boolean = true
    override val supportsComments: Boolean = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private fun httpGet(urlStr: String, extraHeaders: Map<String, String> = emptyMap()): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000
            setInstanceFollowRedirects(true)
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
            setRequestProperty("Referer", "https://music.163.com/")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    private fun httpPost(url: String, body: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
            requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
            setRequestProperty("Referer", "https://music.163.com/")
            outputStream.write(body.toByteArray())
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    // ── EAPI encryption (ported from wy/utils/crypto.js eapi()) ──

    private val eapiKey = "e82ckenh8dichen8".toByteArray(Charsets.UTF_8)

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    /** AES-128-ECB with PKCS5Padding (matches Java's Cipher.getInstance("AES") default). */
    private fun aesEcbEncrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(eapiKey, "AES"))
        return cipher.doFinal(data)
    }

    /** EAPI params encryption. Returns hex params string (raw cipher bytes → hex uppercased). */
    private fun eapi(url: String, jsonText: String): String {
        val message = "nobody${url}use${jsonText}md5forencrypt"
        val digest = md5(message)
        val toEncrypt = "${url}-36cd479b6b5-${jsonText}-36cd479b6b5-${digest}"
        val encrypted = aesEcbEncrypt(toEncrypt.toByteArray(Charsets.UTF_8))
        return encrypted.joinToString("") { "%02X".format(it) }
    }

    /** POST to EAPI batch endpoint. `dataAsJson` must be valid JSON string. */
    private fun eapiPost(apiPath: String, dataAsJson: String): String {
        val params = eapi(apiPath, dataAsJson)
        val body = "params=${URLEncoder.encode(params, "UTF-8")}"
        val conn = (URL("https://interface3.music.163.com/eapi/batch").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
            requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36")
            setRequestProperty("Origin", "https://music.163.com")
            setRequestProperty("Referer", "https://music.163.com/")
            outputStream.write(body.toByteArray())
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    // ── WeAPI encryption (ported from wy/utils/crypto.js weapi()) ──

    private val wePresetKey = "0CoJUm6Qyw8W8jud".toByteArray(Charsets.UTF_8)
    private val weIv = "0102030405060708".toByteArray(Charsets.UTF_8)
    private val wePublicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ3\n" +
        "7BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvakl\n" +
        "V8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44o\n" +
        "ncaTWz7OBGLbCiK45wIDAQAB\n-----END PUBLIC KEY-----"

    private fun aesCbcPkcs7(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun rsaNoPadding(data: ByteArray): ByteArray {
        val keyBytes = Base64.getDecoder().decode(
            wePublicKeyPem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replace("\n", ""))
        val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
        val key = java.security.KeyFactory.getInstance("RSA").generatePublic(keySpec)
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        // Input must be exactly 128 bytes for RSA-1024
        return cipher.doFinal(data)
    }

    /** WeAPI encryption. Returns {params, encSecKey} as URL-encoded body string.
     *
     *  Critical: RN aesEncryptSync(text, key, iv, mode) base64-DECODES all inputs
     *  before encrypting, then base64-ENCODES the output. So the effective plaintext
     *  for the outer AES is innerB64 bytes (NOT double-base64'd), and the key is
     *  secretKey bytes (NOT base64'd). Python reference confirmed correct.
     */
    private fun weapi(data: Map<String, JsonElement>): String {
        val text = json.encodeToString(JsonObject.serializer(), JsonObject(data))
        val secretKey = (1..16).map { "0123456789abcdef"[kotlin.random.Random.nextInt(16)] }.joinToString("")
        val textBytes = text.toByteArray(Charsets.UTF_8)

        // Inner: AES-CBC(text UTF-8 bytes, presetKey, iv)
        // RN: aesEncrypt(btoa(text), btoa(presetKey), btoa(iv))
        //   → native module base64-decodes all inputs → encrypts raw text bytes
        val inner = aesCbcPkcs7(textBytes, wePresetKey, weIv)
        val innerB64 = Base64.getEncoder().encodeToString(inner)

        // Outer: AES-CBC(innerB64 bytes, secretKey bytes, iv)
        // RN: aesEncrypt(btoa(innerB64), btoa(secretKey), btoa(iv))
        //   → native module base64-decodes all inputs → encrypts innerB64 bytes
        val outer = aesCbcPkcs7(innerB64.toByteArray(Charsets.UTF_8), secretKey.toByteArray(Charsets.UTF_8), weIv)
        val params = Base64.getEncoder().encodeToString(outer)

        // encSecKey: rsaEncrypt(Buffer.from(secretKey).reverse(), publicKey).toString('hex')
        //   → raw reversed secretKey bytes, left-padded to 128, RSA/ECB/NoPadding → hex
        val reversed = secretKey.reversed().toByteArray(Charsets.UTF_8)
        val padded = ByteArray(128).apply { System.arraycopy(reversed, 0, this, 128 - reversed.size, reversed.size) }
        val rsaResult = rsaNoPadding(padded)
        val encSecKey = rsaResult.joinToString("") { "%02x".format(it) }

        return "params=${URLEncoder.encode(params, "UTF-8")}&encSecKey=${URLEncoder.encode(encSecKey, "UTF-8")}"
    }

    /** POST to WeAPI endpoint. */
    private fun weapiPost(url: String, data: Map<String, JsonElement>): String {
        val body = weapi(data)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000
            requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36")
            setRequestProperty("Origin", "https://music.163.com")
            setRequestProperty("Referer", "https://music.163.com/")
            setRequestProperty("Accept-Encoding", "identity") // Disable gzip to avoid decompression issues
        }
        // Write body and flush — don't close outputStream before reading response
        val os = conn.outputStream
        os.write(body.toByteArray())
        os.flush()
        val code = conn.responseCode
        val contentLen = conn.contentLength
        val encoding = conn.contentEncoding
        android.util.Log.d("WyPic", "weapi http status=$code, contentLength=$contentLen, encoding=$encoding")
        val resp = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        os.close()
        conn.disconnect()
        return resp
    }

    // Search: try EAPI first (has quality), fall back to public API
    override suspend fun searchMusic(keyword: String, page: Int, limit: Int): Result<SearchResult> = runCatching {
        val offset = limit * (page - 1)
        // Try EAPI first
        try {
            val reqJson = json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("keyword", JsonPrimitive(keyword))
                put("needCorrect", JsonPrimitive("1"))
                put("channel", JsonPrimitive("typing"))
                put("offset", JsonPrimitive(offset))
                put("scene", JsonPrimitive("normal"))
                put("total", JsonPrimitive(page == 1))
                put("limit", JsonPrimitive(limit))
            })
            val resp = eapiPost("/api/search/song/list/page", reqJson)
            val obj = json.parseToJsonElement(resp).jsonObject
            val resources = obj["data"]?.jsonObject?.get("resources")?.jsonArray
            if (obj["code"]?.jsonPrimitive?.content?.toIntOrNull() == 200 && resources != null) {
                val list = resources.mapNotNull { res ->
                    val r = res.jsonObject
                    val base = r["baseInfo"]?.jsonObject?.get("simpleSongData")?.jsonObject ?: return@mapNotNull null
                    val id = r["resourceId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = base["name"]?.jsonPrimitive?.content ?: ""
                    val ar = base["ar"]?.jsonArray
                    val singer = ar?.joinToString("/") { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" } ?: ""
                    val al = base["al"]?.jsonObject
                    val dur = base["dt"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    MusicInfo("wy_$id", name, singer, "wy",
                        if (dur > 0) "${dur / 60000}:${((dur % 60000) / 1000).toString().padStart(2, '0')}" else null,
                        MusicInfoMetaOnline(songId = id,
                            albumName = al?.get("name")?.jsonPrimitive?.content ?: "",
                            albumId = al?.get("id")?.jsonPrimitive?.content ?: "",
                            picUrl = al?.get("picUrl")?.jsonPrimitive?.content,
                            qualitys = parseWyQuality(base)))
                }
                return@runCatching SearchResult(list = list, total = obj["data"]?.jsonObject?.get("totalCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
            }
        } catch (_: Exception) { /* fall through to public API */ }

        // Fallback: public API (no quality, but reliable)
        val body = httpPost("https://music.163.com/api/search/get",
            "s=${URLEncoder.encode(keyword, "UTF-8")}&type=1&offset=$offset&limit=$limit&total=true")
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["code"]?.jsonPrimitive?.content?.toIntOrNull() != 200) return@runCatching SearchResult()
        val songs = obj["result"]?.jsonObject?.get("songs")?.jsonArray ?: return@runCatching SearchResult()
        val list = songs.map { it.jsonObject }.map { song ->
            val name = song["name"]?.jsonPrimitive?.content ?: ""
            val singer = song["artists"]?.jsonArray?.joinToString("/") { a -> a.jsonObject["name"]?.jsonPrimitive?.content ?: "" } ?: ""
            val id = song["id"]?.jsonPrimitive?.content ?: ""
            val album = song["album"]?.jsonObject
            val dur = song["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            MusicInfo(id = "wy_$id", name = name, singer = singer, source = "wy",
                interval = if (dur > 0) "${dur / 60000}:${((dur % 60000) / 1000).toString().padStart(2, '0')}" else null,
                meta = MusicInfoMetaOnline(songId = id,
                    albumName = album?.get("name")?.jsonPrimitive?.content ?: "",
                    albumId = album?.get("id")?.jsonPrimitive?.content ?: ""))
        }
        val total = obj["result"]?.jsonObject?.get("songCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        SearchResult(list = list, total = total)
    }

    override suspend fun hotSearch(): Result<List<String>> = runCatching {
        val reqJson = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("id", JsonPrimitive("HOT_SEARCH_SONG#@#"))
        })
        val resp = eapiPost("/api/search/chart/detail", reqJson)
        val obj = json.parseToJsonElement(resp).jsonObject
        obj["data"]?.jsonObject?.get("itemList")?.jsonArray?.mapNotNull {
            it.jsonObject["searchWord"]?.jsonPrimitive?.content
        } ?: emptyList()
    }

    override suspend fun tipSearch(keyword: String): Result<TipSearchResult> = runCatching {
        val resp = weapiPost("https://music.163.com/weapi/search/suggest/web", mapOf(
            "s" to JsonPrimitive(keyword)
        ))
        val obj = json.parseToJsonElement(resp).jsonObject
        if (obj["code"]?.jsonPrimitive?.intOrNull != 200) return@runCatching TipSearchResult()
        val songs = obj["result"]?.jsonObject?.get("songs")?.jsonArray
        val list = songs?.mapNotNull { item ->
            val name = item.jsonObject["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val artists = item.jsonObject["artists"]?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content
            }?.joinToString("/") ?: ""
            if (artists.isNotEmpty()) "$name - $artists" else name
        } ?: emptyList()
        TipSearchResult(list = list)
    }
    // Hardcoded board list (ported from wy/leaderboard.js topList)
    override suspend fun getLeaderboards() = Result.success(listOf(
        LeaderboardItem("wy__19723756", "飙升榜"), LeaderboardItem("wy__3779629", "新歌榜"),
        LeaderboardItem("wy__2884035", "原创榜"), LeaderboardItem("wy__3778678", "热歌榜"),
        LeaderboardItem("wy__991319590", "说唱榜"), LeaderboardItem("wy__71384707", "古典榜"),
        LeaderboardItem("wy__1978921795", "电音榜"), LeaderboardItem("wy__5453912201", "黑胶VIP爱听榜"),
        LeaderboardItem("wy__71385702", "ACG榜"), LeaderboardItem("wy__745956260", "韩语榜"),
        LeaderboardItem("wy__10520166", "国电榜"), LeaderboardItem("wy__180106", "UK排行榜周榜"),
        LeaderboardItem("wy__60198", "美国Billboard榜"), LeaderboardItem("wy__3812895", "Beatport电子舞曲榜"),
        LeaderboardItem("wy__21845217", "KTV唛榜"), LeaderboardItem("wy__60131", "日本Oricon榜"),
        LeaderboardItem("wy__2809513713", "欧美热歌榜"), LeaderboardItem("wy__2809577409", "欧美新歌榜"),
        LeaderboardItem("wy__27135204", "法国NRJ周榜"), LeaderboardItem("wy__3001835560", "ACG动画榜"),
        LeaderboardItem("wy__3001795926", "ACG游戏榜"), LeaderboardItem("wy__3001890046", "ACG VOCALOID榜"),
        LeaderboardItem("wy__3112516681", "中国新乡村音乐榜"), LeaderboardItem("wy__5059644681", "日语榜"),
        LeaderboardItem("wy__5059633707", "摇滚榜"), LeaderboardItem("wy__5059642708", "国风榜"),
        LeaderboardItem("wy__5338990334", "潜力爆款榜"), LeaderboardItem("wy__5059661515", "民谣榜"),
        LeaderboardItem("wy__6688069460", "听歌识曲榜"), LeaderboardItem("wy__6723173524", "网络热歌榜"),
        LeaderboardItem("wy__6732051320", "俄语榜"), LeaderboardItem("wy__6732014811", "越南语榜"),
        LeaderboardItem("wy__6886768100", "中文DJ榜"), LeaderboardItem("wy__6939992364", "俄罗斯top hit榜"),
        LeaderboardItem("wy__7095271308", "泰语榜"), LeaderboardItem("wy__7356827205", "BEAT排行榜"),
        LeaderboardItem("wy__7603212484", "LOOK直播歌曲榜"), LeaderboardItem("wy__7775163417", "赏音榜"),
        LeaderboardItem("wy__7785123708", "黑胶VIP新歌榜"), LeaderboardItem("wy__7785066739", "黑胶VIP热歌榜"),
        LeaderboardItem("wy__7785091694", "黑胶VIP爱搜榜"),
    ))
    // Leaderboard: EAPI playlist detail + song detail (same eapiPost as search).
    override suspend fun getLeaderboardDetail(boardId: String, page: Int, limit: Int): Result<SearchResult> = runCatching {
        val topId = boardId.removePrefix("wy__")
        val TAG = "WyLb"
        android.util.Log.e(TAG, "=== start board=$topId ===")

        // Path 1: EAPI playlist detail
        try {
            android.util.Log.e(TAG, "trying EAPI playlist detail...")
            val plReq = """{"id":$topId,"n":100000,"p":1}"""
            val plResp = eapiPost("/api/v3/playlist/detail", plReq)
            android.util.Log.e(TAG, "EAPI playlist resp len=${plResp.length}, first 200=${plResp.take(200)}")
            val plObj = json.parseToJsonElement(plResp).jsonObject
            val code = plObj["code"]?.jsonPrimitive?.content
            android.util.Log.e(TAG, "EAPI playlist code=$code")
            val trackIds = plObj["playlist"]?.jsonObject?.get("trackIds")?.jsonArray
            val count = trackIds?.size ?: 0
            android.util.Log.e(TAG, "EAPI playlist trackIds count=$count")
            if (trackIds != null && trackIds.isNotEmpty()) {
                val ids = trackIds.map { it.jsonObject["id"]?.jsonPrimitive?.content ?: "" }.filter { it.isNotEmpty() }
                android.util.Log.e(TAG, "fetching song detail for ${ids.size} tracks, first 3 ids: ${ids.take(3)}")
                val allSongs = ids.chunked(100).flatMapIndexed { bi, batch ->
                    try {
                        val detailReq = """{"ids":"[${batch.joinToString(",")}]"}"""
                        val detailResp = eapiPost("/api/song/detail", detailReq)
                        val detailObj = json.parseToJsonElement(detailResp).jsonObject
                        val songCount = detailObj["songs"]?.jsonArray?.size ?: 0
                        android.util.Log.e(TAG, "EAPI song batch $bi: $songCount songs")
                        (detailObj["songs"]?.jsonArray ?: emptyList()).map { parseWYSong(it.jsonObject) }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "EAPI song batch $bi failed: ${e.message}", e)
                        emptyList()
                    }
                }
                android.util.Log.e(TAG, "EAPI total songs: ${allSongs.size}")
                if (allSongs.isNotEmpty()) return@runCatching SearchResult(list = allSongs, total = allSongs.size, page = page, limit = limit)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "EAPI path failed: ${e.message}", e)
        }

        // Path 2: HTML scrape
        try {
            android.util.Log.e(TAG, "trying HTML scrape...")
            val html = httpGet("https://music.163.com/discover/toplist?id=$topId")
            android.util.Log.e(TAG, "HTML len=${html.length}")
            val match = Regex("""<textarea[^>]*id="song-list-pre-data"[^>]*>(.+?)</textarea>""",
                RegexOption.DOT_MATCHES_ALL).find(html)
            if (match != null) {
                val raw = match.groupValues[1].trim()
                android.util.Log.e(TAG, "HTML textarea len=${raw.length}")
                val list = json.parseToJsonElement(raw).jsonArray
                android.util.Log.e(TAG, "HTML parsed ${list.size} songs")
                val htmlSongs = list.map { el -> parseWYSong(el.jsonObject) }
                if (htmlSongs.isNotEmpty()) {
                    android.util.Log.e(TAG, "HTML returning ${htmlSongs.size} songs")
                    return@runCatching SearchResult(list = htmlSongs, total = htmlSongs.size, page = page, limit = limit)
                }
            } else {
                android.util.Log.e(TAG, "HTML textarea NOT FOUND")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "HTML path failed: ${e.message}", e)
        }

        // Path 3: Public API fallback
        try {
            android.util.Log.e(TAG, "trying public API...")
            val resp = httpGet("https://music.163.com/api/v3/playlist/detail?id=$topId")
            val obj = json.parseToJsonElement(resp).jsonObject
            android.util.Log.e(TAG, "public API code=${obj["code"]?.jsonPrimitive?.content}")
            val tracks = obj["playlist"]?.jsonObject?.get("tracks")?.jsonArray
            android.util.Log.e(TAG, "public API tracks count=${tracks?.size ?: 0}")
            if (tracks != null && tracks.isNotEmpty()) {
                val songs = tracks.map { parseWYSong(it.jsonObject) }
                return@runCatching SearchResult(list = songs, total = songs.size, page = page, limit = limit)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "public API failed: ${e.message}", e)
        }

        android.util.Log.e(TAG, "ALL paths failed for board $topId")
        SearchResult()
    }
    override suspend fun getMusicUrl(musicInfo: MusicInfo, quality: Quality): Result<String> =
        Result.failure(UnsupportedOperationException("Custom source required"))
    override suspend fun getLyric(musicInfo: MusicInfo): Result<LyricInfo> = runCatching {
        val songId = musicInfo.meta.songId
        // yv=1, ytv=1, yrv=1 to request YRC (word-timing) lyric format
        val req = """{"id":$songId,"cp":false,"tv":0,"lv":0,"rv":0,"kv":0,"yv":1,"ytv":1,"yrv":1}"""
        val resp = eapiPost("/api/song/lyric/v1", req)
        val obj = json.parseToJsonElement(resp).jsonObject
        // Parse YRC (word-timing) lyrics if available, fall back to LRC
        val yrcLyric = parseYrcLyric(obj["yrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content)
        val yrcTlyric = parseYrcLyric(obj["ytlrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content)
        val lrc = if (yrcLyric != null) yrcLyric.first else obj["lrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content ?: ""
        val tlyric = if (yrcTlyric != null) yrcTlyric.first else obj["tlyric"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content ?: ""
        val rlyric = obj["romalrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content ?: ""
        val lxlyric = yrcLyric?.second
        LyricInfo(lyric = lrc, tlyric = tlyric.ifBlank { null }, rlyric = rlyric.ifBlank { null }, lxlyric = lxlyric)
    }

    /**
     * Parse YRC format lyric to (clean lyric, lxlyric).
     * YRC format: [startMs,durationMs](0,1)word1(startMs,durationMs,1)word2...
     */
    private fun parseYrcLyric(yrcText: String?): Pair<String, String>? {
        if (yrcText.isNullOrBlank()) return null
        val lrcLines = mutableListOf<String>()
        val lxlrcLines = mutableListOf<String>()
        val lineTimeRegex = Regex("""^\[(\d+),\d+]""")
        val wordTimeRegex = Regex("""\((\d+),\d+,\d+\)""")
        val wordTimeAll = Regex("""(\(\d+,\d+,\d+\))""")
        val msRegex3 = Regex("""\[\d+:\d+\.\d{3}]""")

        for (line in yrcText.split("\n")) {
            val trimmed = line.trim()
            val timeMatch = lineTimeRegex.find(trimmed) ?: run {
                if (trimmed.startsWith("[offset")) {
                    lxlrcLines.add(trimmed)
                    lrcLines.add(trimmed)
                }
                continue
            }
            val startMs = timeMatch.groupValues[1].toLong()
            val timeStr = formatYrcTime(startMs)
            if (timeStr.isEmpty()) continue

            var words = trimmed.replace(lineTimeRegex, "")
            // Clean lyric: remove word timing
            lrcLines.add("$timeStr${words.replace(wordTimeAll, "")}")

            val times = wordTimeAll.findAll(words).toList()
            if (times.isEmpty()) continue
            val convertedTimes = times.map { t ->
                val m = Regex("""\((\d+),(\d+),\d+\)""").find(t.value)!!
                val wordStart = m.groupValues[1].toLong()
                val wordDur = m.groupValues[2].toLong()
                "<${maxOf(0, wordStart - startMs)},$wordDur>"
            }
            val wordArr = words.split(Regex("""\(\d+,\d+,\d+\)""")).toMutableList()
            if (wordArr.isNotEmpty()) wordArr.removeFirst() // first element is empty
            val newWords = convertedTimes.zip(wordArr).joinToString("") { (time, w) -> "$time$w" }
            lxlrcLines.add("$timeStr$newWords")
        }
        return Pair(lrcLines.joinToString("\n"), lxlrcLines.joinToString("\n"))
    }

    private fun formatYrcTime(timeMs: Long): String {
        val ms = (timeMs % 1000).toString().padStart(3, '0')
        val totalSec = timeMs / 1000
        val m = (totalSec / 60).toString().padStart(2, '0')
        val s = (totalSec % 60).toString().padStart(2, '0')
        return "[$m:$s.$ms]"
    }

    override suspend fun getPic(musicInfo: MusicInfo): Result<String> {
        val songId = musicInfo.meta.songId
        android.util.Log.d("WyPic", "getPic start, songId=$songId")
        return runCatching {
            // CRITICAL: c and ids must be JSON-encoded STRINGS (like RN's JSON.stringify),
            // NOT nested JSON objects. The weapi server expects string values.
            val resp = weapiPost("https://music.163.com/weapi/v3/song/detail", mapOf(
                "c" to kotlinx.serialization.json.JsonPrimitive("""[{"id":$songId}]"""),
                "ids" to kotlinx.serialization.json.JsonPrimitive("""[$songId]""")
            ))
            android.util.Log.d("WyPic", "weapi resp len=${resp.length}, first 200: ${resp.take(200)}")
            val obj = json.parseToJsonElement(resp).jsonObject
            if (obj["code"]?.jsonPrimitive?.content?.toIntOrNull() != 200) {
                android.util.Log.w("WyPic", "weapi returned code=${obj["code"]} for songId=$songId")
                return@runCatching ""
            }
            val songs = obj["songs"]?.jsonArray ?: return@runCatching ""
            val pic = songs.firstOrNull()?.jsonObject?.get("al")?.jsonObject?.get("picUrl")?.jsonPrimitive?.content ?: ""
            android.util.Log.d("WyPic", "got pic for $songId: ${pic.take(60)}")
            pic
        }.also { result ->
            if (result.isFailure) {
                android.util.Log.e("WyPic", "getPic failed for $songId: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
            }
        }
    }
    // Ported from src/utils/musicSdk/wy/songList.js getTag via public API
    // (avoids weapi encryption; uses music.163.com/api which doesn't need it)
    override suspend fun getSongListTags(): Result<List<SongListTag>> = runCatching {
        val body = httpPost("https://music.163.com/api/playlist/catalogue", "")
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["code"]?.jsonPrimitive?.intOrNull != 200) return@runCatching emptyList()
        // categories: { "0": "语种", "1": "风格", "2": "场景", ... }
        val cats = obj["categories"]?.jsonObject
        // sub: [{ name, category, hot, ... }, ...]
        val subItems = obj["sub"]?.jsonArray ?: return@runCatching emptyList()
        val grouped = mutableMapOf<String, MutableList<SongListTag>>()
        for (subEl in subItems) {
            val sub = subEl.jsonObject
            val catId = sub["category"]?.jsonPrimitive?.intOrNull?.toString() ?: continue
            val catName = cats?.get(catId)?.jsonPrimitive?.content ?: catId
            grouped.getOrPut(catName) { mutableListOf() }.add(
                SongListTag(name = sub["name"]?.jsonPrimitive?.content ?: "", id = sub["name"]?.jsonPrimitive?.content ?: ""))
        }
        grouped.map { (catName, children) -> SongListTag(name = catName, id = "", children = children) }
    }

    // Ported from src/utils/musicSdk/wy/songList.js getList via public API
    // sortId: hot / new; tagId: category name (e.g. "华语", "全部")
    override suspend fun getSongList(sortId: String, tagId: String, page: Int): Result<List<SongListItem>> = runCatching {
        val cat = tagId.ifEmpty { "全部" }
        val limit = 36
        val url = "https://music.163.com/api/playlist/list?cat=${URLEncoder.encode(cat, "UTF-8")}&order=$sortId&limit=$limit&offset=${limit * (page - 1)}"
        val body = httpGet(url)
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj["code"]?.jsonPrimitive?.intOrNull != 200) return@runCatching emptyList()
        (obj["playlists"]?.jsonArray ?: emptyList()).map { el ->
            val item = el.jsonObject
            SongListItem(
                id = item["id"]?.jsonPrimitive?.content ?: "",
                name = item["name"]?.jsonPrimitive?.content ?: "",
                pic = item["coverImgUrl"]?.jsonPrimitive?.contentOrNull,
                playCount = item["playCount"]?.jsonPrimitive?.longOrNull ?: 0L,
                author = item["creator"]?.jsonObject?.get("nickname")?.jsonPrimitive?.content ?: "",
                desc = item["description"]?.jsonPrimitive?.contentOrNull ?: "",
                source = "wy")
        }
    }
    // Song list detail via EAPI (returns ALL tracks with quality).
    override suspend fun getSongListDetail(listId: String, page: Int, limit: Int): Result<SongListDetailResult> = runCatching {
        val id = parseWYListId(listId)
        // 1. Get playlist info + trackIds via EAPI
        val plReq = """{"id":$id,"n":100000,"p":1}"""
        val plResp = eapiPost("/api/v3/playlist/detail", plReq)
        val plObj = json.parseToJsonElement(plResp).jsonObject
        if (plObj["code"]?.jsonPrimitive?.content?.toIntOrNull() != 200) return@runCatching SongListDetailResult()
        val pl = plObj["playlist"]?.jsonObject ?: return@runCatching SongListDetailResult()
        val trackIds = pl["trackIds"]?.jsonArray ?: return@runCatching SongListDetailResult()
        if (trackIds.isEmpty()) return@runCatching SongListDetailResult()

        // 2. Fetch song details via EAPI
        val ids = trackIds.map { it.jsonObject["id"]?.jsonPrimitive?.content ?: "" }.filter { it.isNotEmpty() }
        val allSongs = ids.chunked(100).flatMap { batch ->
            try {
                val detailReq = """{"ids":"[${batch.joinToString(",")}]"}"""
                val detailResp = eapiPost("/api/song/detail", detailReq)
                val detailObj = json.parseToJsonElement(detailResp).jsonObject
                (detailObj["songs"]?.jsonArray ?: emptyList()).mapNotNull { el ->
                    try { parseWYSong(el.jsonObject) } catch (_: Exception) { null }
                }
            } catch (_: Exception) { emptyList() }
        }
        // Safe helpers for pl fields
        fun JsonElement?.str() = this?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        fun JsonElement?.obj() = this?.takeIf { it !is JsonNull }?.jsonObject
        fun JsonObject.s(k: String) = get(k).str()
        fun JsonObject.o(k: String) = get(k).obj()

        SongListDetailResult(
            list = allSongs, total = allSongs.size, page = 1, limit = allSongs.size,
            info = SongListDetailInfo(
                name = pl.s("name") ?: "",
                pic = pl.s("coverImgUrl"),
                desc = pl.s("description") ?: "",
                author = pl.o("creator")?.s("nickname") ?: "",
                playCount = pl["playCount"]?.jsonPrimitive?.longOrNull ?: 0L))
    }

    private fun parseWYListId(input: String): String {
        val s = input.trim()
        if (s.all { it.isDigit() }) return s
        Regex("""[?&]id=(\d+)""").find(s)?.groupValues?.get(1)?.let { return it }
        Regex("""/playlist/(\d+)""").find(s)?.groupValues?.get(1)?.let { return it }
        Regex("""/playlist\?id=(\d+)""").find(s)?.groupValues?.get(1)?.let { return it }
        return s
    }

    /** Parse song from either playlist track (ar/al/dt) or song detail (artists/album/duration). */
    private fun parseWYSong(item: JsonObject): MusicInfo {
        // Safe accessors — handle JsonNull properly (JsonNull is not Kotlin null!)
        fun JsonElement?.str() = this?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        fun JsonElement?.arr() = this?.takeIf { it !is JsonNull }?.jsonArray
        fun JsonElement?.obj() = this?.takeIf { it !is JsonNull }?.jsonObject
        fun JsonElement?.int() = this?.str()?.toIntOrNull() ?: 0
        fun JsonObject.s(k: String) = get(k).str()
        fun JsonObject.a(k: String) = get(k).arr()
        fun JsonObject.o(k: String) = get(k).obj()
        fun JsonObject.i(k: String) = get(k).int()

        val id = item.s("id") ?: ""
        val durMs = item.i("dt").let { if (it > 0) it else item.i("duration") }
        val artists = item.a("ar") ?: item.a("artists")
        val singer = artists?.joinToString("/") { it.obj()?.s("name") ?: "" } ?: ""
        val al = item.o("al") ?: item.o("album")
        return MusicInfo("wy_$id", item.s("name") ?: "", singer, "wy",
            if (durMs > 0) "${durMs / 60000}:${((durMs % 60000) / 1000).toString().padStart(2, '0')}" else null,
            MusicInfoMetaOnline(songId = id,
                albumName = al?.s("name") ?: "",
                albumId = al?.s("id") ?: "",
                picUrl = al?.s("picUrl"),
                qualitys = parseWyQuality(item)))
    }

    private suspend fun fetchWYSongsBatch(ids: List<String>): List<MusicInfo> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(50).flatMap { batch ->
            val url = "https://music.163.com/api/song/detail?ids=[${batch.joinToString(",")}]"
            val obj = json.parseToJsonElement(httpGet(url)).jsonObject
            (obj["songs"]?.jsonArray ?: emptyList()).map { parseWYSong(it.jsonObject) }
        }
    }
    // EAPI cloudsearch for playlists (type=1000)
    override suspend fun searchSongList(keyword: String, page: Int): Result<List<SongListItem>> = runCatching {
        val limit = 20
        val reqJson = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("s", JsonPrimitive(keyword))
            put("type", JsonPrimitive(1000))
            put("limit", JsonPrimitive(limit))
            put("total", JsonPrimitive(page == 1))
            put("offset", JsonPrimitive(limit * (page - 1)))
        })
        val resp = eapiPost("/api/cloudsearch/pc", reqJson)
        val obj = json.parseToJsonElement(resp).jsonObject
        if (obj["code"]?.jsonPrimitive?.intOrNull != 200) throw Exception("搜索失败")
        (obj["result"]?.jsonObject?.get("playlists")?.jsonArray ?: emptyList()).map { el ->
            val item = el.jsonObject
            SongListItem(
                id = item["id"]?.jsonPrimitive?.content ?: "",
                name = item["name"]?.jsonPrimitive?.content ?: "",
                pic = item["coverImgUrl"]?.jsonPrimitive?.contentOrNull,
                playCount = item["playCount"]?.jsonPrimitive?.longOrNull ?: 0L,
                author = item["creator"]?.jsonObject?.get("nickname")?.jsonPrimitive?.content ?: "",
                desc = item["description"]?.jsonPrimitive?.contentOrNull ?: "",
                source = "wy")
        }
    }
    override suspend fun getHotComments(musicInfo: MusicInfo, page: Int, limit: Int) = Result.success(CommentResult())
    override suspend fun getNewComments(musicInfo: MusicInfo, page: Int, limit: Int) = Result.success(CommentResult())

    /** Parse quality from privilege (search/leaderboard HTML) or *Music.bitrate (EAPI song detail). */
    private fun parseWyQuality(item: JsonObject): List<MusicQualityType> {
        // Re-use safe accessors from parseWYSong scope
        fun JsonElement?.str() = this?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        fun JsonElement?.obj() = this?.takeIf { it !is JsonNull }?.jsonObject
        fun JsonElement?.int() = this?.str()?.toIntOrNull() ?: 0
        fun JsonObject.s(k: String) = get(k).str()
        fun JsonObject.o(k: String) = get(k).obj()
        fun JsonObject.i(k: String) = get(k).int()

        val types = mutableListOf<MusicQualityType>()
        val priv = item.o("privilege") ?: item.o("pc")
        if (priv != null) {
            if (priv.s("maxBrLevel") == "hires") types.add(MusicQualityType("flac24bit", ""))
            when (priv.i("maxbr")) {
                999000 -> { types.add(MusicQualityType("flac", "")); types.add(MusicQualityType("320k", "")); types.add(MusicQualityType("128k", "")) }
                320000 -> { types.add(MusicQualityType("320k", "")); types.add(MusicQualityType("128k", "")) }
                192000, 128000 -> types.add(MusicQualityType("128k", ""))
            }
            if (types.isEmpty()) {
                val fl = priv.i("fl")
                val t = when { fl > 1920000 -> "flac24bit"; fl > 999000 -> "flac"; fl > 192000 -> "320k"; fl > 0 -> "128k"; else -> null }
                if (t != null) types.add(MusicQualityType(t, ""))
            }
        } else {
            item.o("hrMusic")?.let { if (it.i("bitrate") > 0) types.add(MusicQualityType("flac24bit", "")) }
            item.o("sqMusic")?.let { if (it.i("bitrate") > 0) types.add(MusicQualityType("flac", "")) }
            item.o("hMusic")?.let {
                when { it.i("bitrate") > 192000 -> types.add(MusicQualityType("320k", ""))
                       it.i("bitrate") > 0 -> types.add(MusicQualityType("128k", "")) }
            }
        }
        return types
    }
}
