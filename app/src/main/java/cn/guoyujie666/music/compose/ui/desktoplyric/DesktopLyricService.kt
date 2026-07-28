package cn.guoyujie666.music.compose.ui.desktoplyric

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import cn.guoyujie666.music.compose.ui.theme.LxMusicTheme

/**
 * Service managing a floating desktop lyric overlay window.
 *
 * Ported from LyricModule + LyricView.java.
 * The native LyricPlayer.java engine handles LRC timing (reused as-is).
 * This Compose implementation replaces the original View-based rendering.
 *
 * Window type: TYPE_APPLICATION_OVERLAY (Android 8+) / TYPE_SYSTEM_ALERT (older)
 * Permission required: SYSTEM_ALERT_WINDOW
 */
class DesktopLyricService : Service(), LifecycleOwner {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Compose state
    var lyrics by mutableStateOf("")
    var translation by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var isLocked by mutableStateOf(false)
    var fontSize by mutableFloatStateOf(24f)
    var opacity by mutableFloatStateOf(1f)
    var unplayedColor by mutableStateOf("#B0BEC5")   // grey
    var playedColor by mutableStateOf("#4CAF50")      // green
    var shadowColor by mutableStateOf("#000000")
    var showAnimation by mutableStateOf(true)
    var isSingleLine by mutableStateOf(false)
    var maxLines by mutableIntStateOf(5)
    var windowWidth by mutableIntStateOf(300)
    var textAlignX by mutableStateOf("center")
    var textAlignY by mutableStateOf("center")

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    // Initial position
    private var windowX = 0
    private var windowY = 100

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("DesktopLyric", "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_UPDATE_LYRIC -> {
                lyrics = intent.getStringExtra(EXTRA_LYRIC) ?: ""
                translation = intent.getStringExtra(EXTRA_TRANSLATION)
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
            }
            ACTION_UPDATE_STYLE -> {
                fontSize = intent.getFloatExtra(EXTRA_FONT_SIZE, fontSize)
                opacity = intent.getFloatExtra(EXTRA_OPACITY, opacity)
                unplayedColor = intent.getStringExtra(EXTRA_UNPLAYED_COLOR) ?: unplayedColor
                playedColor = intent.getStringExtra(EXTRA_PLAYED_COLOR) ?: playedColor
                isLocked = intent.getBooleanExtra(EXTRA_IS_LOCKED, isLocked)
                isSingleLine = intent.getBooleanExtra(EXTRA_IS_SINGLE_LINE, isSingleLine)
                maxLines = intent.getIntExtra(EXTRA_MAX_LINES, maxLines)
                windowWidth = intent.getIntExtra(EXTRA_WIDTH, windowWidth)
                textAlignX = intent.getStringExtra(EXTRA_TEXT_ALIGN_X) ?: textAlignX
                textAlignY = intent.getStringExtra(EXTRA_TEXT_ALIGN_Y) ?: textAlignY
                updateWindowParams()
            }
            ACTION_SET_POSITION -> {
                windowX = intent.getIntExtra(EXTRA_POS_X, windowX)
                windowY = intent.getIntExtra(EXTRA_POS_Y, windowY)
                updateWindowPosition()
            }
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return

        try {
        // Foreground notification (required for TYPE_APPLICATION_OVERLAY service on Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            val channel = android.app.NotificationChannel("lx_desktop_lyric", "Desktop Lyric", android.app.NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
            val notification = androidx.core.app.NotificationCompat.Builder(this, "lx_desktop_lyric")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("LX Music Desktop Lyric")
                .setOngoing(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(2, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(2, notification)
            }
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DesktopLyricService)
            setContent {
                LxMusicTheme(
                    colorScheme = androidx.compose.material3.lightColorScheme(),
                    isDark = false
                ) {
                    DesktopLyricContent(
                        lyrics = lyrics,
                        translation = translation,
                        isPlaying = isPlaying,
                        fontSize = fontSize,
                        opacity = opacity,
                        unplayedColor = unplayedColor,
                        playedColor = playedColor,
                        shadowColor = shadowColor,
                        showAnimation = showAnimation,
                        isSingleLine = isSingleLine,
                        maxLines = maxLines,
                        textAlignX = textAlignX,
                        textAlignY = textAlignY,
                        modifier = Modifier
                    )
                }
            }
        }

        layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = PixelFormat.TRANSLUCENT
            width = (windowWidth * resources.displayMetrics.density).toInt()
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = windowX
            y = windowY
        }

        overlayView = composeView
        windowManager.addView(composeView, layoutParams)

        // Touch handling for drag
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        composeView.setOnTouchListener { _, event ->
            if (isLocked) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams!!.x
                    initialY = layoutParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(composeView, layoutParams)
                    true
                }
                else -> false
            }
        }

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        } catch (e: Exception) {
            android.util.Log.e("DesktopLyric", "showOverlay failed", e)
            overlayView = null
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
        }
        overlayView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    private fun updateWindowParams() {
        val view = overlayView ?: return
        layoutParams?.let { params ->
            params.width = windowWidth
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun updateWindowPosition() {
        val view = overlayView ?: return
        layoutParams?.let { params ->
            params.x = windowX
            params.y = windowY
            windowManager.updateViewLayout(view, params)
        }
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "cn.guoyujie666.music.compose.DESKTOP_LYRIC_SHOW"
        const val ACTION_HIDE = "cn.guoyujie666.music.compose.DESKTOP_LYRIC_HIDE"
        const val ACTION_UPDATE_LYRIC = "cn.guoyujie666.music.compose.DESKTOP_LYRIC_UPDATE"
        const val ACTION_UPDATE_STYLE = "cn.guoyujie666.music.compose.DESKTOP_LYRIC_STYLE"
        const val ACTION_SET_POSITION = "cn.guoyujie666.music.compose.DESKTOP_LYRIC_POSITION"

        const val EXTRA_LYRIC = "lyric"
        const val EXTRA_TRANSLATION = "translation"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_FONT_SIZE = "font_size"
        const val EXTRA_OPACITY = "opacity"
        const val EXTRA_UNPLAYED_COLOR = "unplayed_color"
        const val EXTRA_PLAYED_COLOR = "played_color"
        const val EXTRA_SHADOW_COLOR = "shadow_color"
        const val EXTRA_IS_LOCKED = "is_locked"
        const val EXTRA_IS_SINGLE_LINE = "is_single_line"
        const val EXTRA_MAX_LINES = "max_lines"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_TEXT_ALIGN_X = "text_align_x"
        const val EXTRA_TEXT_ALIGN_Y = "text_align_y"
        const val EXTRA_POS_X = "pos_x"
        const val EXTRA_POS_Y = "pos_y"
    }
}

