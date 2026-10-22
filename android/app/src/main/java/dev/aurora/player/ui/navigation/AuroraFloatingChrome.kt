package dev.aurora.player.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.kyant.backdrop.Backdrop
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.rememberPressProgress
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.components.GlassSurface
import dev.aurora.player.ui.components.SelectionCapsule
import dev.aurora.player.ui.components.MiniPlayer
import dev.aurora.player.ui.components.MiniPlayerHeight
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
 * Above this text scale the pill shows icons only.
 *
 * 1.3 is where the longest label ("Settings") stops fitting its quarter of the pill at
 * the narrowest width AURORA supports. Past it the label is not shortened, it is clipped.
 */
private const val LABEL_MAX_FONT_SCALE = 1.3f

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
    /** Collected here rather than in the bar, so the bar stays a function of its inputs. */
    headphones: kotlinx.coroutines.flow.StateFlow<dev.aurora.player.domain.headphones.HeadphoneState>? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = Aurora.spacing
    val playerState by LocalPlayerCoordinator.current.state.collectAsState()

    GlassSurface(
        level = GlassLevel.Elevated,
        shape = RoundedCornerShape(percent = 50),
        backdrop = backdrop,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = spacing.space4, vertical = spacing.space3),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.space2)
        ) {

        val headphoneState by (headphones?.collectAsState()
            ?: remember {
                androidx.compose.runtime.mutableStateOf<dev.aurora.player.domain.headphones.HeadphoneState>(
                    dev.aurora.player.domain.headphones.HeadphoneState.Disconnected
                )
            })

        MiniPlayer(
            backdrop = backdrop,
            headphones = headphoneState,
            onExpand = onExpandPlayer,
            useGlass = false,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f).height(BarHeight)
            ) {
                /*
                 * The pill gains the lozenge the quality control already had.
                 *
                 * Four tabs with only a tinted icon to mark the current one meant the
                 * selection was carried by colour alone - the thing the design rules rule
                 * out. It now has a shape as well, and the same one, because two rows of
                 * mutually exclusive choices that mark themselves differently are two
                 * controls to learn instead of one.
                 */
                val destinations = AuroraDestination.pillDestinations
                val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }
                if (selectedIndex >= 0) {
                    SelectionCapsule(
                        index = selectedIndex,
                        count = destinations.size,
                        backdrop = backdrop,
                        modifier = Modifier.padding(horizontal = spacing.space2),
                        inset = 6.dp,
                    )
                }

                // Equal slots, so each item's centre is where SelectionCapsule puts the
                // lozenge. SpaceEvenly spaced content-sized items, and the two disagreed.
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = spacing.space2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEach { destination ->
                        NavPillItem(
                            destination = destination,
                            selected = currentRoute == destination.route,
                            onClick = { onNavigate(destination) },
                            modifier = Modifier.weight(1f)
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
                useGlass = false,
                onClick = { onNavigate(AuroraDestination.Search) }
            )
        }
    }
}
}

/** Big enough to read as a player rather than an icon, small enough to dock. */

@Composable
private fun NavPillItem(
    destination: AuroraDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current
    val interaction = remember { MutableInteractionSource() }
    val press by rememberPressProgress(interaction)
    // Read here: graphicsLayer's lambda is not a composable scope.
    val pressDepth = Aurora.motion
    val tint = if (selected) colors.accentPrimary else colors.textSecondary

    Column(
        modifier = modifier
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
                scaleX = 1f - pressDepth.pressScaleCompact * press
                scaleY = 1f - pressDepth.pressScaleCompact * press
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        /*
         * The label goes away before it gets cut in half.
         *
         * Four labels share the pill's width, so at a large text scale they do not fit:
         * measured at 1.5x, "Library" rendered as "Libraru" and "Settings" as "Se" - not
         * truncated with an ellipsis, simply clipped mid-letter, which reads as a
         * rendering fault rather than as a shortened word. An icon alone is honest about
         * being an icon.
         *
         * The accessible name moves with it. It was on the label precisely so it would not
         * be said twice; with no label, the icon carries it, so a screen reader still hears
         * "Library, tab" at every text size.
         */
        val labelFits = LocalDensity.current.fontScale <= LABEL_MAX_FONT_SCALE

        Icon(
            imageVector = destination.icon,
            contentDescription = if (labelFits) null else destination.label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        if (labelFits) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = destination.label,
                style = Aurora.typography.caption,
                color = tint,
                maxLines = 1
            )
        }
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
    /**
     * Whether to wrap this button in its own glass surface.
     *
     * Set to false when the button sits inside a parent glass surface (the floating bar),
     * so we don't nest glass on glass. The parent supplies the blur, refraction and
     * highlight; this button just draws its icon flat.
     */
    useGlass: Boolean = true,
) {
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current
    val interaction = remember { MutableInteractionSource() }
    val press by rememberPressProgress(interaction)
    // Read here: graphicsLayer's lambda is not a composable scope.
    val pressDepth = Aurora.motion

    val content: @Composable () -> Unit = {
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

    if (useGlass) {
        GlassSurface(
            // The surface answers the finger too, not just the transform: the refraction
            // deepens and the edge lifts under a press.
            pressProgress = { press },
            modifier = modifier
                .size(size)
                .graphicsLayer {
                    scaleX = 1f - pressDepth.pressScaleControl * press
                    scaleY = 1f - pressDepth.pressScaleControl * press
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
            content()
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .graphicsLayer {
                    scaleX = 1f - pressDepth.pressScaleControl * press
                    scaleY = 1f - pressDepth.pressScaleControl * press
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
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * The room the chrome occupies: the bars that are actually on screen, their gaps, and the
 * navigation-bar inset.
 *
 * The mini-player's height counts only while something is playing. It was always counted,
 * which left a bar's worth of dead space under every list before the first track of a
 * session - room reserved for a surface that was not there.
 */
@Composable
fun rememberChromeInsets(): PaddingValues {
    val spacing = Aurora.spacing
    val coordinator = LocalPlayerCoordinator.current

    /*
     * Whether there is a mini-player, and nothing else about it.
     *
     * This value is handed to every screen through a composition local, so reading the
     * whole player state here would rebuild the navigation host four times a second as
     * the playhead ticks - the cost of a position tick, paid by the entire tree. The flow
     * is narrowed to one boolean and de-duplicated before it is collected, and the result
     * is remembered, so the local's identity changes only when a track starts or stops.
     */
    val hasMiniPlayer by androidx.compose.runtime.produceState(false, coordinator) {
        coordinator.state
            .map { it.currentTrack != null }
            .distinctUntilChanged()
            .collect { value = it }
    }

    val bottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    return remember(hasMiniPlayer, bottomInset, spacing) {
        val miniPlayer = if (hasMiniPlayer) MiniPlayerHeight + spacing.space2 else 0.dp
        PaddingValues(bottom = miniPlayer + BarHeight + spacing.space3 * 2 + bottomInset)
    }
}
