package cn.guoyujie666.music.compose.core.userapi

import android.content.Context
import android.os.Bundle
import cn.guoyujie666.music.compose.core.event.AppEvent
import cn.guoyujie666.music.compose.core.event.EventBus
import cn.guoyujie666.music.compose.core.model.KnownSources
import cn.guoyujie666.music.compose.core.model.Quality
import cn.guoyujie666.music.compose.core.model.UserApiInfo
import cn.guoyujie666.music.compose.core.model.UserApiSourceInfo
import cn.guoyujie666.music.compose.core.model.UserApiStatus
import cn.guoyujie666.music.compose.core.model.UserApiUpdateAlert
import cn.guoyujie666.music.compose.core.music.SourceRegistry
import cn.toside.music.mobile.userApi.QuickJsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user-defined API scripts loaded via QuickJS.
 *
 * Ported from src/core/userApi.ts, src/core/init/userApi/index.ts.
 *
 * Responsibilities:
 * - Load/unload user API scripts into QuickJS engine
 * - Track script metadata (name, author, version, sources)
 * - Forward HTTP requests from scripts (lx.request()) via Ktor HttpClient
 * - Bridge QuickJS events to the app's event bus
 * - Register user sources into the SourceRegistry
 */
@Singleton
class UserApiManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: EventBus,
    private val sourceRegistry: SourceRegistry,
    private val httpClient: HttpClient
) {
    private val _apiList = MutableStateFlow<List<UserApiInfo>>(emptyList())
    val apiList: StateFlow<List<UserApiInfo>> = _apiList.asStateFlow()

    private val _status = MutableStateFlow(UserApiStatus(status = false))
    val status: StateFlow<UserApiStatus> = _status.asStateFlow()

    // Update alert state — UI collects this to show a dialog
    private val _updateAlert = MutableStateFlow<UserApiUpdateAlert?>(null)
    val updateAlert: StateFlow<UserApiUpdateAlert?> = _updateAlert.asStateFlow()

    fun dismissUpdateAlert() { _updateAlert.value = null }

    // Persistence
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val prefs = context.getSharedPreferences("lx_user_api", Context.MODE_PRIVATE)
    private val scriptsStore = context.getSharedPreferences("lx_user_api_scripts", Context.MODE_PRIVATE)

    // Active engine instances, keyed by apiId
    private val activeEngines = mutableMapOf<String, QuickJsEngine>()

    // Map of apiId → userApiSourceId (e.g., "user_api_1" → SourceRegistry id)
    private val registeredSourceIds = mutableMapOf<String, String>()

    // Coroutine scope for HTTP requests from scripts
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Track pending HTTP requests from scripts (requestKey → Job for cancellation)
    private val pendingHttpRequests = mutableMapOf<String, kotlinx.coroutines.Job>()

    // Track script load time to handle init window for UnhandledPromiseRejectionException
    private val loadTimeByApi = mutableMapOf<String, Long>()

    init {
        // Load saved APIs on startup
        try {
            val saved = prefs.getString("api_list_json", null)
            if (saved != null) {
                val list = json.parseToJsonElement(saved).jsonArray.map {
                    val o = it.jsonObject
                    UserApiInfo(
                        id = o["id"]?.jsonPrimitive?.content ?: "",
                        name = o["name"]?.jsonPrimitive?.content ?: "",
                        description = o["description"]?.jsonPrimitive?.content ?: "",
                        version = o["version"]?.jsonPrimitive?.content ?: "1.0.0",
                        author = o["author"]?.jsonPrimitive?.content ?: "",
                        homepage = o["homepage"]?.jsonPrimitive?.content ?: "",
                        allowShowUpdateAlert = o["allowShowUpdateAlert"]?.jsonPrimitive?.boolean ?: true
                    )
                }
                _apiList.value = list
            }
        } catch (_: Exception) {}
    }

    private fun saveApiList() {
        val arr = buildString {
            append("[")
            _apiList.value.forEachIndexed { i, api ->
                if (i > 0) append(",")
                append("""{"id":"${api.id}","name":"${api.name}","description":"${api.description}","version":"${api.version}","author":"${api.author}","homepage":"${api.homepage}","allowShowUpdateAlert":${api.allowShowUpdateAlert}}""")
            }
            append("]")
        }
        prefs.edit().putString("api_list_json", arr).apply()
    }

    /**
     * Import a user API from script content.
     */
    fun importApi(
        script: String,
        metadata: UserApiInfo
    ): Result<UserApiInfo> {
        if (metadata.name.isBlank()) return Result.failure(IllegalArgumentException("Name required"))
        if (metadata.id.isBlank()) return Result.failure(IllegalArgumentException("ID required"))

        if (_apiList.value.any { it.id == metadata.id }) {
            return Result.failure(IllegalStateException("API with this ID already exists"))
        }

        val apiInfo = metadata.copy()
        _apiList.value = _apiList.value + apiInfo
        saveApiList()
        scriptsStore.edit().putString(apiInfo.id, script).apply()

        return Result.success(apiInfo)
    }

    /**
     * Load a user API script into the QuickJS engine.
     */
    fun loadApi(apiInfo: UserApiInfo) {
        // Re-register if already loaded
        activeEngines.remove(apiInfo.id)?.destroy()
        val sourceId = "user_api_${apiInfo.id}"
        val engine = QuickJsEngine(context)

        // === HTTP request forwarding from script ===
        // When the script calls lx.request(url, options, callback),
        // we make a real HTTP request via Ktor and send the response back.
        engine.onHttpRequest = { requestKey: String, url: String, optionsStr: String ->
            scope.launch {
                val options = JSONObject(optionsStr)
                val method = options.optString("method", "get").lowercase()
                try {
                    val headers = options.optJSONObject("headers")
                    val body = if (options.isNull("body")) null else options.optString("body", "")
                    val form = options.optJSONObject("form")
                    val formData = options.optJSONObject("formData")
                    val timeout = options.optLong("timeout", 30000)
                    val binary = options.optBoolean("binary", false)

                    android.util.Log.d("UserApi", "HTTP $method $url")

                    val response = when (method) {
                        "get" -> {
                            httpClient.prepareGet(url) {
                                headers?.let { h ->
                                    h.keys().forEach { key -> header(key, h.getString(key)) }
                                }
                            }.execute()
                        }
                        "post" -> {
                            httpClient.preparePost(url) {
                                headers?.let { h ->
                                    h.keys().forEach { key -> header(key, h.getString(key)) }
                                }
                                when {
                                    body != null -> {
                                        contentType(ContentType.Application.Json)
                                        setBody(body)
                                    }
                                    form != null -> {
                                        val params = form.keys().asSequence().map { k ->
                                            k to form.getString(k)
                                        }.toList()
                                        setBody(FormDataContent(io.ktor.http.Parameters.build {
                                            params.forEach { (k, v) -> append(k, v) }
                                        }))
                                    }
                                    formData != null -> {
                                        setBody(formData.toString())
                                    }
                                }
                            }.execute()
                        }
                        else -> throw UnsupportedOperationException("HTTP method $method not supported")
                    }

                    val statusCode = response.status.value
                    val statusMessage = response.status.description
                    val responseHeaders = JSONObject().apply {
                        response.headers.forEach { name, values ->
                            put(name, values.joinToString(", "))
                        }
                    }
                    val responseBody = response.bodyAsText()

                    // Parse body as JSON if possible so the script can access
                    // structured fields (e.g. body.code) instead of treating
                    // everything as a raw string
                    val bodyValue: Any = try {
                        org.json.JSONObject(responseBody)
                    } catch (_: Exception) {
                        try {
                            org.json.JSONArray(responseBody)
                        } catch (_: Exception) {
                            responseBody
                        }
                    }

                    val result = JSONObject().apply {
                        put("statusCode", statusCode)
                        put("statusMessage", statusMessage)
                        put("headers", responseHeaders)
                        put("body", bodyValue)
                    }

                    // Log response for debugging
                    android.util.Log.d("UserApi", "HTTP $method $url → status=$statusCode bodyLen=${responseBody.length} body=${responseBody.take(200)}")

                    // Send response back to script
                    engine.sendAction("response",
                        JSONObject().apply {
                            put("requestKey", requestKey)
                            put("response", result)
                        }.toString()
                    )
                } catch (e: Exception) {
                    android.util.Log.e("UserApi", "HTTP $method $url FAILED: ${e.message}")
                    // Send error back to script
                    engine.sendAction("response",
                        JSONObject().apply {
                            put("requestKey", requestKey)
                            put("error", e.message ?: "HTTP request failed")
                        }.toString()
                    )
                }
            }
        }

        // === Cancel HTTP request ===
        engine.onCancelRequest = { requestKey: String ->
            pendingHttpRequests.remove(requestKey)?.cancel()
        }

        // === API response from script (musicUrl/lyric/pic results) ===
        engine.onApiAction = { action: String, data: String ->
            if (action == "response") {
                // Script is responding to our musicUrl/lyric/pic query
                try {
                    val bridge = sourceRegistry.getSource(sourceId) as? UserApiSourceBridge
                    if (bridge != null) {
                        val reqKey = try {
                            JSONObject(data).optString("requestKey", "")
                        } catch (_: Exception) { "" }
                        bridge.onScriptResponse(reqKey, data)
                    }
                } catch (_: Exception) {}
            }
            // "init" is handled in onInitSuccess
        }

        engine.onLog = { type: String, message: String ->
            scope.launch {
                eventBus.emit(AppEvent.ApiLog(type, message))
            }
        }

        // === Init success: script loaded and called lx.send('inited', ...) ===
        var initDataReceived = false
        var fallbackJob: kotlinx.coroutines.Job? = null

        engine.onInitSuccess = { initData: String? ->
            if (initData != null && !initDataReceived) {
                initDataReceived = true
                android.util.Log.d("UserApi", "onInitSuccess with data: ${initData.take(300)}")
                try {
                    val parsed = JSONObject(initData)
                    val info = parsed.optJSONObject("info")
                    val status = parsed.optBoolean("status", false)

                    if (status && info != null) {
                        val sourcesObj = info.optJSONObject("sources")
                        if (sourcesObj != null) {
                            // Parse sources from script's init data
                            val sourcesMap = mutableMapOf<String, UserApiSourceInfo>()
                            val sourceNames = listOf("kw", "kg", "tx", "wy", "mg", "local")
                            for (srcName in sourceNames) {
                                val srcObj = sourcesObj.optJSONObject(srcName) ?: continue
                                if (srcObj.optString("type", "") != "music") continue
                                val actions = srcObj.optJSONArray("actions")?.let { arr ->
                                    (0 until arr.length()).map { arr.getString(it) }
                                } ?: emptyList()
                                val qualitys = srcObj.optJSONArray("qualitys")?.let { arr ->
                                    (0 until arr.length()).map { arr.getString(it) }
                                } ?: emptyList()
                                sourcesMap[srcName] = UserApiSourceInfo(
                                    name = apiInfo.name,
                                    type = "music",
                                    actions = actions,
                                    qualitys = qualitys
                                )
                            }

                            // Update apiInfo with parsed sources
                            if (sourcesMap.isNotEmpty()) {
                                val updatedApiInfo = apiInfo.copy(sources = sourcesMap)
                                registerAsSource(sourceId, updatedApiInfo)
                                _status.value = UserApiStatus(status = true, apiInfo = updatedApiInfo)
                                android.util.Log.d("UserApi", "Registered sources: ${sourcesMap.keys}")
                                // Skip fallback registration
                                initDataReceived = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UserApi", "Failed to parse init data: ${e.message}")
                }
            }

        }

        engine.onInitFailed = { error ->
            fallbackJob?.cancel()
            fallbackJob = null
            // Unregister source during the init window (first 10s after loadScript)
            // This handles the race where onInitSuccess registers the source
            // before callJS throws an UnhandledPromiseRejectionException
            val loadTime = loadTimeByApi[apiInfo.id] ?: 0L
            val inInitWindow = System.currentTimeMillis() - loadTime < 10000
            if (!initDataReceived || inInitWindow) {
                initDataReceived = true
                if (inInitWindow) {
                    sourceRegistry.unregister(sourceId)
                    registeredSourceIds.remove(apiInfo.id)
                    android.util.Log.d("UserApi", "Init failed during init window, unregistered $sourceId")
                }
            }
            _status.value = UserApiStatus(
                status = false,
                message = error,
                apiInfo = apiInfo
            )
            _updateAlert.value = UserApiUpdateAlert(
                name = apiInfo.name,
                log = "初始化失败: $error",
                updateUrl = null,
                isError = true
            )
        }

        engine.onShowUpdateAlert = { log, updateUrl ->
            android.util.Log.d("UserApi", "Update alert from ${apiInfo.name}: $log, url=$updateUrl")
            _updateAlert.value = UserApiUpdateAlert(
                name = apiInfo.name,
                log = log,
                updateUrl = updateUrl
            )
        }

        // Build script info bundle and load
        val bundle = Bundle().apply {
            putString("id", apiInfo.id)
            putString("name", apiInfo.name)
            putString("description", apiInfo.description)
            putString("version", apiInfo.version)
            putString("author", apiInfo.author)
            putString("homepage", apiInfo.homepage)
            putString("script", scriptsStore.getString(apiInfo.id, "") ?: "")
        }

        if (engine.loadScript(bundle)) {
            loadTimeByApi[apiInfo.id] = System.currentTimeMillis()
            android.util.Log.d("UserApi", "loadApi: loadScript OK, waiting for init...")
            activeEngines[apiInfo.id] = engine
            registeredSourceIds[apiInfo.id] = sourceId
        } else {
            android.util.Log.e("UserApi", "loadApi: loadScript FAILED for ${apiInfo.name}")
        }
    }

    /**
     * Send an action to a loaded script (for musicUrl/lyric/pic queries).
     */
    fun sendAction(apiId: String, action: String, data: String): Boolean {
        return activeEngines[apiId]?.sendAction(action, data) ?: false
    }

    /**
     * Unload and destroy a script engine.
     */
    fun unloadApi(apiId: String) {
        val engine = activeEngines.remove(apiId) ?: return
        val sourceId = registeredSourceIds.remove(apiId)

        // Unregister from SourceRegistry
        sourceId?.let { sourceRegistry.unregister(it) }

        engine.destroy()
        _status.value = UserApiStatus(status = false)
    }

    /**
     * Remove a user API completely.
     */
    fun removeApi(apiId: String) {
        unloadApi(apiId)
        _apiList.value = _apiList.value.filter { it.id != apiId }
        saveApiList()
        scriptsStore.edit().remove(apiId).apply()
        if (getActiveId() == apiId) prefs.edit().remove("active_id").apply()
    }

    /**
     * Switch to a specific source — destroys all other engines, loads only the selected one.
     */
    fun switchToSource(id: String) {
        setActiveId(id)
        // Destroy all other engines
        activeEngines.keys.toList().forEach { engineId ->
            if (engineId != id) unloadApi(engineId)
        }
        // Load the selected source if not already loaded
        if (!activeEngines.containsKey(id)) {
            val api = _apiList.value.find { it.id == id }
            if (api != null) loadApi(api)
        }
    }

    /**
     * Auto-load the saved active source on startup.
     */
    fun autoLoadActive() {
        val activeId = getActiveId() ?: return
        val api = _apiList.value.find { it.id == activeId } ?: return
        android.util.Log.d("UserApi", "autoLoadActive: loading $activeId (${api.name})")
        loadApi(api)
    }

    /**
     * Get all loaded user API source IDs.
     */
    fun getActiveSourceIds(): List<String> = registeredSourceIds.values.toList()
    fun getActiveId(): String? = prefs.getString("active_id", null)
    fun setActiveId(id: String) = prefs.edit().putString("active_id", id).apply()

    /**
     * Check if a source ID is a user API.
     */
    fun isUserApiSource(sourceId: String): Boolean =
        sourceId.startsWith("user_api_")

    /**
     * Clean up all engines (call on app exit).
     */
    fun destroyAll() {
        activeEngines.keys.toList().forEach { unloadApi(it) }
        activeEngines.clear()
        registeredSourceIds.clear()
        pendingHttpRequests.clear()
        scope.cancel()
    }

    /**
     * Register a loaded script as a music source in SourceRegistry.
     */
    private fun registerAsSource(sourceId: String, apiInfo: UserApiInfo) {
        // Use script-declared source info, or fallback to defaults
        val sourceInfo = apiInfo.sources?.values?.firstOrNull()
            ?: UserApiSourceInfo(
                name = apiInfo.name,
                actions = listOf("musicUrl", "lyric", "pic"),
                qualitys = listOf("128k", "320k", "flac", "flac24bit")
            )

        val source = UserApiSourceBridge(
            sourceId = sourceId,
            sourceName = apiInfo.name,
            sourceInfo = sourceInfo,
            apiInfo = apiInfo,
            apiId = apiInfo.id,
            sendRawAction = { action, data -> sendAction(apiInfo.id, action, data) }
        )

        android.util.Log.d("UserApi", "registerAsSource: registering $sourceId (${apiInfo.name}) with actions=${sourceInfo.actions}")
        sourceRegistry.register(source)
        android.util.Log.d("UserApi", "registerAsSource: total sources now = ${sourceRegistry.count}")
    }
}
