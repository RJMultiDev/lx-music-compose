package cn.guoyujie666.music.compose.core.model

import kotlinx.serialization.Serializable

// === User API Source Info ===

@Serializable
data class UserApiSourceInfo(
    val name: String,
    val type: String = "music",     // "music"
    val actions: List<String> = emptyList(),  // "musicUrl" | "lyric" | "pic"
    val qualitys: List<Quality> = emptyList()
)

// === User API Info (script metadata) ===

@Serializable
data class UserApiInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val allowShowUpdateAlert: Boolean = false,
    val author: String = "",
    val homepage: String = "",
    val version: String = "",
    val sources: Map<String, UserApiSourceInfo>? = null
)

// === User API Status ===

data class UserApiStatus(
    val status: Boolean,
    val message: String? = null,
    val apiInfo: UserApiInfo? = null
)

// === User API Update Info ===

@Serializable
data class UserApiUpdateInfo(
    val name: String,
    val description: String,
    val log: String,
    val updateUrl: String? = null
)

// === User API Request ===

data class UserApiRequestParams(
    val requestKey: String,
    val data: Any
)

// === Import User API ===

data class ImportUserApi(
    val apiInfo: UserApiInfo,
    val apiList: List<UserApiInfo>
)

// === Update Alert ===

data class UserApiUpdateAlert(
    val name: String,
    val log: String,
    val updateUrl: String?,
    val isError: Boolean = false
)
