package cn.guoyujie666.music.compose.ui.modals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.guoyujie666.music.compose.core.model.UserApiInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cn.guoyujie666.music.compose.core.userapi.UserApiManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class UserApiViewModel @Inject constructor(
    private val apiManager: UserApiManager
) : ViewModel() {

    val apiList: StateFlow<List<UserApiInfo>> = apiManager.apiList
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _activeId = MutableStateFlow<String?>(apiManager.getActiveId())
    val activeId: StateFlow<String?> = _activeId
    private val _initResults = MutableStateFlow<Map<String, String>>(emptyMap())
    val initResults: StateFlow<Map<String, String>> = _initResults

    init {
        // Watch real init status from UserApiManager — only track the active source
        viewModelScope.launch {
            apiManager.status.collect { status ->
                status.apiInfo?.let { api ->
                    // Only show status for the currently selected source
                    if (api.id == _activeId.value) {
                        if (status.status) {
                            _initResults.value = mapOf(api.id to "success")
                        } else {
                            _initResults.value = mapOf(api.id to "failed")
                        }
                    }
                }
            }
        }

        // Auto-init the saved active source on startup
        _activeId.value?.let { id ->
            val api = apiList.value.find { it.id == id }
            if (api != null) {
                viewModelScope.launch { apiManager.loadApi(api) }
            }
        }
    }

    fun isEnabled(id: String) = _activeId.value == id
    fun initStatus(id: String) = _initResults.value[id]

    fun selectSource(id: String) {
        if (_activeId.value == id) return  // Already active — skip
        _activeId.value = id
        _initResults.value = emptyMap()  // Clear all old statuses, only show current
        viewModelScope.launch { apiManager.switchToSource(id) }
    }

    fun removeApi(id: String) {
        if (_activeId.value == id) _activeId.value = null
        _initResults.value = _initResults.value - id
        apiManager.removeApi(id)
    }

    fun importScript(script: String) {
        val meta = parseHeader(script)
        apiManager.importApi(script, meta)
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            try {
                val script = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000; conn.readTimeout = 15000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    if (conn.contentLength > 9_000_000) throw Exception("File too large")
                    conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
                }
                if (script.isNotBlank()) importScript(script)
            } catch (_: Exception) {}
        }
    }

    /** Parse the /* ... */ header comment from a user API script. */
    private fun parseHeader(script: String): UserApiInfo {
        val headerRegex = Regex("""/\*[*\s]*?([^*]+|\*+[^/])*?\*/""")
        val match = headerRegex.find(script.trimStart())
        val meta = mutableMapOf<String, String>()
        if (match != null) {
            Regex("""\*\s*@(\w+)\s+(.+)""").findAll(match.value).forEach { m ->
                meta[m.groupValues[1]] = m.groupValues[2].trim()
            }
        }
        val id = "user_api_${(100..999).random()}_${System.currentTimeMillis()}"
        return UserApiInfo(
            id = id,
            name = meta["name"]?.take(24) ?: "Untitled",
            description = meta["description"]?.take(36) ?: "",
            version = meta["version"] ?: "1.0.0",
            author = meta["author"] ?: "",
            homepage = meta["homepage"] ?: "",
            allowShowUpdateAlert = true
        )
    }
}
