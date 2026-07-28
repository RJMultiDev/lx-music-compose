package cn.guoyujie666.music.compose.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import cn.guoyujie666.music.compose.core.setting.SettingsManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LX Music app-specific theme extras beyond Material 3.
 */
@Immutable
data class LxMusicColors(
    val isDark: Boolean = false,
    val backgroundImageRes: Int? = null,
    val themeId: String = "green",
    val primaryColor: Color = Color(77, 175, 124)
)

val LocalLxMusicColors = staticCompositionLocalOf { LxMusicColors() }

/**
 * ThemeManager — holds the active theme state as StateFlows.
 * Injected as a singleton; UI layer collects flows to trigger recomposition.
 */
@Singleton
class ThemeManager @Inject constructor(
    private val settingsManager: SettingsManager
) {
    private val _themeId = MutableStateFlow("green")
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    private val _colorScheme = MutableStateFlow(
        ThemeRegistry.buildColorScheme(ThemeRegistry.getThemeById("green"))
    )
    val colorScheme: StateFlow<ColorScheme> = _colorScheme.asStateFlow()

    private val _isDark = MutableStateFlow(false)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    private val _backgroundImage = MutableStateFlow<String?>(null)
    val backgroundImage: StateFlow<String?> = _backgroundImage.asStateFlow()

    private val _primaryColor = MutableStateFlow(Color(77, 175, 124))
    val primaryColor: StateFlow<Color> = _primaryColor.asStateFlow()

    private var isAutoTheme = false

    init {
        // Load persisted theme on creation
        val settings = runCatching { runBlocking { settingsManager.getSettings() } }.getOrNull()
        val savedId = settings?.themeId ?: "green"
        isAutoTheme = settings?.isAutoTheme ?: false
        applyTheme(savedId)
    }

    fun setTheme(id: String) {
        applyTheme(id)
        // Persist
        GlobalScope.launch(Dispatchers.IO) {
            settingsManager.updateSettings(settingsManager.getSettings().copy(themeId = id))
        }
    }

    fun setAutoTheme(enabled: Boolean, systemIsDark: Boolean) {
        isAutoTheme = enabled
        if (enabled && systemIsDark) {
            applyTheme("black")
        } else if (enabled && !systemIsDark) {
            // Revert to the saved light theme
            val lightId = runCatching {
                runBlocking { settingsManager.getSettings().themeLightId }
            }.getOrDefault("green")
            applyTheme(lightId)
        }
    }

    fun onSystemDarkModeChanged(systemIsDark: Boolean) {
        if (!isAutoTheme) return
        if (systemIsDark) applyTheme("black")
        else {
            val lightId = runCatching {
                runBlocking { settingsManager.getSettings().themeLightId }
            }.getOrDefault("green")
            applyTheme(lightId)
        }
    }

    /** For legacy compatibility */
    fun shouldUseDarkColors(): Boolean = _isDark.value
    fun setDarkMode(isDark: Boolean) {
        if (isDark) applyTheme("black") else applyTheme(_themeId.value)
    }
    fun getActiveTheme() = ThemeInfo(_themeId.value, "", _isDark.value)

    private fun applyTheme(id: String) {
        val config = ThemeRegistry.getThemeById(id)
        _themeId.value = config.id
        _isDark.value = config.isDark
        _colorScheme.value = ThemeRegistry.buildColorScheme(config)
        _backgroundImage.value = config.backgroundImage
        _primaryColor.value = config.primary
    }
}

data class ThemeInfo(val id: String, val name: String, val isDark: Boolean)

/**
 * Resolve background image name to drawable resource ID.
 */
fun resolveBackgroundImageRes(context: android.content.Context, name: String?): Int? {
    if (name.isNullOrEmpty()) return null
    return BG_IMAGE_MAP[name]
}

private val BG_IMAGE_MAP: Map<String, Int> by lazy {
    mapOf(
        "bg_landing_moon" to cn.guoyujie666.music.compose.R.drawable.bg_landing_moon,
        "bg_jqbg" to cn.guoyujie666.music.compose.R.drawable.bg_jqbg,
        "bg_myzcbg" to cn.guoyujie666.music.compose.R.drawable.bg_myzcbg,
        "bg_china_ink" to cn.guoyujie666.music.compose.R.drawable.bg_china_ink,
        "bg_xnkl" to cn.guoyujie666.music.compose.R.drawable.bg_xnkl,
    )
}

/**
 * A Box that draws the theme background image (if any) behind its content.
 * Each screen/page should wrap its content in this to show the background.
 */
@Composable
fun ThemeBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val lxColors = LocalLxMusicColors.current
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        if (lxColors.backgroundImageRes != null) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(lxColors.backgroundImageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        content()
    }
}

@Composable
fun LxMusicTheme(
    colorScheme: ColorScheme,
    isDark: Boolean,
    backgroundImageRes: Int? = null,
    themeId: String = "green",
    primaryColor: Color = Color(77, 175, 124),
    content: @Composable () -> Unit
) {
    // When a background image is active, make surface colors 75% opaque
    // so the image shows through all components (NavigationBar, Scaffold, MiniPlayer, etc.)
    val effectiveScheme = if (backgroundImageRes != null) {
        val alpha = 0.75f
        colorScheme.copy(
            background = colorScheme.background.copy(alpha = alpha),
            surface = colorScheme.surface.copy(alpha = alpha),
            surfaceVariant = colorScheme.surfaceVariant.copy(alpha = alpha),
            surfaceContainer = colorScheme.surfaceContainer.copy(alpha = alpha),
            surfaceContainerHigh = colorScheme.surfaceContainerHigh.copy(alpha = alpha),
            surfaceContainerHighest = colorScheme.surfaceContainerHighest.copy(alpha = alpha),
            surfaceContainerLow = colorScheme.surfaceContainerLow.copy(alpha = alpha),
            surfaceContainerLowest = colorScheme.surfaceContainerLowest.copy(alpha = alpha),
        )
    } else colorScheme

    val lxColors = LxMusicColors(
        isDark = isDark,
        backgroundImageRes = backgroundImageRes,
        themeId = themeId,
        primaryColor = primaryColor
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalLxMusicColors provides lxColors) {
        MaterialTheme(
            colorScheme = effectiveScheme,
            typography = LxMusicTypography,
            content = content
        )
    }
}
