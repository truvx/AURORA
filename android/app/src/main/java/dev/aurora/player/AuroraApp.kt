package dev.aurora.player

import android.app.Application
import dev.aurora.player.app.AppContainer
import dev.aurora.player.app.DefaultAppContainer

/**
 * AURORA application entry point.
 * Phase 1: minimal application class for the composition root.
 * Future phases will register dependency injection here.
 */
class AuroraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
