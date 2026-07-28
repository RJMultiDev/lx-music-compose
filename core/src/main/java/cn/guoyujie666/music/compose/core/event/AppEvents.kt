package cn.guoyujie666.music.compose.core.event

import cn.guoyujie666.music.compose.core.model.MusicInfo
import cn.guoyujie666.music.compose.core.model.PlaybackProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central event bus for LX Music.
 *
 * Replaces the original custom Event system (src/event/Event.ts).
 * Uses Kotlin SharedFlow for one-shot events and StateFlow for observable state.
 *
 * Design decision: Single event hub rather than per-module singletons
 * (unlike the original global.state_event, global.app_event, global.list_event).
 * This simplifies DI and testing.
 */

// === One-shot Event Types (SharedFlow) ===

sealed interface AppEvent {
    // Player events
    data class PlayerPlaying(val trackId: String) : AppEvent
    data object PlayerPaused : AppEvent
    data object PlayerEnded : AppEvent
    data class PlayerError(val error: String) : AppEvent

    // List events
    data class MyListUpdated(val lists: List<Any>) : AppEvent  // TODO: typed
    data class MyListToggled(val id: String) : AppEvent
    data class MusicToggled(val musicInfo: MusicInfo) : AppEvent
    data object PicUpdated : AppEvent
    data object LyricUpdated : AppEvent

    // API events
    data class ApiSourceUpdated(val sourceId: String) : AppEvent
    data class ApiAction(val action: String, val data: String) : AppEvent
    data class ApiLog(val type: String, val message: String) : AppEvent

    // Sync events
    data class SyncStatusUpdated(val status: Boolean, val message: String) : AppEvent

    // Version events
    data class VersionUpdateAvailable(val version: String, val desc: String) : AppEvent

    // Common events
    data class FontSizeUpdated(val size: Float) : AppEvent
    data class ThemeChanged(val isDark: Boolean) : AppEvent
    data class LanguageChanged(val locale: String) : AppEvent
    data class NavActiveIdChanged(val navId: String) : AppEvent
    data class BgPicUpdated(val pic: String?) : AppEvent
}

// === Observable State Types (StateFlow) ===

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentTrackId: String? = null,
    val progress: PlaybackProgress = PlaybackProgress(),
    val volume: Float = 1f,
    val playbackRate: Float = 1f,
    val toggleMode: String = "listLoop"
)

data class SyncState(
    val connected: Boolean = false,
    val message: String = ""
)

@Singleton
class EventBus @Inject constructor() {
    // One-shot events
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    // Observable state flows
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun emit(event: AppEvent) {
        _events.emit(event)
        // Also update state flows for relevant events
        when (event) {
            is AppEvent.PlayerPlaying -> {
                _playerState.value = _playerState.value.copy(
                    isPlaying = true,
                    currentTrackId = event.trackId
                )
            }
            is AppEvent.PlayerPaused -> {
                _playerState.value = _playerState.value.copy(isPlaying = false)
            }
            is AppEvent.PlayerEnded -> {
                _playerState.value = _playerState.value.copy(isPlaying = false)
            }
            is AppEvent.SyncStatusUpdated -> {
                _syncState.value = SyncState(event.status, event.message)
            }
            else -> {}
        }
    }
}
