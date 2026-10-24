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
 * Primary: Listen, Search, Library, AI, Settings.
 * Uses Material Symbols Outlined as per docs/ICONOGRAPHY_FINAL.md.
 *
 * The default destination is named for what is on it - resume and recommendations - rather
 * than as a generic "Home". The screen's own headline already read "Listen Now" while the
 * tab under it said something else, and a label that names its contents is predictable
 * before you open it in a way an umbrella term is not.
 */
sealed class AuroraDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    /** Route id is unchanged: it is persisted in saved navigation state. */
    data object Home : AuroraDestination("home", "Listen", Icons.Outlined.Home)
    data object Search : AuroraDestination("search", "Search", Icons.Outlined.Search)
    data object Library : AuroraDestination("library", "Library", Icons.Outlined.LibraryMusic)
    data object Ai : AuroraDestination("ai", "AI", Icons.Outlined.AutoAwesome)
    data object Settings : AuroraDestination("settings", "Settings", Icons.Outlined.Settings)

    companion object {
        /*
         * Computed on access, not stored.
         *
         * A `val` here is initialised with the companion, and the companion is initialised
         * by this class's static init - which is triggered by the first touch of any of its
         * objects. Reaching AuroraDestination.Home first (it is the start destination) then
         * builds these lists while Home.INSTANCE is still null, and the app dies on launch
         * with an NPE reading `route` off a null entry. A JVM test that touches the list
         * first initialises in the opposite order and sees nothing wrong.
         */
        val primaryDestinations: List<AuroraDestination>
            get() = listOf(Home, Search, Library, Ai, Settings)

        /**
         * What the navigation pill holds. Search is deliberately absent: it lives in its own
         * circular button beside the pill, because finding something is a different kind of
         * action from moving between sections.
         */
        val pillDestinations: List<AuroraDestination>
            get() = listOf(Home, Library, Ai, Settings)
    }
}
