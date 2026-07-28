package cn.guoyujie666.music.compose.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Top-level routes (appear in bottom nav) */
@Serializable data object HomeRoute : NavKey

/** Detail / push routes */
@Serializable data object PlayDetailRoute : NavKey
@Serializable data class SonglistDetailRoute(val listId: String, val source: String = "") : NavKey
@Serializable data object CommentRoute : NavKey
@Serializable data class SettingsDetailRoute(val category: String) : NavKey

/** Top-level routes set for NavigationState */
val topLevelRoutes = setOf<NavKey>(HomeRoute)
