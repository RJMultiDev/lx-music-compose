package cn.toside.music.mobile.userApi

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Kotlin wrapper around the QuickJS + JavaScriptThread Java classes.
 *
 * Provides a clean coroutine/Flow-compatible API for loading user scripts
 * and sending/receiving actions, replacing the old RN bridge layer
 * (UserApiModule, JsHandler, UtilsEvent).
 */
class QuickJsEngine(
    private val appContext: Context
) {
    companion object {
        private const val TAG = "QuickJsEngine"
    }

    private var javaScriptThread: JavaScriptThread? = null

    // Callback invoked when the script sends events back
    var onApiAction: ((action: String, data: String) -> Unit)? = null
    var onHttpRequest: ((requestKey: String, url: String, options: String) -> Unit)? = null
    var onCancelRequest: ((requestKey: String) -> Unit)? = null
    var onLog: ((type: String, message: String) -> Unit)? = null
    var onInitSuccess: ((initData: String?) -> Unit)? = null
    var onInitFailed: ((errorMessage: String) -> Unit)? = null
    var onShowUpdateAlert: ((log: String, updateUrl: String?) -> Unit)? = null

    private val mainHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: android.os.Message) {
            when (msg.what) {
                HandlerWhat.INIT_SUCCESS -> {
                    val initData = msg.obj as? String
                    onInitSuccess?.invoke(initData)
                }
                HandlerWhat.INIT_FAILED -> {
                    val errorMessage = msg.obj as? String ?: "Unknown error"
                    Log.e(TAG, "Init failed: $errorMessage")
                    sendLogEvent("error", errorMessage)
                    onInitFailed?.invoke(errorMessage)
                }
                HandlerWhat.ACTION -> {
                    val action = msg.obj as? Array<*> ?: return
                    if (action.size >= 2) {
                        val actionType = action[0] as? String ?: return
                        val data = action[1] as? String ?: return
                        Log.d(TAG, "Script action: $actionType data: ${data.take(200)}")
                        when (actionType) {
                            "request" -> {
                                // Script wants to make an HTTP request
                                try {
                                    val json = org.json.JSONObject(data)
                                    val requestKey = json.optString("requestKey", "")
                                    val url = json.optString("url", "")
                                    val options = json.optJSONObject("options")?.toString() ?: "{}"
                                    onHttpRequest?.invoke(requestKey, url, options)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to parse HTTP request: ${e.message}")
                                }
                            }
                            "cancelRequest" -> {
                                val requestKey = try {
                                    org.json.JSONObject(data).optString("requestKey", data)
                                } catch (_: Exception) { data }
                                onCancelRequest?.invoke(requestKey)
                            }
                            "response" -> {
                                // Script is responding to our musicUrl/lyric/pic query
                                onApiAction?.invoke(actionType, data)
                            }
                            "init" -> {
                                // Script initialization — pass init data
                                onInitSuccess?.invoke(data)
                                onApiAction?.invoke(actionType, data)
                            }
                            "showUpdateAlert" -> {
                                try {
                                    val json = org.json.JSONObject(data)
                                    val log = json.optString("log", "")
                                    val updateUrl = if (json.isNull("updateUrl")) null else json.optString("updateUrl", "")
                                    onShowUpdateAlert?.invoke(log, updateUrl)
                                } catch (_: Exception) {}
                                onApiAction?.invoke(actionType, data)
                            }
                            else -> {
                                onApiAction?.invoke(actionType, data)
                            }
                        }
                    }
                }
                HandlerWhat.LOG -> {
                    val logData = msg.obj as? Array<*> ?: return
                    if (logData.size >= 2) {
                        val type = logData[0] as? String ?: "log"
                        val message = logData[1] as? String ?: ""
                        sendLogEvent(type, message)
                    }
                }
            }
        }
    }

    private fun sendLogEvent(type: String, message: String) {
        Log.d(TAG, "[$type] $message")
        onLog?.invoke(type, message)
    }

    /**
     * Load a user API script into the QuickJS engine.
     *
     * @param scriptInfo Bundle containing: id, name, description, version, author, homepage, script
     * @return true if script loading started successfully
     */
    fun loadScript(scriptInfo: Bundle): Boolean {
        if (javaScriptThread != null) {
            destroy()
        }
        javaScriptThread = JavaScriptThread(appContext, scriptInfo)
        javaScriptThread?.prepareHandler(mainHandler)
        javaScriptThread?.getHandler()?.sendEmptyMessage(HandlerWhat.INIT)
        javaScriptThread?.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, ex ->
            val jsHandler = javaScriptThread?.getHandler() ?: return@UncaughtExceptionHandler
            val msg = jsHandler.obtainMessage()
            msg.what = HandlerWhat.LOG
            msg.obj = arrayOf("error", "Uncaught exception: ${ex.message}")
            jsHandler.sendMessage(msg)
            Log.e(TAG, "Uncaught exception: ${ex.message}")
        }
        return true
    }

    /**
     * Send an action to the running JS script.
     * @return true if the action was sent successfully
     */
    fun sendAction(action: String, info: String): Boolean {
        val thread = javaScriptThread ?: return false
        val jsHandler = thread.getHandler()
        val msg = jsHandler.obtainMessage()
        msg.what = HandlerWhat.ACTION
        msg.obj = arrayOf(action, info)
        jsHandler.sendMessage(msg)
        return true
    }

    /**
     * Destroy the QuickJS engine and stop the background thread.
     */
    fun destroy() {
        val thread = javaScriptThread ?: return
        thread.getHandler().sendEmptyMessage(HandlerWhat.DESTROY)
        thread.stopThread()
        javaScriptThread = null
    }

    val isRunning: Boolean get() = javaScriptThread != null
}
