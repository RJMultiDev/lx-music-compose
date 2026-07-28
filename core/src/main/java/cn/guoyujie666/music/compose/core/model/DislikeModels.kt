package cn.guoyujie666.music.compose.core.model

import kotlinx.serialization.Serializable

// === Dislike Music Info ===

@Serializable
data class DislikeMusicInfo(
    val name: String,
    val singer: String
)

// === Dislike Rules ===

typealias DislikeRules = String

// === Dislike Info ===

data class DislikeInfo(
    val names: Set<String> = emptySet(),        // "name@singer" combinations
    val musicNames: Set<String> = emptySet(),   // just song names
    val singerNames: Set<String> = emptySet(),  // just singer names
    val rules: DislikeRules = ""
)

// === Dislike Actions (for sync) ===

typealias DislikeActionOverwrite = DislikeInfo

data class DislikeActionAdd(
    val name: String,
    val singer: String
)
