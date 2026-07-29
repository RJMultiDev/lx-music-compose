package cn.guoyujie666.music.compose.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Registry of all 16 built-in themes, ported from createThemes.js.
 * Each theme is defined by a primary color; the full M3 ColorScheme
 * is generated at runtime via ColorGenerator.
 */
object ThemeRegistry {

    data class ThemeConfig(
        val id: String,
        val name: String,
        val isDark: Boolean,
        val primary: Color,
        val font: Color? = null,
        val backgroundImage: String? = null,
        // M3E additional palette colors for full token coverage
        val surfaceColor: Color? = null,
        val surfaceVariantColor: Color? = null,
        val outlineColor: Color? = null
    )

    val themes: List<ThemeConfig> = listOf(
        ThemeConfig("green", "绿意盎然", false, Color(77, 175, 124)),
        ThemeConfig("blue", "蓝田生玉", false, Color(52, 152, 219)),
        ThemeConfig("blue_plus", "蛋雅深蓝", false, Color(77, 131, 175)),
        ThemeConfig("orange", "橙黄橘绿", false, Color(245, 171, 53)),
        ThemeConfig("brown", "泥牛入海", false, Color(188, 128, 68)),
        ThemeConfig("red", "热情似火", false, Color(214, 69, 65)),
        ThemeConfig("pink", "粉装玉琢", false, Color(241, 130, 141)),
        ThemeConfig("purple", "重斤球紫", false, Color(155, 89, 182)),
        ThemeConfig("grey", "灰常美丽", false, Color(108, 122, 137)),
        ThemeConfig("ming", "青出于黑", false, Color(51, 110, 123)),
        ThemeConfig("blue2", "清热板蓝", false, Color(79, 98, 208)),
        ThemeConfig("black", "黑灯瞎火", true,
            Color(190, 190, 190), Color(255, 255, 255), "bg_landing_moon"),
        ThemeConfig("mid_autumn", "月里嫦娥", false,
            Color(74, 55, 82), null, "bg_jqbg"),
        ThemeConfig("naruto", "木叶之村", false,
            Color(87, 144, 167), null, "bg_myzcbg"),
        ThemeConfig("china_ink", "近墨者黑", false,
            Color(47, 47, 47), null, "bg_china_ink"),
        ThemeConfig("happy_new_year", "新年快乐", false,
            Color(192, 57, 43), null, "bg_xnkl"),
    )

    private val themeMap: Map<String, ThemeConfig> = themes.associateBy { it.id }

    fun getThemeById(id: String): ThemeConfig = themeMap[id] ?: themeMap["green"]!!

    /**
     * Generate a Material 3 ColorScheme from a ThemeConfig.
     * Maps the generated palette to M3 color roles with full token coverage.
     */
    fun buildColorScheme(config: ThemeConfig): ColorScheme {
        val palette = ColorGenerator.generatePalette(
            config.primary, 
            config.font, 
            config.isDark,
            config.surfaceColor,
            config.surfaceVariantColor,
            config.outlineColor
        )
        return if (config.isDark) buildDarkScheme(palette) else buildLightScheme(palette)
    }

    private fun buildLightScheme(p: ColorGenerator.ThemePalette): ColorScheme {
        val primary = p.primary
        val onPrimary = Color.White
        val primaryContainer = p.primaryLight[5] // light-600
        val onPrimaryContainer = p.primaryDark[3] // dark-400
        // Use generated palette colors instead of hardcoding
        val surface = p.surface ?: Color(0xFFFAFAFA)
        val onSurface = p.onSurface ?: p.fontColors[20]  // c-1000 (darkest font)
        val background = Color.White
        val surfaceVariant = p.surfaceVariant ?: Color(0xFFF5F5F5)
        val onSurfaceVariant = p.onSurfaceVariant ?: p.fontColors[12] // c-600

        return lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = ColorGenerator.linearShade(0.1f, primary),
            onSecondary = Color.White,
            secondaryContainer = p.primaryLight[7], // light-800
            onSecondaryContainer = p.primaryDark[2], // dark-300
            tertiary = ColorGenerator.linearShade(-0.15f, primary),
            onTertiary = Color.White,
            tertiaryContainer = p.primaryLight[6],
            onTertiaryContainer = p.primaryDark[4],
            error = Color(0xFFD32F2F),
            onError = Color.White,
            errorContainer = Color(0xFFFFCDD2),
            onErrorContainer = Color(0xFFB71C1C),
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            background = background,
            onBackground = onSurface,
            outline = p.outline ?: Color(0xFFBDBDBD),
            outlineVariant = p.outlineVariant ?: Color(0xFFE0E0E0),
            inverseSurface = p.inverseSurface ?: Color(0xFF424242),
            inverseOnSurface = p.inverseOnSurface ?: Color(0xFFFAFAFA),
            inversePrimary = p.primaryLight[3]
        )
    }

    private fun buildDarkScheme(p: ColorGenerator.ThemePalette): ColorScheme {
        val primary = p.theme // c-theme = primary-light-900 for dark
        val onPrimary = Color(0xFF1A1A1A)
        val primaryContainer = p.primaryDark[2] // dark-300
        val onPrimaryContainer = p.primaryLight[5]
        val surface = p.surfaceDark ?: Color(0xFF121212)
        val onSurface = p.onSurfaceDark ?: p.fontColors[0] // c-000 (lightest font for dark)
        val background = Color(0xFF121212)
        val surfaceVariant = p.surfaceVariantDark ?: Color(0xFF2D2D2D)
        val onSurfaceVariant = p.onSurfaceVariantDark ?: p.fontColors[8] // c-400

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = ColorGenerator.linearShade(-0.1f, primary),
            onSecondary = Color(0xFF1A1A1A),
            secondaryContainer = p.primaryDark[4],
            onSecondaryContainer = p.primaryLight[3],
            tertiary = ColorGenerator.linearShade(0.15f, primary),
            onTertiary = Color(0xFF1A1A1A),
            tertiaryContainer = p.primaryDark[5],
            onTertiaryContainer = p.primaryLight[4],
            error = Color(0xFFEF9A9A),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            background = background,
            onBackground = onSurface,
            outline = p.outlineDark ?: Color(0xFF757575),
            outlineVariant = p.outlineVariantDark ?: Color(0xFF424242),
            inverseSurface = p.inverseSurfaceDark ?: Color(0xFFEEEEEE),
            inverseOnSurface = p.inverseOnSurfaceDark ?: Color(0xFF121212),
            inversePrimary = p.primaryDark[1]
        )
    }
}
