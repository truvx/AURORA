package dev.aurora.player.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import dev.aurora.player.app.PlayerCoordinator

/**
 * CompositionLocal to provide the PlayerCoordinator down the Compose tree.
 */
val LocalPlayerCoordinator = staticCompositionLocalOf<PlayerCoordinator> {
    error("No PlayerCoordinator provided")
}

val LocalYouTubePlayerAdapter = staticCompositionLocalOf<dev.aurora.player.data.player.YouTubePlayerAdapter> {
    error("No YouTubePlayerAdapter provided")
}
