package cn.guoyujie666.music.compose.core.model

import kotlinx.serialization.Serializable

// === Music Quality Types ===

@Serializable
data class MusicQualityType(
    val type: Quality,
    val size: String? = null
)

@Serializable
data class MusicQualityTypeKg(
    val type: Quality,
    val size: String? = null,
    val hash: String = ""
)

// === Music Info Meta ===

@Serializable
sealed interface MusicInfoMeta {
    val songId: String
    val albumName: String
    val picUrl: String?
}

@Serializable
data class MusicInfoMetaOnline(
    override val songId: String,
    override val albumName: String,
    override val picUrl: String? = null,
    val qualitys: List<MusicQualityType> = emptyList(),
    val albumId: String? = null,
    // Per-source metadata stored as flexible map
    val extra: Map<String, String> = emptyMap()
) : MusicInfoMeta

@Serializable
data class MusicInfoMetaLocal(
    override val songId: String,
    override val albumName: String,
    override val picUrl: String? = null,
    val filePath: String = "",
    val ext: String = ""
) : MusicInfoMeta

// === Music Info ===

@Serializable
data class MusicInfo(
    val id: String,
    val name: String,
    val singer: String,
    val source: Source,
    val interval: String? = null,
    val meta: MusicInfoMeta = MusicInfoMetaOnline("", "")
) {
    val isLocal: Boolean get() = source == KnownSources.LOCAL

    companion object {
        fun local(
            id: String,
            name: String,
            singer: String,
            interval: String? = null,
            filePath: String = "",
            ext: String = ""
        ): MusicInfo = MusicInfo(
            id = id,
            name = name,
            singer = singer,
            source = KnownSources.LOCAL,
            interval = interval,
            meta = MusicInfoMetaLocal(
                songId = filePath,
                albumName = "",
                picUrl = null,
                filePath = filePath,
                ext = ext
            )
        )
    }
}

// Per-source extended music info (for KG-specific hash, TX-specific strMediaMid, etc.)
data class MusicInfoExtended(
    val musicInfo: MusicInfo,
    val kgHash: String? = null,
    val txStrMediaMid: String? = null,
    val txAlbumMid: String? = null,
    val mgCopyrightId: String? = null,
    val mgLrcUrl: String? = null,
    val mgMrcUrl: String? = null,
    val mgTrcUrl: String? = null
)

// === Lyric Info ===

@Serializable
data class LyricInfo(
    val lyric: String = "",
    val tlyric: String? = null,     // translation lyric
    val rlyric: String? = null,     // romaji lyric
    val lxlyric: String? = null     // word-by-word lyric
)

@Serializable
data class LyricInfoSave(
    val id: String,
    val lyrics: LyricInfo
)

// === Music URL Info ===

@Serializable
data class MusicUrlInfo(
    val id: String,
    val url: String
)

// === Music Info Other Source Save (cross-source matching) ===

@Serializable
data class MusicInfoOtherSourceSave(
    val id: String,
    val list: List<MusicInfo>
)
