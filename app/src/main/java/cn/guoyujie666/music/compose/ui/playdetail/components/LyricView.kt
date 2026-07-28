package cn.guoyujie666.music.compose.ui.playdetail.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.guoyujie666.music.compose.core.lyric.LyricLine
import cn.guoyujie666.music.compose.ui.i18n.t
import kotlin.math.roundToLong

@Composable
fun LyricView(
    lyrics: List<LyricLine>,
    currentTime: Long,
    lyricFontSize: androidx.compose.ui.unit.TextUnit = MaterialTheme.typography.bodyLarge.fontSize,
    transFontSize: androidx.compose.ui.unit.TextUnit = MaterialTheme.typography.bodySmall.fontSize,
    lyricAlign: String = "center",
    allowSeek: Boolean = true,
    showTranslation: Boolean = false,
    showRomaji: Boolean = false,
    showWordHighlight: Boolean = false,
    showDuetLyric: Boolean = false,
    onSeekToLine: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listHeight by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragTime by remember { mutableLongStateOf(0L) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val activeLineIndex = lyrics.indexOfLast { it.timeMs <= currentTime }.takeIf { it >= 0 } ?: 0
    val spacerCount = 9

    // Auto-scroll to center active line during playback (not when dragging)
    LaunchedEffect(activeLineIndex) {
        if (!isDragging && listHeight > 0) {
            val targetIdx = spacerCount + activeLineIndex
            listState.animateScrollToItem(
                index = maxOf(0, targetIdx),
                scrollOffset = -(listHeight / 2).toInt()
            )
        }
    }

    // Scroll LazyColumn to follow drag position
    LaunchedEffect(dragTime) {
        if (isDragging && listHeight > 0 && lyrics.isNotEmpty()) {
            val idx = lyrics.indexOfFirst { it.timeMs == dragTime }
                .takeIf { it >= 0 } ?: ((dragProgress * (lyrics.size - 1)).roundToLong().toInt().coerceIn(0, lyrics.size - 1))
            val targetIdx = spacerCount + idx
            listState.scrollToItem(
                index = maxOf(0, targetIdx),
                scrollOffset = -(listHeight / 2).toInt()
            )
        }
    }

    val textAlign = when (lyricAlign) {
        "left" -> TextAlign.Start; "right" -> TextAlign.End; else -> TextAlign.Center
    }

    if (lyrics.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(t("no_lyrics"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val hasBgImage = MaterialTheme.colorScheme.background.alpha < 1f

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().onSizeChanged { listHeight = it.height }
                .then(if (hasBgImage) Modifier.graphicsLayer { alpha = 0.99f }.drawWithContent {
                    drawContent()
                    // Top fade mask
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.06f to Color.Black
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                    // Bottom fade mask
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.94f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                } else Modifier),
            userScrollEnabled = !isDragging && !allowSeek
        ) {
            // Dynamic spacers: fill half the screen so first lyric can reach center
            items(spacerCount) { Box(Modifier.fillMaxWidth().height(48.dp)) }

            itemsIndexed(lyrics) { index, line ->
                val isActive = index == activeLineIndex && !isDragging
                val color by animateColorAsState(
                    targetValue = if (isActive) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(300)
                )
                val fontSize = if (isActive) lyricFontSize * 1.2f else lyricFontSize
                val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

                // Translation highlight: in word mode, fade in over 0.5s,
                // starting 0.5s before the last word of the line finishes.
                // In line mode, highlight with the main lyric line.
                val lastWord = line.words?.lastOrNull()
                val lastWordEnd = lastWord?.let { line.timeMs + it.startTime + it.duration } ?: Long.MAX_VALUE
                val transTarget = when {
                    line.words != null && currentTime >= lastWordEnd - 500 -> MaterialTheme.colorScheme.primary
                    line.words == null && isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
                val transColor by animateColorAsState(
                    targetValue = transTarget,
                    animationSpec = tween(500)
                )
                val romaColor = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }

                // Singer role styling for duet lyrics (only when enabled)
                val roleArrangement = if (showDuetLyric) when (line.singerRole) {
                    cn.guoyujie666.music.compose.core.lyric.SingerRole.Male -> Arrangement.Start
                    cn.guoyujie666.music.compose.core.lyric.SingerRole.Female -> Arrangement.End
                    cn.guoyujie666.music.compose.core.lyric.SingerRole.Chorus -> Arrangement.Center
                    else -> when (textAlign) {
                        TextAlign.Start -> Arrangement.Start
                        TextAlign.End -> Arrangement.End
                        else -> Arrangement.Center
                    }
                } else when (textAlign) {
                    TextAlign.Start -> Arrangement.Start
                    TextAlign.End -> Arrangement.End
                    else -> Arrangement.Center
                }
                val roleColor = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> color
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
                    if (showWordHighlight && line.words != null) {
                        WordLyricLine(
                            line = line, currentTime = currentTime, isActive = isActive,
                            fontSize = fontSize, fontWeight = fontWeight,
                            dimColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            roleArrangement = roleArrangement,
                            roleColor = roleColor
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = roleArrangement) {
                            Text(text = line.text, fontSize = fontSize, fontWeight = fontWeight,
                                color = roleColor)
                        }
                    }
                    if (showTranslation && line.translation != null)
                        Text(line.translation!!, fontSize = transFontSize, color = transColor, textAlign = textAlign, modifier = Modifier.fillMaxWidth())
                    if (showRomaji && line.romaji != null)
                        Text(line.romaji!!, fontSize = transFontSize, color = romaColor, textAlign = textAlign, modifier = Modifier.fillMaxWidth())
                }
            }

            // Bottom spacers: allow the last lyric to scroll to center
            items(spacerCount) { Box(Modifier.fillMaxWidth().height(48.dp)) }
        }

        // Transparent drag overlay — rendered on top of LazyColumn to intercept vertical drags
        if (allowSeek) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(lyrics.size) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                hideJob?.cancel()
                                isDragging = true
                            },
                            onDragEnd = {
                                // Keep indicator visible for 5s after release
                                hideJob = scope.launch {
                                    kotlinx.coroutines.delay(5000)
                                    isDragging = false
                                }
                            },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { _, amount ->
                                // Negate: swipe up → later time, swipe down → earlier time
                                dragProgress = (dragProgress - amount / listHeight).coerceIn(-0.05f, 1.05f)
                                if (lyrics.isNotEmpty()) {
                                    val idx = (dragProgress * (lyrics.size - 1)).roundToLong()
                                        .toInt().coerceIn(0, lyrics.size - 1)
                                    dragTime = lyrics[idx].timeMs
                                }
                            }
                        )
                    }
            )
        }

        // Top/bottom fade: gradient overlay for non-bg-image themes
        if (!hasBgImage) {
            val bgColor = MaterialTheme.colorScheme.background
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(bgColor, Color.Transparent)))
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, bgColor)))
            )
        }

        // Drag indicator: dashed line + time + play button
        if (isDragging && allowSeek) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                // Dashed line
                Box(
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                        .drawWithContent {
                            drawLine(Color.Gray.copy(alpha = 0.5f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                        }
                )
                // Time + play button on the right
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(dragTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { onSeekToLine(dragTime); isDragging = false },
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/**
 * Render a single lyric line in word-by-word (lxlrc) mode.
 * Each word/syllable is highlighted progressively as the song plays.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordLyricLine(
    line: LyricLine, currentTime: Long, isActive: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit, fontWeight: FontWeight,
    dimColor: Color, roleArrangement: Arrangement.Horizontal? = null,
    roleColor: Color = Color.Unspecified
) {
    val brightColor = if (isActive) MaterialTheme.colorScheme.primary
        else if (roleColor != Color.Unspecified) roleColor else dimColor
    val playedColor = MaterialTheme.colorScheme.primary

    val arrangement = roleArrangement ?: Arrangement.Center

    val words = line.words!!
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = arrangement
    ) {
        for ((index, word) in words.withIndex()) {
            val wordStart = line.timeMs + word.startTime
            val wordEnd = wordStart + word.duration

            val isBeingPlayed = currentTime in wordStart..wordEnd
            val wordTarget = when {
                currentTime >= wordEnd -> playedColor
                currentTime < wordStart -> dimColor
                else -> brightColor
            }
            val wordColor by animateColorAsState(
                targetValue = wordTarget,
                animationSpec = tween(durationMillis = word.duration.toInt().coerceIn(100, 500))
            )

            Text(
                text = word.text + if (index < words.size - 1) "" else "",
                fontSize = fontSize,
                fontWeight = if (isActive && isBeingPlayed) fontWeight else FontWeight.Normal,
                color = wordColor
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60; val s = totalSeconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
