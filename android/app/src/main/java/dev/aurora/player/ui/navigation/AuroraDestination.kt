package dev.aurora.player.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Typed navigation destinations from docs/NAVIGATION_ARCHITECTURE.md.
 *
 * Primary: Home, Search, Library, AI, Settings.
 * Uses Material Symbols Outlined as per docs/ICONOGRAPHY_FINAL.md.
 */
sealed class AuroraDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Home : AuroraDestination("home", "Home", Icons.Outlined.Home)
    data object Search : AuroraDestination("search", "Search", Icons.Outlined.Search)
    data object Library : AuroraDestination("library", "Library", Icons.Outlined.LibraryMusic)
    data object Ai : AuroraDestination("ai", "AI", Icons.Outlined.AutoAwesome)
    data object Settings : AuroraDestination("settings", "Settings", Icons.Outlined.Settings)

    companion object {
        val primaryDestinations = listOf(Home, Search, Library, Ai, Settings)
    }
}
