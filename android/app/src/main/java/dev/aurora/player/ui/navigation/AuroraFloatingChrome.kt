package dev.aurora.player.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.components.YouTubePlayerSurface
import dev.aurora.player.ui.components.GlassSurface
import dev.aurora.player.ui.components.MiniPlayer
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

/**
 * How much room the floating chrome needs at the bottom of a scrolling screen.
 *
 * The chrome floats *over* content rather than reserving a strip of the window, so lists
 * add this to their own bottom padding: the last row then scrolls clear of the bar instead
 * of resting permanently underneath it.
 */
val LocalChromeInsets = staticCompositionLocalOf { PaddingValues(bottom = 0.dp) }

/** Height of the nav pill and of the circular buttons beside it. */
private val BarHeight = 60.dp

/**
 * The floating glass chrome: mini-player above, navigation pill and search button below.
 *
 * Built as free-floating pills over the content rather than as a bar attached to the
 * bottom edge, so page content passes underneath and refracts through the glass.
 *
 * @param backdrop what the chrome samples: the artwork atmosphere *and* the page content
 *   beneath it. The chrome is drawn outside that captured layer, which is what keeps it
 *   from sampling itself.
 */
@Composable
fun AuroraFloatingChrome(
    currentRoute: String?,
    onNavigate: (AuroraDestination) -> Unit,
    onExpandPlayer: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val spacing = Aurora.spacing
    val playerState by LocalPlayerCoordinator.current.state.collectAsState()
    val youTubePlaying = playerState.currentTrack?.provider == ProviderKind.YOUTUBE

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = spacing.space4, vertical = spacing.space3),
        verticalArrangement = Arrangement.spacedBy(spacing.space2)
    ) {
        if (youTubePlaying) {
            DockedYouTubePlayer(modifier = Modifier.align(Alignment.End))
        }

        MiniPlayer(backdrop = backdrop, onExpand = onExpandPlayer)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassSurface(
                modifier = Modifier.weight(1f).height(BarHeight),
                level = GlassLevel.Elevated,
                shape = RoundedCornerShape(percent = 50),
                backdrop = backdrop
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = spacing.space2),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AuroraDestination.pillDestinations.forEach { destination ->
                        NavPillItem(
                            destination = destination,
                            selected = currentRoute == destination.route,
                            onClick = { onNavigate(destination) }
                        )
                    }
                }
            }

            // Search sits apart, as its own circle: it is a different kind of action from
            // switching sections, and the references give it its own button for that reason.
            GlassCircleButton(
                icon = AuroraDestination.Search.icon,
                contentDescription = AuroraDestination.Search.label,
                selected = currentRoute == AuroraDestination.Search.route,
                backdrop = backdrop,
                onClick = { onNavigate(AuroraDestination.Search) }
            )
        }
    }
}

/**
 * The official YouTube player, docked while the full player is closed.
 *
 * docs/YOUTUBE_CAPABILITY_MATRIX.md requires the player visible with its own controls and
 * branding for as long as it is playing, and the mini-player's 44dp cover slot was never
 * that - it was a video the size of a thumbnail standing in for the artwork. So the two
 * jobs are separated: the pill names the record, this plays it, small but whole, and
 * nothing is drawn over it.
 *
 * It sits at the end of the row rather than centred, because the one thing it must not do
 * is take the middle of the screen away from what the listener is actually reading.
 */
@Composable
private fun DockedYouTubePlayer(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(DockedPlayerWidth)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
    ) {
        YouTubePlayerSurface(modifier = Modifier.fillMaxSize())
    }
}

/** Big enough to read as a player rather than an icon, small enough to dock. */
private val DockedPlayerWidth = 148.dp

@Composable
private fun NavPillItem(
    destination: AuroraDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        // Critically damped: this is a tap, not a throw, so it settles without overshoot.
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "nav press"
    )
    val tint = if (selected) colors.accentPrimary else colors.textSecondary

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .selectable(
                selected = selected,
                interactionSource = interaction,
                // No ripple: the press response is the scale below, which starts on
                // pointer-down rather than waiting for the release.
                indication = null,
                role = Role.Tab,
                onClick = {
                    if (!selected) haptics.fire(HapticEvent.Selection)
                    onClick()
                }
            )
            .padding(horizontal = Aurora.spacing.space3, vertical = Aurora.spacing.space2)
            .graphicsLayer {
                scaleX = 1f - 0.08f * press
                scaleY = 1f - 0.08f * press
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = destination.icon,
            // The label below is the accessible name; repeating it here says it twice.
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = destination.label,
            style = Aurora.typography.caption,
            color = tint,
            maxLines = 1
        )
    }
}

/** A circular glass button, as used for search beside the navigation pill. */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    backdrop: Backdrop? = null,
    size: androidx.compose.ui.unit.Dp = BarHeight,
) {
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "circle press"
    )

    GlassSurface(
        // The surface answers the finger too, not just the transform: the refraction
        // deepens and the edge lifts under a press.
        pressProgress = { press },
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = 1f - 0.06f * press
                scaleY = 1f - 0.06f * press
            }
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = {
                    if (!selected) haptics.fire(HapticEvent.Selection)
                    onClick()
                }
            ),
        level = GlassLevel.Elevated,
        shape = CircleShape,
        backdrop = backdrop
    ) {
        // fillMaxSize, not fillMaxWidth: GlassSurface lays its content out from the top
        // start, so a box only as tall as the icon left the lens pinned to the top of the
        // circle rather than centred in it.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) colors.accentPrimary else colors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** The room the chrome occupies: both bars, their gaps, and the navigation-bar inset. */
@Composable
fun rememberChromeInsets(): PaddingValues {
    val spacing = Aurora.spacing
    val bottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    return PaddingValues(
        bottom = 60.dp + BarHeight + spacing.space2 + spacing.space3 * 2 + bottomInset
    )
}
