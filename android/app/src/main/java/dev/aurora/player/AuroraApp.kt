package dev.aurora.player

import android.app.Application
import dev.aurora.player.app.AppContainer
import dev.aurora.player.app.DefaultAppContainer
import dev.aurora.player.data.player.MediaSessionConnection
import dev.aurora.player.data.player.youtube.BotGuardTokenGenerator

/**
 * AURORA application entry point.
 * Phase 1: minimal application class for the composition root.
 * Future phases will register dependency injection here.
 */
class AuroraApp : Application() {
    lateinit var container: AppContainer
        private set

    private lateinit var mediaSession: MediaSessionConnection

    override fun onCreate() {
        super.onCreate()
        // Initialize BotGuard PoToken generator for YouTube stream URL resolution.
        // Ported from NomaTune (GPL-3.0, github.com/Shahdullah/NomaTune).
        BotGuardTokenGenerator.initialize(this)
        container = DefaultAppContainer(this)
        // Restores the previous queue and begins recording listening history, resume
        // positions, and queue snapshots. All local and private; see docs/SECURITY.md.
        container.startPlaybackPersistence()

        /*
         * Opens the media session, which is what makes the service exist at all.
         *
         * Here rather than in the Activity: a controller tied to an Activity would take the
         * session down on every rotation, and the point of the session is to outlive the
         * screen.
         */
        mediaSession = MediaSessionConnection(this)
        mediaSession.connect()
    }
}
