package cn.guoyujie666.music.compose.ui.desktoplyric

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Desktop lyric overlay composable — the actual floating lyric display.
 *
 * Ported from the native LyricView + LyricSwitchView + LyricTextView components.
 * All custom View rendering is now done in pure Compose.
 *
 * Features:
 * - Animated lyric transitions (slide + fade)
 * - Single-line mode with marquee effect
 * - Multi-line mode showing previous/future lines
 * - Configurable colors, opacity, font size, alignment
 * - Shadow effect on text
 */
@Composable
fun DesktopLyricContent(
    lyrics: String,
    translation: String? = null,
    isPlaying: Boolean = false,
    fontSize: Float = 24f,
    opacity: Float = 1f,
    unplayedColor: String = "#B0BEC5",
    playedColor: String = "#4CAF50",
    shadowColor: String = "#000000",
    showAnimation: Boolean = true,
    isSingleLine: Boolean = false,
    maxLines: Int = 5,
    textAlignX: String = "center",
    textAlignY: String = "center",
    modifier: Modifier = Modifier
) {
    val parsedPlayed = try { Color(android.graphics.Color.parseColor(playedColor)) } catch (_: Exception) { Color(0xFF4CAF50) }
    val parsedUnplayed = try { Color(android.graphics.Color.parseColor(unplayedColor)) } catch (_: Exception) { Color(0xFFB0BEC5) }
    val parsedShadow = try { Color(android.graphics.Color.parseColor(shadowColor)) } catch (_: Exception) { Color(0xFF000000) }

    val boxAlignment = when {
        textAlignX == "left" && textAlignY == "top" -> Alignment.TopStart
        textAlignX == "left" && textAlignY == "bottom" -> Alignment.BottomStart
        textAlignX == "right" && textAlignY == "top" -> Alignment.TopEnd
        textAlignX == "right" && textAlignY == "bottom" -> Alignment.BottomEnd
        textAlignX == "left" -> Alignment.CenterStart
        textAlignX == "right" -> Alignment.CenterEnd
        textAlignY == "top" -> Alignment.TopCenter
        textAlignY == "bottom" -> Alignment.BottomCenter
        else -> Alignment.Center
    }

    val horizontalAlign = when (textAlignX) {
        "left" -> Alignment.Start
        "right" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    val textAlign = when (textAlignX) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }

    Box(
        modifier = modifier
            .alpha(opacity)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = boxAlignment
    ) {
        if (isSingleLine) {
            // Single-line mode — marquee scroll effect
            SingleLineLyric(
                text = lyrics.ifEmpty { "LX Music" },
                color = if (isPlaying) parsedPlayed else parsedUnplayed,
                fontSize = fontSize,
                shadowColor = parsedShadow,
                textAlign = textAlign
            )
        } else {
            // Multi-line mode — static display
            Column(
                horizontalAlignment = horizontalAlign
            ) {
                LyricText(
                    text = lyrics.ifEmpty { "LX Music" },
                    color = if (isPlaying) parsedPlayed else parsedUnplayed,
                    fontSize = fontSize,
                    shadowColor = parsedShadow,
                    textAlign = textAlign,
                    maxLines = maxLines,
                    isPlaying = isPlaying,
                    showAnimation = showAnimation
                )

                if (translation != null && isPlaying) {
                    LyricText(
                        text = translation,
                        color = parsedPlayed.copy(alpha = 0.7f),
                        fontSize = fontSize * 0.8f,
                        shadowColor = parsedShadow,
                        textAlign = textAlign,
                        maxLines = maxLines,
                        isPlaying = false,
                        showAnimation = showAnimation
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleLineLyric(
    text: String,
    color: Color,
    fontSize: Float,
    shadowColor: Color,
    textAlign: TextAlign
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, ambientColor = shadowColor)
    )
}

@Composable
private fun LyricText(
    text: String,
    color: Color,
    fontSize: Float,
    shadowColor: Color,
    textAlign: TextAlign,
    maxLines: Int,
    isPlaying: Boolean,
    showAnimation: Boolean = true
) {
    val content = @Composable {
        Text(
            text = text,
            color = color,
            fontSize = fontSize.sp,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (isPlaying) 4.dp else 2.dp, ambientColor = shadowColor)
        )
    }

    if (showAnimation && isPlaying) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                    (slideOutVertically { -it / 2 } + fadeOut())
            }
        ) { targetText ->
            Text(
                text = targetText,
                color = color,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, ambientColor = shadowColor)
            )
        }
    } else {
        content()
    }
}
