package cn.guoyujie666.music.compose.core.model

import kotlinx.serialization.Serializable

/**
 * Theme configuration models.
 * Ported from src/types/theme.d.ts.
 *
 * Material 3 Express migration note:
 * The original app used ~200 CSS-like color tokens (c-000 through c-1000, c-primary-* variants, etc.).
 * In M3 Express, we map these to Material 3 color roles (primary, secondary, surface, etc.)
 * while providing backward-compatible theme definitions.
 */

// === M3 Color Scheme ===

@Serializable
data class ThemeColors(
    val primary: String = "#4CAF50",
    val onPrimary: String = "#FFFFFF",
    val primaryContainer: String = "#C8E6C9",
    val onPrimaryContainer: String = "#1B5E20",
    val secondary: String = "#607D8B",
    val onSecondary: String = "#FFFFFF",
    val secondaryContainer: String = "#CFD8DC",
    val onSecondaryContainer: String = "#263238",
    val tertiary: String = "#FF9800",
    val onTertiary: String = "#FFFFFF",
    val tertiaryContainer: String = "#FFE0B2",
    val onTertiaryContainer: String = "#E65100",
    val error: String = "#F44336",
    val onError: String = "#FFFFFF",
    val errorContainer: String = "#FFCDD2",
    val onErrorContainer: String = "#B71C1C",
    val surface: String = "#FAFAFA",
    val onSurface: String = "#212121",
    val surfaceVariant: String = "#F5F5F5",
    val onSurfaceVariant: String = "#757575",
    val background: String = "#FFFFFF",
    val onBackground: String = "#212121",
    val outline: String = "#BDBDBD",
    val outlineVariant: String = "#E0E0E0",
    val inverseSurface: String = "#303030",
    val inverseOnSurface: String = "#F5F5F5",
    val inversePrimary: String = "#A5D6A7",
    val appBackground: String = "#FFFFFF",
    val contentBackground: String = "#F5F5F5"
)

// === Active Theme (used at runtime) ===

data class ActiveTheme(
    val id: String,
    val name: String,
    val isDark: Boolean = false,
    val colors: ThemeColors = ThemeColors()
) {
    val surfaceColor: String get() = colors.surface
    val backgroundColor: String get() = colors.background
    val contentBackgroundColor: String get() = colors.contentBackground
    val primaryColor: String get() = colors.primary
}

// === Full Theme Definition (for loading/storage) ===

@Serializable
data class Theme(
    val id: String,
    val name: String,
    val isDark: Boolean = false,
    val isCustom: Boolean = false,
    val colors: ThemeColors = ThemeColors()
)

// === Theme Info (registry) ===

data class ThemeInfo(
    val themes: List<Theme> = emptyList(),
    val userThemes: List<Theme> = emptyList(),
    val dataPath: String = ""
)

// === Theme Setting (persisted) ===

data class ThemeSetting(
    val shouldUseDarkColors: Boolean = false,
    val themeId: String = "green",
    val themeName: String = "Green",
    val isDark: Boolean = false
)
