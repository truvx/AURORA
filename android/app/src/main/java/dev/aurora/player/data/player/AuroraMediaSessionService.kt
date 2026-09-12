package dev.aurora.player.data.player

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
        
        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
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
