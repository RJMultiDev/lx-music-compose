package cn.guoyujie666.music.compose.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.ui.i18n.t

/**
 * A single row in a music list.
 *
 * Layout when showSource=true (aggregate search / MyList):
 *   [idx] [song name           ] [SQ] [⋮] [interval]
 *         [kw] [singer · album]
 *
 * Layout when showSource=false (specific-source search / leaderboard / songlist):
 *   [idx] [song name                ] [⋮] [interval]
 *         [SQ] [singer · album]          ← quality moves to source position
 *
 * MyList: showSource=true, showQuality=false → source only, no quality.
 *
 * Ported from src/components/OnlineList/ListItem.tsx.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicListItem(
    musicInfo: MusicInfo,
    index: Int,
    showAlbumName: Boolean = false,
    showSource: Boolean = true,
    showInterval: Boolean = true,
    showQuality: Boolean = true,
    showAction: Boolean = false,
    isPlaying: Boolean = false,
    onPlay: (MusicInfo) -> Unit = {},
    onLongPress: (MusicInfo) -> Unit = {},
    onAction: ((MusicInfo) -> Unit)? = null,
    // Action menu callbacks (optional, for custom menu items)
    onPlayLater: ((MusicInfo) -> Unit)? = null,
    onAddTo: ((MusicInfo) -> Unit)? = null,
    onMoveTo: ((MusicInfo) -> Unit)? = null,
    onToggleSource: ((MusicInfo) -> Unit)? = null,
    onSourceDetail: ((MusicInfo) -> Unit)? = null,
    onDislike: ((MusicInfo) -> Unit)? = null,
    onDelete: ((MusicInfo) -> Unit)? = null,
    onShare: ((MusicInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onPlay(musicInfo) },
                onLongClick = { onLongPress(musicInfo) }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number / index (or play icon when this song is currently playing)
        if (isPlaying) {
            Icon(
                Icons.Filled.PlayArrow, "Playing",
                modifier = Modifier.width(36.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp)
            )
        }

        // Song info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Pick highest available quality (matches RN useQualityTag priority chain)
            val qualityOrder = mapOf("flac24bit" to 4, "flac" to 3, "ape" to 3, "wav" to 3, "320k" to 2)
            val qualityType = if (showQuality && musicInfo.meta is cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline)
                (musicInfo.meta as cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline).qualitys
                    .filter { it.type in qualityOrder }
                    .maxByOrNull { qualityOrder[it.type]!! }?.type ?: ""
            else ""
            val hasQuality = qualityType.isNotEmpty()

            // Row 1: song name + quality (when source IS shown, quality goes here)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = musicInfo.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (hasQuality && showSource) {
                    Spacer(Modifier.width(4.dp))
                    QualityBadge(qualityType)
                }
            }

            // Row 2: singer + album, plus source OR quality (but not both)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showSource && musicInfo.source != "local") {
                    SourceBadge(source = musicInfo.source)
                    Spacer(Modifier.width(4.dp))
                } else if (hasQuality && !showSource) {
                    // Quality moves to source position when source is hidden
                    QualityBadge(qualityType)
                    Spacer(Modifier.width(4.dp))
                }

                Text(
                    text = musicInfo.singer,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (showAlbumName && musicInfo.meta.albumName.isNotEmpty()) {
                    Text(
                        text = " · ${musicInfo.meta.albumName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Interval
        val intervalStr = musicInfo.interval
        if (showInterval && intervalStr != null) {
            Text(
                text = intervalStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Action button (three-dot) + dropdown menu — rightmost
        if (showAction) {
            var menuExpanded by remember { mutableStateOf(false) }
            val clipboard = LocalClipboardManager.current
            Box(Modifier.size(32.dp)) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.MoreVert, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MusicActionMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onPlay = { onPlay(musicInfo); menuExpanded = false },
                    onPlayLater = { onPlayLater?.invoke(musicInfo); menuExpanded = false },
                    onAddTo = { onAddTo?.invoke(musicInfo); menuExpanded = false },
                    onMoveTo = { onMoveTo?.invoke(musicInfo); menuExpanded = false },
                    onToggleSource = { onToggleSource?.invoke(musicInfo); menuExpanded = false },
                    onCopyName = {
                        clipboard.setText(AnnotatedString("${musicInfo.name} - ${musicInfo.singer}"))
                        onShare?.invoke(musicInfo)
                        menuExpanded = false
                    },
                    onSourceDetail = { onSourceDetail?.invoke(musicInfo); menuExpanded = false },
                    onDislike = { onDislike?.invoke(musicInfo); menuExpanded = false },
                    onDelete = { onDelete?.invoke(musicInfo); menuExpanded = false },
                    showPlayLater = onPlayLater != null,
                    showMyListActions = onMoveTo != null
                )
            }
        }
    }
}

// ── Quality badge (SQ / HQ / Hi-Res) ──────────────────────────
// Labels ported from RN i18n: quality_lossless_24bit / quality_lossless / quality_high_quality

@Composable
fun QualityBadge(quality: String) {
    val (label, color) = when (quality) {
        "flac24bit" -> "24bit" to Color(0xFFE91E63)   // pink — lossless 24bit
        "flac"      -> "SQ"    to Color(0xFFE91E63)   // pink — lossless
        "ape"       -> "SQ"    to Color(0xFFE91E63)
        "wav"       -> "SQ"    to Color(0xFFE91E63)
        "320k"      -> "HQ"    to Color(0xFFFF9800)   // orange — high quality
        else -> "" to Color.Unspecified
    }
    if (label.isNotEmpty()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Source badge (color-coded per source) ─────────────────────

private val sourceColors = mapOf(
    "kw" to Color(0xFFFF9800),  // orange — 酷我
    "kg" to Color(0xFF2196F3),  // blue   — 酷狗
    "tx" to Color(0xFF4CAF50),  // green  — QQ
    "wy" to Color(0xFFE91E63),  // pink   — 网易
    "mg" to Color(0xFF9C27B0),  // purple — 咪咕
)

private val sourceLabels = mapOf("kw" to "KW", "kg" to "KG", "tx" to "QQ", "wy" to "WY", "mg" to "MG")

var globalSourceNameType: String = "real"

@Composable
fun SourceBadge(source: String) {
    // Music list items always show abbreviations (KW/KG/QQ/WY/MG);
    // other places use globalSourceNameType + i18n for full names.
    val label = sourceLabels[source] ?: source.uppercase()
    val color = sourceColors[source] ?: MaterialTheme.colorScheme.secondary
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium
    )
}

// ── Music action menu (ported from OnlineList/ListMenu.tsx) ─────

@Composable
fun MusicActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit = {},
    onPlayLater: () -> Unit = {},
    onAddTo: () -> Unit = {},
    onCopyName: () -> Unit = {},
    onSourceDetail: () -> Unit = {},
    onDislike: () -> Unit = {},
    onDelete: () -> Unit = {},
    onMoveTo: () -> Unit = {},
    onToggleSource: () -> Unit = {},
    showPlayLater: Boolean = true,
    showMyListActions: Boolean = false,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(t("play")) },
            leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
            onClick = onPlay
        )
        if (showPlayLater) {
            DropdownMenuItem(
                text = { Text(t("play_later")) },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                onClick = onPlayLater
            )
        }
        HorizontalDivider()
        if (showMyListActions) {
            DropdownMenuItem(
                text = { Text(t("add_to")) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = onAddTo
            )
            DropdownMenuItem(
                text = { Text(t("move_to")) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = onMoveTo ?: {}
            )
        } else {
            DropdownMenuItem(
                text = { Text(t("add_to")) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = onAddTo
            )
        }
        HorizontalDivider()
        if (showMyListActions) {
            DropdownMenuItem(
                text = { Text(t("toggle_source")) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = onToggleSource
            )
        }
        DropdownMenuItem(
            text = { Text(t("copy_name")) },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
            onClick = onCopyName
        )
        DropdownMenuItem(
            text = { Text(t("source_detail")) },
            leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
            onClick = onSourceDetail
        )
        if (showMyListActions) {
            DropdownMenuItem(
                text = { Text(t("delete"), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null, tint = MaterialTheme.colorScheme.error) },
                onClick = onDelete
            )
        } else {
            DropdownMenuItem(
                text = { Text(t("dislike")) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = onDislike
            )
        }
    }
}
