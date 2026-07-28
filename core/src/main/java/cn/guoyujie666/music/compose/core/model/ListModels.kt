package cn.guoyujie666.music.compose.core.model

import kotlinx.serialization.Serializable

// === User List Info (custom playlists) ===

@Serializable
data class UserListInfo(
    val id: String,
    val name: String,
    val source: OnlineSource? = null,
    val sourceListId: String? = null,
    val locationUpdateTime: Long? = null
)

// === Default List Info (试听列表) ===

@Serializable
data class MyDefaultListInfo(
    val id: String = ListIds.DEFAULT,
    val name: String = "试听列表"
)

// === Love List Info (我的收藏) ===

@Serializable
data class MyLoveListInfo(
    val id: String = ListIds.LOVE,
    val name: String = "我的收藏"
)

// === Temp List Info (临时列表) ===

@Serializable
data class MyTempListInfo(
    val id: String = ListIds.TEMP,
    val name: String = "临时列表",
    val meta: TempListMeta = TempListMeta()
)

@Serializable
data class TempListMeta(
    val id: String? = null
)

// === All Lists ===

data class MyAllLists(
    val defaultList: MyDefaultListInfo = MyDefaultListInfo(),
    val loveList: MyLoveListInfo = MyLoveListInfo(),
    val userList: List<UserListInfo> = emptyList(),
    val tempList: MyTempListInfo = MyTempListInfo()
)

// === Full list data (with actual music content) ===

data class ListDataFull(
    val defaultList: List<MusicInfo> = emptyList(),
    val loveList: List<MusicInfo> = emptyList(),
    val userList: List<UserListInfoFull> = emptyList(),
    val tempList: List<MusicInfo> = emptyList()
)

data class UserListInfoFull(
    val id: String,
    val name: String,
    val list: List<MusicInfo> = emptyList(),
    val source: OnlineSource? = null,
    val sourceListId: String? = null,
    val locationUpdateTime: Long? = null
)

// === Search History ===

typealias SearchHistoryList = List<String>

// === List Position Info (saved scroll positions) ===

typealias ListPositionInfo = Map<String, Int>

// === List Update Info ===

@Serializable
data class ListUpdateInfo(
    val updateTime: Long,
    val isAutoUpdate: Boolean = false
)

typealias ListUpdateInfoMap = Map<String, ListUpdateInfo>

// === List Actions (for sync/CRUD operations) ===

data class ListActionDataOverwrite(
    val defaultList: List<MusicInfo> = emptyList(),
    val loveList: List<MusicInfo> = emptyList(),
    val userList: List<UserListInfoFull> = emptyList(),
    val tempList: List<MusicInfo> = emptyList()
)

data class ListActionAdd(
    val position: Int,
    val listInfos: List<UserListInfo>
)

data class ListActionUpdatePosition(
    val ids: List<String>,
    val position: Int
)

data class ListActionMusicAdd(
    val id: String,
    val musicInfos: List<MusicInfo>,
    val addMusicLocationType: AddMusicLocationType
)

data class ListActionMusicMove(
    val fromId: String,
    val toId: String,
    val musicInfos: List<MusicInfo>,
    val addMusicLocationType: AddMusicLocationType
)

data class ListActionMusicRemove(
    val listId: String,
    val ids: List<String>
)

data class ListActionMusicUpdatePosition(
    val listId: String,
    val position: Int,
    val ids: List<String>
)

data class ListActionMusicOverwrite(
    val listId: String,
    val musicInfos: List<MusicInfo>
)

// === List Save ===

typealias ListSaveType = String  // "myList" | "downloadList"
