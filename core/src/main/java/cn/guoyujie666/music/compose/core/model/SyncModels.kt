package cn.guoyujie666.music.compose.core.model

import kotlinx.serialization.Serializable

// === Sync Status ===

data class SyncStatus(
    val status: Boolean = false,
    val message: String = ""
)

// === Sync Key Info ===

@Serializable
data class SyncKeyInfo(
    val clientId: String,
    val key: String,
    val serverName: String
)

// === Sync URL Info ===

@Serializable
data class SyncUrlInfo(
    val wsProtocol: String = "ws",
    val httpProtocol: String = "http",
    val hostPath: String = "",
    val href: String = ""
)

// === Sync Mode Types ===

object SyncListMode {
    const val MERGE_LOCAL_REMOTE = "merge_local_remote"
    const val MERGE_REMOTE_LOCAL = "merge_remote_local"
    const val OVERWRITE_LOCAL_REMOTE = "overwrite_local_remote"
    const val OVERWRITE_REMOTE_LOCAL = "overwrite_remote_local"
    const val OVERWRITE_LOCAL_REMOTE_FULL = "overwrite_local_remote_full"
    const val OVERWRITE_REMOTE_LOCAL_FULL = "overwrite_remote_local_full"
    const val CANCEL = "cancel"
}

object SyncDislikeMode {
    const val MERGE_LOCAL_REMOTE = "merge_local_remote"
    const val MERGE_REMOTE_LOCAL = "merge_remote_local"
    const val OVERWRITE_LOCAL_REMOTE = "overwrite_local_remote"
    const val OVERWRITE_REMOTE_LOCAL = "overwrite_remote_local"
    const val CANCEL = "cancel"
}

// === Sync Mode Selection ===

data class SyncModeType(
    val type: String,  // "list" | "dislike"
    val mode: String
)

// === Sync Config ===

data class SyncListConfig(
    val skipSnapshot: Boolean = false
)

data class SyncDislikeConfig(
    val skipSnapshot: Boolean = false
)

data class SyncEnabledFeatures(
    val list: SyncListConfig? = null,   // null/false = disabled
    val dislike: SyncDislikeConfig? = null
)

typealias SyncSupportedFeatures = Map<String, Int>

// === Server Type ===
typealias SyncServerType = String  // "desktop-app" | "server"
