package cn.guoyujie666.music.compose

import androidx.lifecycle.ViewModel
import cn.guoyujie666.music.compose.core.model.UserApiUpdateAlert
import cn.guoyujie666.music.compose.core.userapi.UserApiManager
import cn.guoyujie666.music.compose.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class MainUiState(
    val isAppInitialized: Boolean = false,
    val fontSize: Float = 14f
)

@HiltViewModel
class MainViewModel @Inject constructor(
    val themeManager: ThemeManager,
    private val userApiManager: UserApiManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    val userApiAlert: StateFlow<UserApiUpdateAlert?> = userApiManager.updateAlert

    init {
        userApiManager.autoLoadActive()
    }

    fun setAppInitialized() {
        _uiState.value = _uiState.value.copy(isAppInitialized = true)
    }

    fun dismissUpdateAlert() {
        userApiManager.dismissUpdateAlert()
    }
}
