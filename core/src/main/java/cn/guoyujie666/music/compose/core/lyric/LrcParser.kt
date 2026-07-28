package cn.guoyujie666.music.compose.core.lyric

import android.util.Base64
import cn.guoyujie666.music.compose.core.model.LyricInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LRC lyric parser and playback engine.
 *
 * Lyric format support:
 * - Standard LRC: [mm:ss.xx] text
 * - Extended LRC: [mm:ss.xxx] text (higher precision)
 * - Multiple time tags per line
 * - Translation lyrics (tlrc) — via tLrc param or inline duplicates
 * - Romaji lyrics (rlrc)
 * - Saved LRC files with [awlrc:lrc:...,tlrc:...,...] tags (ported from RN parseLyric)
 */

/** A single word/syllable with timing for word-by-word (lxlrc) lyrics */
data class LyricWord(
    val text: String,           // The word/syllable text
    val startTime: Long,        // Start time in ms, relative to the line's timeMs
    val duration: Long          // Duration in ms
)

/** Singer role markers for duet songs */
enum class SingerRole { Male, Female, Chorus, Unknown }

/** A single parsed lyric line */
data class LyricLine(
    val timeMs: Long,           // Time in milliseconds
    val text: String,           // Main lyric text (word tags stripped)
    val translation: String? = null,   // Translation text (if available)
    val romaji: String? = null,        // Romaji text (if available)
    val words: List<LyricWord>? = null,  // Word-level timing (null = line mode, no per-word data)
    val singerRole: SingerRole = SingerRole.Unknown  // Role for duet/chorus songs
)

/** Parsed lyric data ready for display */
data class ParsedLyric(
    val lines: List<LyricLine> = emptyList(),
    val offset: Long = 0,       // Global offset in ms
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val by: String? = null
)

@Singleton
class LrcParser @Inject constructor() {

    companion object {
        // LRC time tag regex: [mm:ss.xx] or [mm:ss.xxx]
        private val TIME_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{2,3}))?\]""")

        // Meta tag regex: [ti:xxx], [ar:xxx], etc.
        private val META_TAG_REGEX = Regex("""\[(ti|ar|al|by|offset):(.+)\]""")

        // awlrc tag regex: [awlrc:lrc:base64,tlrc:base64,...] (ported from RN parseLyric)
        private val AWLRC_TAG_REGEX = Regex("""(?:^|\n\s*)\[awlrc:([^\]]+)]""", RegexOption.IGNORE_CASE)

        // Individual part regex: lrc:base64, tlrc:base64, awlrc:base64, rlrc:base64
        private val AWLRC_PART_REGEX = Regex("""^(lrc|awlrc|tlrc|rlrc):(.+)$""", RegexOption.IGNORE_CASE)

        // Word-level timing tag: <startMs,durationMs> (lxlrc / word-by-word format)
        private val WORD_TAG_REGEX = Regex("""<(\d+),(\d+)>([^<]*)""")

        // Verify standard LRC format
        private fun verifyLrc(content: String): Boolean {
            return Regex("""(?m)(?:^|\s*)\[\d+:\d+(?:\.\d+)].+$""").containsMatchIn(content)
        }

        // Verify word-timed LRC (awlrc/lxlyric) format: [mm:ss.xx]<start,duration>word...
        private fun verifyAwlrc(content: String): Boolean {
            return Regex("""(?m)(?:^|\s*)\[\d+:\d+(?:\.\d+)]<\d+,\d+>.+$""").containsMatchIn(content)
        }
    }

    /**
     * Parse a saved LRC file that may contain an [awlrc:...] tag.
     *
     * Ported from RN src/core/music/local.ts parseLyric().
     * The awlrc tag stores base64-encoded lyric components:
     *   lrc:  — main lyric with word-by-word timing
     *   tlrc: — translation lyric
     *   rlrc: — romaji lyric
     *   awlrc: — additional word-timing data
     *
     * Returns a LyricInfo with the decoded components separated.
     * If no awlrc tag is found, the original content is returned as `lyric`.
     */
    fun parseFileContent(content: String): LyricInfo {
        var lyric = content
        var tlyric: String? = null
        var rlyric: String? = null
        var lxlyric: String? = null

        val awlrcMatch = AWLRC_TAG_REGEX.find(content)
        if (awlrcMatch != null) {
            val tagContent = awlrcMatch.groupValues[1]

            // Remove the awlrc tag from the main lyric text
            lyric = content.replace(AWLRC_TAG_REGEX, "").trim()

            // Parse each comma-separated part: lrc:base64, tlrc:base64, etc.
            val parts = tagContent.split(",")
            for (part in parts) {
                val trimmed = part.trim()
                val partMatch = AWLRC_PART_REGEX.find(trimmed) ?: continue
                val key = partMatch.groupValues[1].lowercase()
                val base64Data = partMatch.groupValues[2]

                try {
                    val decoded = Base64.decode(base64Data, Base64.DEFAULT)
                    val decodedStr = String(decoded, Charsets.UTF_8).trim()

                    when (key) {
                        "lrc" -> {
                            // The lrc: field contains the main lyric (may have word timing)
                            if (verifyLrc(decodedStr) || verifyAwlrc(decodedStr)) {
                                // Strip word-time tags for display, keep original for lxlyric
                                lyric = decodedStr
                            }
                        }
                        "tlrc" -> {
                            if (verifyLrc(decodedStr)) tlyric = decodedStr
                        }
                        "rlrc" -> {
                            if (verifyLrc(decodedStr)) rlyric = decodedStr
                        }
                        "awlrc" -> {
                            if (verifyAwlrc(decodedStr)) lxlyric = decodedStr
                        }
                    }
                } catch (_: Exception) {
                    // Skip malformed base64 parts
                }
            }
        }

        return LyricInfo(lyric = lyric, tlyric = tlyric, rlyric = rlyric, lxlyric = lxlyric)
    }

    /**
     * Parse raw LRC text into structured lyric lines.
     *
     * When the main lrc contains inline duplicate timestamps (e.g. KW format
     * where the original language and translation share the same timestamps),
     * the second occurrence is captured as an inline translation — but only
     * for timestamps that aren't already covered by the explicit tLrc param.
     *
     * @param lrc Raw LRC content
     * @param tLrc Translation lyrics (optional, takes precedence over inline duplicates)
     * @param rLrc Romaji lyrics (optional)
     */
    fun parse(lrc: String, tLrc: String? = null, rLrc: String? = null, lxLrc: String? = null): ParsedLyric {
        val meta = mutableMapOf<String, String>()
        val timeTextPairs = mutableListOf<Pair<Long, String>>()
        val seenTimes = mutableSetOf<Long>()
        // Capture duplicate timestamps as inline translations
        // (e.g. KW LRC where original + translation share time tags)
        val inlineTranslations = mutableMapOf<Long, String>()

        // Parse word-level timing from lxLrc separately (may be incomplete)
        val wordDataByTime = if (lxLrc != null) parseWordDataFromLxLrc(lxLrc) else emptyMap()

        // Parse main lyrics
        for (line in lrc.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Check for meta tags (including awlrc — skip it if not already extracted)
            val metaMatch = META_TAG_REGEX.find(trimmed)
            if (metaMatch != null) {
                meta[metaMatch.groupValues[1]] = metaMatch.groupValues[2].trim()
                continue
            }
            // Also skip awlrc tag lines that weren't caught above
            if (trimmed.startsWith("[awlrc:", ignoreCase = true)) continue

            // Find all time tags on this line
            val timeMatches = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (timeMatches.isEmpty()) continue

            // Extract text (everything after the last time tag)
            val lastTagEnd = timeMatches.last().range.last + 1
            val text = trimmed.substring(lastTagEnd).trim()

            // Create a time-text pair for each time tag
            for (match in timeMatches) {
                val min = match.groupValues[1].toInt()
                val sec = match.groupValues[2].toInt()
                val ms = match.groupValues[3].takeIf { it.isNotEmpty() }?.let {
                    // Normalize to milliseconds:
                    // 2-digit = centiseconds (*10), 3-digit = milliseconds (as-is)
                    val value = it.toInt()
                    if (it.length == 2) value * 10 else value
                } ?: 0

                val timeMs = (min * 60_000L) + (sec * 1_000L) + ms

                if (seenTimes.add(timeMs)) {
                    // First occurrence → main lyric text
                    timeTextPairs.add(Pair(timeMs, text))
                } else {
                    // Duplicate timestamp → inline translation
                    // Only store if text differs (skip exact duplicates)
                    val existing = inlineTranslations[timeMs]
                    if (existing == null || existing != text) {
                        inlineTranslations[timeMs] = text
                    }
                }
            }
        }

        // Sort by time
        timeTextPairs.sortBy { it.first }

        // Parse explicit translations (tLrc takes precedence)
        val translations = parseTimeTaggedLines(tLrc).toMutableMap()

        // Fill gaps with inline translations for timestamps not covered by explicit tLrc
        for ((time, text) in inlineTranslations) {
            if (time !in translations) {
                translations[time] = text
            }
        }

        // Parse romaji
        val romaji = parseTimeTaggedLines(rLrc)

        // Detect singer roles for duet lyrics (only on lines with actual lyric content, not meta tags)
        val singerRoles = detectSingerRoles(timeTextPairs.map { it.second }.filter { text ->
            // Skip KRC/LRC metadata lines: [ar:xxx], [ti:xxx], [id:xxx], [language:xxx], etc.
            !text.startsWith("[") && !text.startsWith("\\[") && !text.startsWith("\\ufeff") &&
            !TIME_TAG_REGEX.containsMatchIn(text.replaceFirst("^\\[\\d+,\\d+\\]", ""))
        })

        // Build lyric lines
        // Fuzzy time lookup for translations/romaji (KW uses centiseconds causing precision loss)
        fun findClosest(map: Map<Long, String>, targetMs: Long): String? {
            return map.asSequence()
                .filter { (k, _) -> kotlin.math.abs(k - targetMs) <= 50 }
                .minByOrNull { (k, _) -> kotlin.math.abs(k - targetMs) }?.value
        }

        val offset = meta["offset"]?.toLongOrNull() ?: 0L
        val lines = timeTextPairs.mapIndexed { idx, (time, text) ->
            val (plainText, inlineWords) = parseWords(text)
            // Prefer word data from lxLrc (decoded word timing) over inline tags
            val words = wordDataByTime[time] ?: inlineWords
            LyricLine(
                timeMs = time + offset,
                text = plainText,
                translation = findClosest(translations, time),
                romaji = findClosest(romaji, time),
                words = words,
                singerRole = singerRoles[idx]
            )
        }

        return ParsedLyric(
            lines = lines,
            offset = offset,
            title = meta["ti"],
            artist = meta["ar"],
            album = meta["al"],
            by = meta["by"]
        )
    }

    /**
     * Detect singer roles for duet lyrics.
     * Scans lines for singer markers (like "男：", "女：", "合：", "飞：", "H：")
     * and assigns roles to subsequent lines until the next marker.
     */
    private fun detectSingerRoles(texts: List<String>): List<SingerRole> {
        val roles = mutableListOf<SingerRole>()
        var currentRole = SingerRole.Unknown
        var altToggle = false
        // Match: line starts with short prefix + colon, optionally followed by lyrics text
        // "Jay：", "飞：你住的巷子里", "合：", "合：躺在你学校的操场看星空"
        val prefixRegex = Regex("""^([^：:]{1,20})[：:]\s*(.*)""")
        fun isChorusPrefix(p: String): Boolean =
            p == "合" || p == "合唱" || p == "all" || p == "ALL"
        // Metadata prefixes that are NOT singer roles
        // Skip production credit prefixes and lines with very long prefixes
        val skipPrefixes = setOf(
            // Chinese production credits
            "词", "曲", "编曲", "制作", "原唱", "唱", "谱曲", "填词",
            "作词", "作曲", "监制", "和声", "混音", "母带", "录音", "吉他", "贝斯",
            "键盘", "鼓", "弦乐", "钢琴", "小提琴", "大提琴", "后期", "发行", "出品",
            "推广", "宣传", "企划", "统筹", "文案", "设计", "摄影", "MV", "导演",
            "协力", "经纪", "管理", "授权", "版権", "權利", "封面", "插图",
            // Common abbreviation credits
            "SP", "OP", "ISRC", "UPC", "DJ", "Feat", "feat", "Prod", "prod",
            "写", "说", "看", "听", "读", "看呀",
            // English production credits
            "Lyrics", "Lyrics by", "Composed", "Composed by", "Produced",
            "Produced by", "Mixed", "Mixed by", "Mastered", "Mastered by",
            "Vocals", "Vocals by", "Backing", "Backing Vocals by",
            "Vocal", "Vocal Production", "Vocal Production by",
            "Co", "Co-production", "Additional", "Additional Production",
            "Executive", "Executive Produced", "Executive Produced by",
            "Performed", "Performed by", "Arranged", "Arranged by",
            "Programmed", "Programmed by", "Recorded", "Recorded by",
            "Engineered", "Engineered by", "Directed", "Directed by")

        fun isSkipPrefix(p: String): Boolean {
            if (p in skipPrefixes) return true
            // KRC/LRC metadata tags that look like prefix:value but are not singer names
            if (p.startsWith("[") || p.startsWith("\\[")) return true
            if (p == "\\") return true
            // Any long prefix (>10 chars) is likely a production credit, not a singer name
            if (p.length > 10) return true
            // Lines with "/" are typically multi-artist credits
            if (p.contains("/")) return true
            return false
        }

        for (text in texts) {
            val t = text.trim()
            val match = prefixRegex.find(t)
            if (match != null) {
                val prefix = match.groupValues[1].trim()
                val rest = match.groupValues[2]
                if (isSkipPrefix(prefix)) {
                    roles.add(SingerRole.Unknown)
                    continue
                }
                currentRole = when {
                    isChorusPrefix(prefix) -> SingerRole.Chorus
                    prefix.contains("男") -> { altToggle = false; SingerRole.Male }
                    prefix.contains("女") -> { altToggle = true; SingerRole.Female }
                    else -> {
                        val role = if (altToggle) SingerRole.Female else SingerRole.Male
                        altToggle = !altToggle
                        role
                    }
                }
                roles.add(currentRole)
            } else {
                roles.add(currentRole)
            }
        }
        // Only keep roles if we detected actual duet (multiple non-Unknown roles at distinct lines)
        val distinctRoles = roles.filter { it != SingerRole.Unknown }.toSet()
        return if (distinctRoles.size >= 2) roles
               else roles.map { SingerRole.Unknown }
    }

    /**
     * Parse word-level timing tags from a lyric line's text.
     *
     * lxlrc format: <startMs,durationMs>word1<startMs,durationMs>word2...
     * Returns (plainText without tags, list of words with timing, or null if no tags found).
     */
    private fun parseWords(rawText: String): Pair<String, List<LyricWord>?> {
        val matches = WORD_TAG_REGEX.findAll(rawText).toList()
        if (matches.isEmpty()) return Pair(rawText, null)

        val words = mutableListOf<LyricWord>()
        val plainText = StringBuilder()

        for (match in matches) {
            val startTime = match.groupValues[1].toLong()
            val duration = match.groupValues[2].toLong()
            val wordText = match.groupValues[3]
            words.add(LyricWord(text = wordText, startTime = startTime, duration = duration))
            plainText.append(wordText)
        }

        return Pair(plainText.toString(), words.takeIf { it.isNotEmpty() })
    }

    /**
     * Extract word-level timing data from an lxLrc string.
     * Returns a map of line timeMs → list of LyricWord.
     * Lines in lxLrc without word tags are simply skipped.
     */
    private fun parseWordDataFromLxLrc(lxLrc: String): Map<Long, List<LyricWord>> {
        val result = mutableMapOf<Long, List<LyricWord>>()
        var sampleLogged = false
        for (line in lxLrc.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val timeMatches = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (timeMatches.isEmpty()) continue

            val lastTagEnd = timeMatches.last().range.last + 1
            val text = trimmed.substring(lastTagEnd).trim()

            val (_, words) = parseWords(text)
            if (!sampleLogged) {
                android.util.Log.d("LxLrc", "lxlyric line: text='$text' hasWords=${words != null} wordCount=${words?.size ?: 0}")
                sampleLogged = true
            }
            if (words != null) {
                for (match in timeMatches) {
                    val min = match.groupValues[1].toInt()
                    val sec = match.groupValues[2].toInt()
                    val ms = match.groupValues[3].takeIf { it.isNotEmpty() }?.let {
                        val value = it.toInt()
                        if (it.length == 2) value * 10 else value
                    } ?: 0
                    val timeMs = (min * 60_000L) + (sec * 1_000L) + ms
                    result[timeMs] = words
                }
            }
        }
        return result
    }

    /**
     * Parse time-tagged translation/romaji lyrics.
     * Returns a map of timeMs -> text.
     */
    private fun parseTimeTaggedLines(content: String?): Map<Long, String> {
        if (content.isNullOrEmpty()) return emptyMap()

        val result = mutableMapOf<Long, String>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val timeMatches = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (timeMatches.isEmpty()) continue

            val lastTagEnd = timeMatches.last().range.last + 1
            val text = trimmed.substring(lastTagEnd).trim()

            for (match in timeMatches) {
                val min = match.groupValues[1].toInt()
                val sec = match.groupValues[2].toInt()
                val ms = match.groupValues[3].takeIf { it.isNotEmpty() }?.let {
                    val value = it.toInt()
                    if (it.length == 2) value * 10 else value
                } ?: 0

                val timeMs = (min * 60_000L) + (sec * 1_000L) + ms
                result[timeMs] = text
            }
        }
        return result
    }

    /**
     * Find the active lyric line index for a given playback position.
     */
    fun findActiveLine(parsedLyric: ParsedLyric, currentTimeMs: Long): Int {
        val lines = parsedLyric.lines
        if (lines.isEmpty()) return -1

        // Find the last line whose time is <= currentTime
        var activeIndex = 0
        for (i in lines.indices) {
            if (lines[i].timeMs <= currentTimeMs) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }

    /**
     * Parse a LyricInfo from the music source into ParsedLyric.
     *
     * First runs parseFileContent to extract any [awlrc:...] tag components,
     * then parses the result into display-ready lyric lines.
     */
    fun fromLyricInfo(info: LyricInfo): ParsedLyric {
        // Parse the content to extract awlrc components if present
        val parsed = parseFileContent(info.lyric)

        // Use explicit tlyric/rlyric from LyricInfo if the awlrc tag didn't provide them
        return parse(
            lrc = parsed.lyric,
            tLrc = parsed.tlyric ?: info.tlyric,
            rLrc = parsed.rlyric ?: info.rlyric,
            lxLrc = parsed.lxlyric ?: info.lxlyric
        )
    }
}
