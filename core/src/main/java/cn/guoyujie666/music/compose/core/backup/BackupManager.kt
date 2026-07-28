package cn.guoyujie666.music.compose.core.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import cn.guoyujie666.music.compose.core.model.AppSetting
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.model.MusicInfoMetaLocal
import cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline
import cn.guoyujie666.music.compose.core.model.MusicQualityType
import cn.guoyujie666.music.compose.core.songlist.SongListManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of importing lists from a backup file.
 * The caller handles merging default list and user lists appropriately.
 */
data class BackupImportResult(
    val totalLists: Int,
    val defaultListSongs: List<MusicInfo> = emptyList(),
    val loveListSongs: List<MusicInfo> = emptyList(),
    val importedUserLists: List<Pair<String, List<MusicInfo>>> = emptyList() // (listId, songs)
)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songListManager: SongListManager
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    // ── Export ────────────────────────────────────────────────────

    /**
     * Export all lists to a gzip-compressed .lxmc file.
     * @param uri Destination URI (from SAF CreateDocument)
     * @param defaultList Songs in the 试听列表 (managed by PlayerController)
     */
    fun exportAllLists(uri: Uri, defaultList: List<MusicInfo>): Boolean {
        return try {
            val entries = mutableListOf<BackupListEntry>()

            // Default list (试听列表)
            entries.add(BackupListEntry(
                id = "default", name = "试听列表",
                list = defaultList.map { it.toBackupMusicInfo() }
            ))

            // Love list (我的收藏)
            val loveSongs = songListManager.getSongs("love")
            entries.add(BackupListEntry(
                id = "love", name = "我的收藏",
                list = loveSongs.map { it.toBackupMusicInfo() }
            ))

            // User lists
            for (list in songListManager.lists.value) {
                val songs = songListManager.getSongs(list.id)
                entries.add(BackupListEntry(
                    id = list.id, name = list.name,
                    list = songs.map { it.toBackupMusicInfo() },
                    source = null, sourceListId = null,
                    locationUpdateTime = null
                ))
            }

            val file = BackupFile(type = "playList_v2", data = entries)
            val jsonStr = json.encodeToString(BackupFile.serializer(), file)
            val gzipped = gzip(jsonStr.toByteArray(Charsets.UTF_8))

            context.contentResolver.openOutputStream(uri)?.use { it.write(gzipped) }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Export failed", e)
            false
        }
    }

    /**
     * Export a single list (playListPart_v2 format — compatible with RN).
     */
    fun exportSingleList(uri: Uri, listId: String, listName: String, songs: List<MusicInfo>): Boolean {
        return try {
            val root = buildJsonObject {
                put("type", "playListPart_v2")
                putJsonObject("data") {
                    put("id", listId)
                    put("name", listName)
                    putJsonArray("list") {
                        for (s in songs.map { it.toBackupMusicInfo() }) {
                            add(json.encodeToJsonElement(BackupMusicInfo.serializer(), s))
                        }
                    }
                }
            }
            val jsonStr = json.encodeToString(JsonElement.serializer(), root)
            val gzipped = gzip(jsonStr.toByteArray(Charsets.UTF_8))
            context.contentResolver.openOutputStream(uri)?.use { it.write(gzipped) }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Export list failed", e)
            false
        }
    }

    /**
     * Export settings to a gzip-compressed .lxmc file.
     */
    fun exportSettings(uri: Uri, settings: AppSetting): Boolean {
        return try {
            val root = buildJsonObject {
                put("type", "setting_v2")
                putJsonObject("data") {
                    for ((key, value) in json.encodeToJsonElement(AppSetting.serializer(), settings).jsonObject) {
                        put(key, value)
                    }
                }
            }
            val jsonStr = json.encodeToString(JsonElement.serializer(), root)
            val gzipped = gzip(jsonStr.toByteArray(Charsets.UTF_8))
            context.contentResolver.openOutputStream(uri)?.use { it.write(gzipped) }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Export settings failed", e)
            false
        }
    }

    /**
     * Export all data (settings + lists) to a single file.
     */
    fun exportAllData(uri: Uri, settings: AppSetting, defaultList: List<MusicInfo>): Boolean {
        return try {
            // Build list entries
            val entries = mutableListOf<BackupListEntry>()
            entries.add(BackupListEntry("default", "试听列表", defaultList.map { it.toBackupMusicInfo() }))
            entries.add(BackupListEntry("love", "我的收藏", songListManager.getSongs("love").map { it.toBackupMusicInfo() }))
            for (list in songListManager.lists.value) {
                entries.add(BackupListEntry(list.id, list.name,
                    songListManager.getSongs(list.id).map { it.toBackupMusicInfo() },
                    null, null, null))
            }

            val root = buildJsonObject {
                put("type", "allData_v2")
                putJsonObject("data") {
                    for ((key, value) in json.encodeToJsonElement(AppSetting.serializer(), settings).jsonObject) {
                        put(key, value)
                    }
                }
                put("playList", json.encodeToJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(BackupListEntry.serializer()), entries))
            }
            val jsonStr = json.encodeToString(JsonElement.serializer(), root)
            val gzipped = gzip(jsonStr.toByteArray(Charsets.UTF_8))
            context.contentResolver.openOutputStream(uri)?.use { it.write(gzipped) }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Export all data failed", e)
            false
        }
    }

    // ── Import ────────────────────────────────────────────────────

    /**
     * Unified import: reads the file once and extracts both settings and lists.
     * Handles all known types (playList_v2, setting_v2, allData_v2, and legacy).
     */
    fun importAll(uri: Uri): Pair<AppSetting?, BackupImportResult?> {
        return try {
            val root = readAndParseRoot(uri) ?: return Pair(null, null)
            val type = root["type"]?.jsonPrimitive?.content ?: return Pair(null, null)

            var settings: AppSetting? = null
            var lists: BackupImportResult? = null

            when (type) {
                "setting_v2" -> {
                    val dataObj = root["data"]?.jsonObject
                    if (dataObj != null) settings = json.decodeFromJsonElement(AppSetting.serializer(), dataObj)
                }
                "playList_v2" -> lists = importPlayListV2(root)
                "allData_v2" -> {
                    val dataObj = root["data"]?.jsonObject
                    if (dataObj != null) settings = json.decodeFromJsonElement(AppSetting.serializer(), dataObj)
                    lists = importAllDataV2Lists(root)
                }
                "playListPart_v2" -> lists = importPlayListPartV2(root)
                "playList", "allData" -> lists = importPlayListV1(root)
                "playListPart" -> lists = importPlayListPartV1(root)
                else -> Log.w("BackupManager", "Unknown backup type: $type")
            }

            Pair(settings, lists)
        } catch (e: Exception) {
            Log.e("BackupManager", "Import failed", e)
            Pair(null, null)
        }
    }

    /** Import only lists (legacy method — prefer importAll to avoid double-read). */
    fun importLists(uri: Uri): BackupImportResult? = importAll(uri).second

    /** allData_v2 has playList under "playList" key, not "data". */
    private fun importAllDataV2Lists(root: JsonObject): BackupImportResult {
        val dataArray = root["playList"]?.jsonArray ?: return BackupImportResult(0)
        val entries = json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(BackupListEntry.serializer()),
            dataArray
        )
        var defaultSongs = emptyList<MusicInfo>()
        var loveSongs = emptyList<MusicInfo>()
        val userLists = mutableListOf<Pair<String, List<MusicInfo>>>()
        var count = 0
        for (entry in entries) {
            val songs = entry.list.mapNotNull { it.toMusicInfo() }
            if (songs.isEmpty()) continue
            when (entry.id) {
                "default" -> { defaultSongs = songs; count++ }
                "love" -> { mergeOrSetSongs("love", songs); loveSongs = songs; count++ }
                else -> { mergeOrSetUserSongs(entry.id, entry.name, songs); userLists.add(entry.id to songs); count++ }
            }
        }
        return BackupImportResult(count, defaultSongs, loveSongs, userLists)
    }

    private fun importPlayListV2(root: JsonObject): BackupImportResult {
        val dataKey = if (root.containsKey("playList")) "playList" else "data"
        val dataArray = root[dataKey]?.jsonArray ?: return BackupImportResult(0)

        val entries = json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(BackupListEntry.serializer()),
            dataArray
        )

        var defaultSongs = emptyList<MusicInfo>()
        var loveSongs = emptyList<MusicInfo>()
        val userLists = mutableListOf<Pair<String, List<MusicInfo>>>()
        var count = 0

        for (entry in entries) {
            val songs = entry.list.mapNotNull { it.toMusicInfo() }
            if (songs.isEmpty()) continue

            when (entry.id) {
                "default" -> { defaultSongs = songs; count++ }
                "love" -> {
                    mergeOrSetSongs("love", songs)
                    loveSongs = songs; count++
                }
                else -> {
                    mergeOrSetUserSongs(entry.id, entry.name, songs)
                    userLists.add(entry.id to songs)
                    count++
                }
            }
        }
        return BackupImportResult(count, defaultSongs, loveSongs, userLists)
    }

    private fun importPlayListPartV2(root: JsonObject): BackupImportResult {
        val dataEl = root["data"] ?: return BackupImportResult(0)
        val entry = json.decodeFromJsonElement(BackupListEntry.serializer(), dataEl)
        val songs = entry.list.mapNotNull { it.toMusicInfo() }
        if (songs.isEmpty()) return BackupImportResult(0)

        when (entry.id) {
            "default" -> return BackupImportResult(1, defaultListSongs = songs)
            "love" -> {
                mergeOrSetSongs("love", songs)
                return BackupImportResult(1, loveListSongs = songs)
            }
            else -> {
                mergeOrSetUserSongs(entry.id, entry.name, songs)
                return BackupImportResult(1, importedUserLists = listOf(entry.id to songs))
            }
        }
    }

    private fun importPlayListV1(root: JsonObject): BackupImportResult {
        val dataArray = root["data"]?.jsonArray ?: return BackupImportResult(0)
        var defaultSongs = emptyList<MusicInfo>()
        var loveSongs = emptyList<MusicInfo>()
        val userLists = mutableListOf<Pair<String, List<MusicInfo>>>()
        var count = 0

        for (el in dataArray) {
            val obj = el.jsonObject
            val songs = obj["list"]?.jsonArray?.mapNotNull { it.toMusicInfoV1() } ?: continue
            if (songs.isEmpty()) continue
            val id = obj["id"]?.jsonPrimitive?.content ?: continue
            val name = obj["name"]?.jsonPrimitive?.content ?: id

            when (id) {
                "default" -> { defaultSongs = songs; count++ }
                "love" -> { mergeOrSetSongs("love", songs); loveSongs = songs; count++ }
                else -> {
                    mergeOrSetUserSongs(id, name, songs)
                    userLists.add(id to songs)
                    count++
                }
            }
        }
        return BackupImportResult(count, defaultSongs, loveSongs, userLists)
    }

    private fun importPlayListPartV1(root: JsonObject): BackupImportResult {
        val obj = root["data"]?.jsonObject ?: return BackupImportResult(0)
        val songs = obj["list"]?.jsonArray?.mapNotNull { it.toMusicInfoV1() } ?: return BackupImportResult(0)
        if (songs.isEmpty()) return BackupImportResult(0)
        val id = obj["id"]?.jsonPrimitive?.content ?: return BackupImportResult(0)
        val name = obj["name"]?.jsonPrimitive?.content ?: id

        when (id) {
            "default" -> return BackupImportResult(1, defaultListSongs = songs)
            "love" -> {
                mergeOrSetSongs("love", songs)
                return BackupImportResult(1, loveListSongs = songs)
            }
            else -> {
                mergeOrSetUserSongs(id, name, songs)
                return BackupImportResult(1, importedUserLists = listOf(id to songs))
            }
        }
    }

    private fun mergeOrSetSongs(listId: String, songs: List<MusicInfo>) {
        val current = songListManager.getSongs(listId)
        val existingIds = current.map { it.id }.toSet()
        val toAdd = songs.filter { it.id !in existingIds }
        if (toAdd.isNotEmpty()) {
            songListManager.saveSongs(listId, current + toAdd)
        }
    }

    private fun mergeOrSetUserSongs(id: String, name: String, songs: List<MusicInfo>) {
        val existing = songListManager.lists.value.find { it.id == id }
        if (existing != null) {
            val current = songListManager.getSongs(existing.id)
            val existingIds = current.map { it.id }.toSet()
            val toAdd = songs.filter { it.id !in existingIds }
            songListManager.saveSongs(existing.id, current + toAdd)
        } else {
            val list = songListManager.createList(name.ifEmpty { "Imported" })
            songListManager.saveSongs(list.id, songs)
        }
    }

    // ── Settings import ───────────────────────────────────────────

    /**
     * Import settings from a backup file.
     * @return Parsed AppSetting, or null on error.
     */
    /** Import only settings (legacy method — prefer importAll to avoid double-read). */
    fun importSettings(uri: Uri): AppSetting? = importAll(uri).first

    /**
     * Read and parse a backup file, handling gzip, JSON, and double-serialization.
     */
    private fun readAndParseRoot(uri: Uri): JsonObject? {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val content = if (isGzipped(raw)) ungzip(raw) else raw
        var contentStr = String(content, Charsets.UTF_8)

        // Fix double-serialization (PC v1.14.0 bug)
        if (contentStr.trimStart().startsWith("\"")) {
            try { contentStr = json.decodeFromString(JsonElement.serializer(), contentStr).jsonPrimitive.content }
            catch (_: Exception) {}
        }
        return try { json.parseToJsonElement(contentStr).jsonObject } catch (_: Exception) { null }
    }

    // ── Conversion helpers ────────────────────────────────────────

    private fun MusicInfo.toBackupMusicInfo(): BackupMusicInfo {
        val metaObj = buildJsonObject {
            when (val m = meta) {
                is MusicInfoMetaOnline -> {
                    put("songId", m.songId)
                    put("albumName", m.albumName)
                    if (m.picUrl != null) put("picUrl", m.picUrl)
                    if (m.albumId != null) put("albumId", m.albumId)

                    // qualitys
                    val qsArr = buildJsonArray {
                        for (q in m.qualitys) {
                            add(buildJsonObject {
                                put("type", q.type)
                                if (q.size != null) put("size", q.size)
                            })
                        }
                    }
                    put("qualitys", qsArr)

                    // _qualitys (derived map for RN compatibility)
                    val qsMap = buildJsonObject {
                        for (q in m.qualitys) {
                            put(q.type, buildJsonObject {
                                if (q.size != null) put("size", q.size)
                            })
                        }
                    }
                    put("_qualitys", qsMap)

                    // Source-specific extra fields
                    for ((key, value) in m.extra) {
                        put(key, value)
                    }
                }
                is MusicInfoMetaLocal -> {
                    put("songId", m.songId)
                    put("albumName", m.albumName)
                    if (m.picUrl != null) put("picUrl", m.picUrl)
                    put("filePath", m.filePath)
                    put("ext", m.ext)
                }
            }
        }
        return BackupMusicInfo(id, name, singer, source, interval, metaObj)
    }

    private fun BackupMusicInfo.toMusicInfo(): MusicInfo? {
        if (id.isBlank() || name.isBlank()) return null
        val m = meta
        val songId = m["songId"]?.jsonPrimitive?.content ?: ""
        val albumName = m["albumName"]?.jsonPrimitive?.content ?: ""

        if (source == "local") {
            return MusicInfo(
                id = id, name = name, singer = singer, source = source, interval = interval,
                meta = MusicInfoMetaLocal(
                    songId = songId, albumName = albumName,
                    picUrl = m["picUrl"]?.jsonPrimitive?.contentOrNull,
                    filePath = m["filePath"]?.jsonPrimitive?.content ?: "",
                    ext = m["ext"]?.jsonPrimitive?.content ?: ""
                )
            )
        }

        // Extract qualitys
        val qualitys = m["qualitys"]?.jsonArray?.mapNotNull { q ->
            val qo = q.jsonObject
            val type = qo["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
            MusicQualityType(type = type, size = qo["size"]?.jsonPrimitive?.contentOrNull)
        } ?: emptyList()

        // Collect extra fields (source-specific data)
        val knownKeys = setOf("songId", "albumName", "picUrl", "albumId", "qualitys", "_qualitys",
            "filePath", "ext", "toggleMusicInfo")
        val extra = mutableMapOf<String, String>()
        for ((key, value) in m) {
            if (key in knownKeys) continue
            val str = value.jsonPrimitive.contentOrNull ?: continue
            extra[key] = str
        }

        return MusicInfo(
            id = id, name = name, singer = singer, source = source, interval = interval,
            meta = MusicInfoMetaOnline(
                songId = songId, albumName = albumName,
                picUrl = m["picUrl"]?.jsonPrimitive?.contentOrNull,
                qualitys = qualitys,
                albumId = m["albumId"]?.jsonPrimitive?.contentOrNull,
                extra = extra
            )
        )
    }

    /** Convert old-format music info (v1) where songId was under "songmid" key. */
    private fun JsonElement.toMusicInfoV1(): MusicInfo? {
        val obj = jsonObject
        val id = obj["id"]?.jsonPrimitive?.content ?: return null
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        val singer = obj["singer"]?.jsonPrimitive?.content ?: ""
        val source = obj["source"]?.jsonPrimitive?.content ?: ""
        val interval = obj["interval"]?.jsonPrimitive?.contentOrNull

        // In old format, songId is at musicInfo.songmid, not in meta
        val metaObj = obj["meta"]?.jsonObject
        val songId = metaObj?.get("songId")?.jsonPrimitive?.content
            ?: obj["songmid"]?.jsonPrimitive?.content ?: ""

        val albumName = metaObj?.get("albumName")?.jsonPrimitive?.content ?: ""
        val picUrl = metaObj?.get("picUrl")?.jsonPrimitive?.contentOrNull
            ?: obj["img"]?.jsonPrimitive?.contentOrNull

        val qualitys = metaObj?.get("qualitys")?.jsonArray?.mapNotNull { q ->
            val qo = q.jsonObject
            MusicQualityType(
                type = qo["type"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                size = qo["size"]?.jsonPrimitive?.contentOrNull
            )
        } ?: emptyList()

        val knownKeys = setOf("songId", "albumName", "picUrl", "albumId", "qualitys", "_qualitys")
        val extra = mutableMapOf<String, String>()
        metaObj?.let { m ->
            for ((key, value) in m) {
                if (key in knownKeys) continue
                extra[key] = value.jsonPrimitive.contentOrNull ?: continue
            }
        }

        return MusicInfo(id, name, singer, source, interval,
            meta = MusicInfoMetaOnline(songId, albumName, picUrl, qualitys,
                albumId = metaObj?.get("albumId")?.jsonPrimitive?.contentOrNull,
                extra = extra))
    }

    // ── Gzip helpers ──────────────────────────────────────────────

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun ungzip(data: ByteArray): ByteArray {
        return GZIPInputStream(data.inputStream()).use { it.readBytes() }
    }

    private fun isGzipped(data: ByteArray): Boolean {
        return data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
    }

    companion object {
        val LXM_FILE_EXTENSIONS = arrayOf("application/json", "application/octet-stream", "*/*")
    }
}
