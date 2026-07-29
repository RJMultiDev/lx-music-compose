package cn.guoyujie666.music.compose.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Color generation utilities ported from the RN version's colorUtils.js.
 * Generates shade palettes and alpha variants from a single primary color.
 */
object ColorGenerator {

    /**
     * Linear shade: lighten (positive p) or darken (negative p) a color.
     * p range: -1.0 to 1.0
     * Ported from RGB_Linear_Shade in colorUtils.js
     */
    fun linearShade(p: Float, color: Color): Color {
        val n = p < 0
        val t = if (n) 0f else 255f * p
        val factor = if (n) 1f + p else 1f - p
        val r = (color.red * 255f * factor + t).roundToInt().coerceIn(0, 255)
        val g = (color.green * 255f * factor + t).roundToInt().coerceIn(0, 255)
        val b = (color.blue * 255f * factor + t).roundToInt().coerceIn(0, 255)
        return Color(r, g, b, (color.alpha * 255).roundToInt())
    }

    /**
     * Modify alpha of a color.
     * p range: -1.0 to 1.0 (positive = more transparent, negative = more opaque)
     * Ported from RGB_Alpha_Shade in colorUtils.js
     */
    fun alphaShade(p: Float, color: Color): Color {
        val n = p < 0
        val a = if (color.alpha < 1f) {
            val current = color.alpha
            val newAlpha = current - if (n) (1f - current) * p else current * p
            if (n) newAlpha.coerceAtLeast(0f) else newAlpha.coerceAtMost(1f)
        } else {
            (1f - p).coerceIn(0f, 1f)
        }
        return color.copy(alpha = a)
    }

    /**
     * Generate theme palette from a primary color.
     * Ported from createThemeColors() in utils.js.
     *
     * Returns key colors needed for Material 3 mapping:
     * - primary shades (dark-100..dark-1000, light-100..light-1000)
     * - c-theme (primary for light, primary-light-900 for dark)
     */
    data class ThemePalette(
        val primary: Color,
        val primaryDark: List<Color>,   // 10 items: dark-100..dark-1000
        val primaryLight: List<Color>,  // 10 items: light-100..light-1000
        val theme: Color,               // c-theme
        val fontColors: List<Color>,    // 21 items: c-000..c-1000
        // M3E additional tokens for full coverage
        val surface: Color? = null,
        val onSurface: Color? = null,
        val surfaceVariant: Color? = null,
        val onSurfaceVariant: Color? = null,
        val outline: Color? = null,
        val outlineVariant: Color? = null,
        val inverseSurface: Color? = null,
        val inverseOnSurface: Color? = null,
        // Dark mode variants
        val surfaceDark: Color? = null,
        val onSurfaceDark: Color? = null,
        val surfaceVariantDark: Color? = null,
        val onSurfaceVariantDark: Color? = null,
        val outlineDark: Color? = null,
        val outlineVariantDark: Color? = null,
        val inverseSurfaceDark: Color? = null,
        val inverseOnSurfaceDark: Color? = null
    )

    fun generatePalette(
        primary: Color, 
        font: Color?, 
        isDark: Boolean,
        surfaceColor: Color? = null,
        surfaceVariantColor: Color? = null,
        outlineColor: Color? = null
    ): ThemePalette {
        val fontColor = font ?: if (isDark) Color(229, 229, 229) else Color(33, 33, 33)

        // Dark shades
        val darkShades = mutableListOf<Color>()
        var preColor = primary
        for (i in 1..10) {
            preColor = linearShade(if (isDark) 0.2f else -0.1f, preColor)
            darkShades.add(preColor)
        }

        // Light shades
        val lightShades = mutableListOf<Color>()
        preColor = primary
        for (i in 1..9) {
            preColor = linearShade(if (isDark) -0.1f else 0.2f, preColor)
            lightShades.add(preColor)
        }
        // 10th light shade (extra bright/dark)
        preColor = linearShade(if (isDark) -0.35f else 1f, preColor)
        lightShades.add(preColor)

        // c-theme
        val themeColor = if (isDark) lightShades[8] else primary // light-900

        // Font grayscale ramp (c-000 to c-1000)
        val fontColors = if (isDark) {
            createFontDarkColors(fontColor)
        } else {
            createFontLightColors(fontColor)
        }
        
        // Calculate default M3 surface colors if not provided
        val calculatedSurface = surfaceColor ?: if (isDark) {
            linearShade(-0.02f, primary.copy(alpha = 1f)) // Subtle tint
        } else {
            linearShade(0.98f, Color.White) // Near white
        }
        
        val calculatedSurfaceVariant = surfaceVariantColor ?: if (isDark) {
            linearShade(-0.05f, primary.copy(alpha = 1f)) // More tint than surface
        } else {
            linearShade(0.96f, Color.White) // Off-white
        }
        
        val calculatedOutline = outlineColor ?: linearShade(if (isDark) -0.3f else 0.2f, primary)

        return ThemePalette(
            primary = primary,
            primaryDark = darkShades,
            primaryLight = lightShades,
            theme = themeColor,
            fontColors = fontColors,
            surface = if (isDark) null else calculatedSurface,
            onSurface = fontColors[0], // lightest for dark text
            surfaceVariant = if (isDark) null else calculatedSurfaceVariant,
            onSurfaceVariant = fontColors[8], // medium grey
            outline = if (isDark) null else calculatedOutline,
            outlineVariant = if (isDark) null else linearShade(0.3f, primary),
            inverseSurface = if (isDark) null else Color(0xFF424242),
            inverseOnSurface = if (isDark) null else Color(0xFFFAFAFA),
            surfaceDark = calculatedSurface,
            onSurfaceDark = fontColors[0],
            surfaceVariantDark = calculatedSurfaceVariant,
            onSurfaceVariantDark = fontColors[8],
            outlineDark = calculatedOutline,
            outlineVariantDark = linearShade(0.1f, primary),
            inverseSurfaceDark = Color(0xFFEEEEEE),
            inverseOnSurfaceDark = Color(0xFF121212)
        )
    }

    private fun createFontLightColors(fontColor: Color): List<Color> {
        // c-1000 = fontColor, then progressively lighter to c-000
        val colors = mutableListOf<Color>()
        for (i in 0..20) {
            colors.add(linearShade(0.05f * (20 - i), fontColor))
        }
        // colors[0] = c-000 (lightest), colors[20] = c-1000 (darkest/fontColor)
        return colors
    }

    private fun createFontDarkColors(fontColor: Color): List<Color> {
        // c-1000 = fontColor, each step darker by -0.05
        val colors = mutableListOf<Color>()
        var preColor = fontColor
        val result = mutableListOf(fontColor) // c-1000
        for (i in 1..20) {
            preColor = linearShade(-0.05f, preColor)
            result.add(preColor)
        }
        // Reverse so index 0 = c-000 (darkest), index 20 = c-1000 (lightest)
        return result.reversed()
    }
}
