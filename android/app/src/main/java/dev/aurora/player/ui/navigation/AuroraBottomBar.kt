package dev.aurora.player.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aurora.player.ui.theme.Aurora
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine

/**
 * AURORA bottom navigation bar.
 *
 * Compact-width layout uses bottom navigation with the 5 primary
 * destinations. Wider layouts may use a navigation rail in the future.
 * Each item has a minimum 48dp touch target and semantic label.
 */
@Composable
fun AuroraBottomBar(
    currentRoute: String?,
    onNavigate: (AuroraDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current

    dev.aurora.player.ui.components.GlassSurface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp),
        level = dev.aurora.player.ui.components.GlassLevel.Elevated,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
    ) {
        NavigationBar(
            modifier = Modifier.padding(horizontal = 8.dp), // Some inner padding for the items
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = colors.textPrimary,
            tonalElevation = 0.dp // Remove default elevation shadow from Nav bar itself
        ) {
            AuroraDestination.primaryDestinations.forEach { destination ->
                val selected = currentRoute == destination.route

                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            style = Aurora.typography.caption,
                        )
                    },
                    selected = selected,
                    onClick = {
                        if (!selected) haptics.fire(HapticEvent.Selection)
                        onNavigate(destination)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accentPrimary,
                        selectedTextColor = colors.accentPrimary,
                        unselectedIconColor = colors.textSecondary,
                        unselectedTextColor = colors.textSecondary,
                        indicatorColor = colors.glassHighlight.copy(alpha = 0.2f),
                    ),
                )
            }
        }
    }
}
