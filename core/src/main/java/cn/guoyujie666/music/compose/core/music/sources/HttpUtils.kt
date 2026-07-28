package cn.guoyujie666.music.compose.core.music.sources

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Shared HTTP and crypto utilities for music source SDKs.
 * Ported from src/utils/musicSdk/utils.js and per-source util.js files.
 */

object HttpUtils {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Encode URL params for Chinese characters */
    fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")

    /** Format seconds to mm:ss */
    fun formatPlayTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    /** Format play count to Chinese units */
    fun formatPlayCount(count: Long): String = when {
        count >= 100_000_000L -> "${"%.1f".format(count / 100_000_000.0)}亿"
        count >= 10_000L -> "${"%.1f".format(count / 10_000.0)}万"
        else -> count.toString()
    }

    /** Replace & with Chinese 、 */
    fun formatSinger(singer: String): String = singer.replace("&", "、")

    /** MD5 hash (returns lowercase hex) */
    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** MD5 hash (returns uppercase hex) */
    fun md5Upper(input: String): String = md5(input).uppercase()

    /** Generate random hex string of given length */
    fun randomHex(length: Int): String {
        val chars = "0123456789abcdef"
        return (1..length).map { chars.random() }.joinToString("")
    }

    /** AES-128-ECB NoPadding encrypt (used by kw leaderboard) */
    fun aesEncryptEcb(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        // Pad with zeros to multiple of 16
        val padded = if (data.size % 16 == 0) data else data + ByteArray(16 - data.size % 16)
        return cipher.doFinal(padded)
    }

    /** AES-128-ECB NoPadding decrypt */
    fun aesDecryptEcb(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    /** AES-128-ECB encrypt with base64 key (kw leaderboard specific) */
    fun aesEncryptEcbBase64(data: String, keyBase64: String): String {
        val key = Base64.getDecoder().decode(keyBase64)
        val encrypted = aesEncryptEcb(data.toByteArray(Charsets.UTF_8), key)
        return Base64.getEncoder().encodeToString(encrypted)
    }

    /** XOR cipher with repeating key (kw lyric specific) */
    fun xorCipher(data: ByteArray, key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        return ByteArray(data.size) { i ->
            (data[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
    }

    /** XOR + base64 (kw lyric request params) */
    fun xorEncryptToBase64(input: String, key: String): String {
        val xored = xorCipher(input.toByteArray(Charsets.UTF_8), key)
        return Base64.getEncoder().encodeToString(xored)
    }

    /** HTML entity decode */
    fun htmlDecode(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")

    /** Parse quality from bitrate string in n_minfo format */
    fun parseQualityFromBitrate(bitrate: Int): String = when (bitrate) {
        4000 -> "flac24bit"
        2000 -> "flac"
        320 -> "320k"
        else -> "128k"
    }

    /** Convert Python dict string (single quotes) to valid JSON */
    fun objStrToJson(str: String): String {
        // Replace single quotes with double quotes
        var result = str
        // Key quotes: 'key' -> "key"
        result = result.replace(Regex("(?<=[{,]\\s*)'([^']+)'\\s*:")) { "\"${it.groupValues[1]}\":" }
        // Value quotes: 'value' -> "value"
        result = result.replace(Regex(":\\s*'([^']*)'")) { ": \"${it.groupValues[1]}\"" }
        return result
    }

    /** Ktor request helpers */

    /** Perform a GET request and return raw text */
    suspend fun httpGetText(
        client: HttpClient,
        url: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): String {
        return client.get(url) {
            params.forEach { (k, v) -> parameter(k, v) }
            headers.forEach { (k, v) -> header(k, v) }
        }.bodyAsText()
    }

    /** Perform a GET request and return raw bytes */
    suspend fun httpGetBytes(
        client: HttpClient,
        url: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): ByteArray {
        return client.get(url) {
            params.forEach { (k, v) -> parameter(k, v) }
            headers.forEach { (k, v) -> header(k, v) }
        }.bodyAsBytes()
    }

    /** Perform a POST request and return raw text */
    suspend fun httpPostText(
        client: HttpClient,
        url: String,
        body: String,
        contentType: String = "application/x-www-form-urlencoded"
    ): String {
        return client.post(url) {
            this.contentType(ContentType.parse(contentType))
            setBody(body)
        }.bodyAsText()
    }
}
