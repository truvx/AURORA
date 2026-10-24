package dev.aurora.player.data.player

import android.app.Notification
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.aurora.player.app.PlayerCoordinator
import dev.aurora.player.domain.player.PlayerCommand

// The service hosts the MediaSession, connected to the application-scoped player engine.
class AuroraMediaSessionService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Android 8.0+ requires a notification channel before any notification can be posted,
        // and a foreground service must show one within five seconds or the system kills it.
        MediaNotification.ensureChannel(this)

        val app = application as dev.aurora.player.AuroraApp
        val adapter = app.container.playerAdapter as? Media3PlayerOwner
            ?: error("media session requires a Media3-backed adapter")
        val coordinator = app.container.playerCoordinator

        val forwardingPlayer = object : ForwardingPlayer(adapter.sessionPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                coordinator.dispatch(PlayerCommand.SkipNext)
            }

            override fun seekToPrevious() {
                coordinator.dispatch(PlayerCommand.SkipPrevious)
            }

            override fun seekToNextMediaItem() {
                coordinator.dispatch(PlayerCommand.SkipNext)
            }

            override fun seekToPreviousMediaItem() {
                coordinator.dispatch(PlayerCommand.SkipPrevious)
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            // Tapping the notification comes back to AURORA rather than launching a
            // second copy of it.
            .setSessionActivity(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, dev.aurora.player.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        // Start the foreground service with the media notification so the system keeps
        // playback alive while the app is not on screen. The notification id and channel
        // match MediaNotification, so track changes replace rather than stack it.
        val notification: Notification = MediaNotification.build(this)
        startForeground(MediaNotification.NOTIFICATION_ID, notification)
    }

    /*
     * Swiping the app away.
     *
     * If something is still playing the service stays: that is the point of background
     * playback, and killing it here would make the notification a lie. If nothing is
     * playing there is nothing to keep alive, and leaving a foreground service running
     * over a paused player is how a music app becomes the thing in the battery screen.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            // DO NOT call player.release() here because the player is application-scoped.
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
