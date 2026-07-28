package cn.guoyujie666.music.compose.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Top-level backup file container.
 * Matches the RN .lxmc format: { type: "playList_v2", data: [...] }
 */
@Serializable
data class BackupFile(
    val type: String,
    val data: List<BackupListEntry>
)

/**
 * A single list entry in the backup.
 * For built-in lists (default, love): only id, name, list.
 * For user lists: also source, sourceListId, locationUpdateTime.
 */
@Serializable
data class BackupListEntry(
    val id: String,
    val name: String,
    val list: List<BackupMusicInfo>,
    val source: String? = null,
    val sourceListId: String? = null,
    val locationUpdateTime: Long? = null
)

/**
 * Music info in the RN-compatible backup format.
 * `meta` is a raw JsonObject to preserve all source-specific fields
 * and avoid polymorphic discriminator issues.
 */
@Serializable
data class BackupMusicInfo(
    val id: String,
    val name: String,
    val singer: String,
    val source: String,
    val interval: String? = null,
    val meta: JsonObject
)
