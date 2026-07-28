package cn.guoyujie666.music.compose.core.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import cn.guoyujie666.music.compose.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Media playback service using Media3 MediaLibraryService.
 *
 * Xiaomi Super Island / system media controls require:
 * 1. A valid MediaSession with sessionActivity (launch intent)
 * 2. All standard player commands exposed (SEEK_TO_NEXT, SEEK_TO_PREVIOUS, PLAY_PAUSE, etc.)
 * 3. Media3's DefaultMediaNotificationProvider handling the notification (not a manual one)
 * 4. Proper audio focus attributes set on the player
 * 5. MediaStyle notification on the correct channel
 */
@AndroidEntryPoint
class MusicPlaybackService : MediaLibraryService() {

    @Inject lateinit var exoPlayerManager: ExoPlayerManager
    @Inject lateinit var playerController: PlayerController

    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        configureNotificationProvider()

        // Set audio attributes for media playback (required for proper audio focus)
        exoPlayerManager.exoPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus= */ true
        )

        // ForwardingPlayer intercepts skip commands and routes to PlayerController.
        // Media3 1.10 dispatches via seekToNext/seekToPrevious AND
        // seekToNextMediaItem/seekToPreviousMediaItem — override all four.
        val wrappedPlayer = object : ForwardingPlayer(exoPlayerManager.exoPlayer) {
            override fun seekToNext() {
                playerController.playNext(isAuto = false)
            }
            override fun seekToPrevious() {
                playerController.playPrev(isAuto = false)
            }
            override fun seekToNextMediaItem() {
                playerController.playNext(isAuto = false)
            }
            override fun seekToPreviousMediaItem() {
                playerController.playPrev(isAuto = false)
            }
            override fun getMaxSeekToPreviousPosition(): Long = Long.MAX_VALUE

            // Report all commands as available so system UI shows all buttons
            override fun getAvailableCommands(): Player.Commands {
                return Player.Commands.Builder()
                    .addAllCommands()
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean = true
        }

        // Build sessionActivity — the PendingIntent launched when the user taps
        // the media notification or Xiaomi Super Island / system media widget.
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, wrappedPlayer, MyCallback())
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            // Do NOT release the ExoPlayer here — it's a shared singleton managed by ExoPlayerManager.
            // Only release the session binding.
            release()
        }
        mediaLibrarySession = null
        super.onDestroy()
    }

    /**
     * Configure the notification channel for media playback.
     * IMPORTANCE_LOW avoids sound/vibration but still shows in the shade.
     * Media3's DefaultMediaNotificationProvider uses this channel.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW)
                        .apply {
                            description = "音乐播放控制"
                            setShowBadge(false)
                        }
                )
            }
        }
    }

    /**
     * Configure Media3's DefaultMediaNotificationProvider to use our channel
     * and show proper media controls. This replaces the manual notification
     * and is what Xiaomi Super Island / system media controls read from.
     */
    private fun configureNotificationProvider() {
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        provider.setSmallIcon(R.mipmap.ic_launcher)
        setMediaNotificationProvider(provider)
    }

    /**
     * MediaLibrarySession.Callback — grants full player and session commands
     * to all controllers (system UI, Xiaomi Super Island, Bluetooth, etc.)
     */
    private inner class MyCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Grant all standard player commands so system UI can show
            // play/pause, next, previous, seek bar, etc.
            val playerCommands = Player.Commands.Builder()
                .addAllCommands()
                .build()

            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand("NEXT", Bundle.EMPTY))
                .add(SessionCommand("PREV", Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "NEXT" -> playerController.playNext(isAuto = false)
                "PREV" -> playerController.playPrev(isAuto = false)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        const val CHANNEL_ID = "lx_music_playback"
        const val NOTIFICATION_ID = 1
    }
}
