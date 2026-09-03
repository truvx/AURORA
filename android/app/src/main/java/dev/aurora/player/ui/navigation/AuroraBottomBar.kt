package dev.aurora.player.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.aurora.player.ui.theme.Aurora

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

    NavigationBar(
        modifier = modifier,
        containerColor = colors.surfaceGlassElevated,
        contentColor = colors.textPrimary,
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
                onClick = { onNavigate(destination) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accentPrimary,
                    selectedTextColor = colors.accentPrimary,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                    indicatorColor = colors.accentPrimary.copy(alpha = 0.12f),
                ),
            )
        }
    }
}
