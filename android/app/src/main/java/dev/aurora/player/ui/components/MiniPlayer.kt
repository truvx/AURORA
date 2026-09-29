package dev.aurora.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import dev.aurora.player.ui.motion.Projection
import dev.aurora.player.ui.theme.AuroraMotionTokens
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.player.PlaybackStatus
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

/**
 * How tall the floating bar is.
 *
 * Shared, because the chrome reserves exactly this much room at the bottom of every
 * scrolling screen: two numbers that have to agree, kept as one.
 */
val MiniPlayerHeight = 64.dp

/**
 * The bar is a rounded rectangle rather than a pill.
 *
 * A pill's ends curve away from the square sleeve sitting inside it, leaving two crescents
 * of empty glass; a radius near the sleeve's own lets the two shapes nest, which is what
 * the reference does and why its bar reads as a container for the record rather than as a
 * capsule with a picture stuck on.
 */
private val BarShape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    backdrop: com.kyant.backdrop.Backdrop? = null,
    /**
     * What is on the listener's head, if anything.
     *
     * Defaulted so a preview or a test that does not care about headphones renders the
     * bar without them, which is also what an emulator with no output device shows.
     */
    headphones: dev.aurora.player.domain.headphones.HeadphoneState =
        dev.aurora.player.domain.headphones.HeadphoneState.Disconnected,
    onExpand: () -> Unit
) {
    val coordinator = LocalPlayerCoordinator.current
    val haptics = LocalHapticEngine.current
    val state by coordinator.state.collectAsState()
    
    val currentTrack = state.currentTrack

    // Reduced motion removes the travel, not the transition: the player still appears and
    // disappears, it just fades instead of sliding up from the bottom edge.
    val reducedMotion =
        dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences.current.reducedMotion

    // Failures were previously invisible: status went to Error and the UI kept showing a
    // player that simply never advanced.
    /*
     * The same identity the full player shows.
     *
     * The lookup is cached per track, so this costs nothing beyond the first resolution -
     * and without it the row read "keshi - Soft Spot (Official Music Video) by KeshiVEVO"
     * while the player above it said "Soft Spot" by "keshi", which is the app disagreeing
     * with itself about what is playing.
     */
    val resolved by dev.aurora.player.ui.artwork.rememberAlbumArtwork(currentTrack)

    val hasError = state.status == PlaybackStatus.Error
    val errorText = state.lastError?.message?.takeIf { it.isNotBlank() }
        ?: "This track could not be played"

    /*
     * Drag-to-expand.
     *
     * docs/AURORA_MOTION_SPEC.md asks for the mini-player to be "drag-linked" and to
     * "settle to nearest state" on release. The surface follows the finger one-to-one, and
     * what decides whether it opens is the velocity at release rather than how far it
     * happened to get - so a short, quick flick opens the player, and a long slow drag that
     * stops dead falls back.
     */
    val expandTravel = with(LocalDensity.current) { 120.dp.toPx() }
    val lift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // The raw pointer offset, kept apart from the drawn one: the drawn value is rubber-
    // banded at the bounds, and using that to judge intent would under-report how far the
    // user actually pulled.
    var pointerOffset by remember { mutableFloatStateOf(0f) }

    val settle = spring<Float>(
        dampingRatio = AuroraMotionTokens.springSettleDamping,
        stiffness = AuroraMotionTokens.springSettleStiffness
    )

    AnimatedVisibility(
        visible = currentTrack != null,
        enter = if (reducedMotion) {
            androidx.compose.animation.fadeIn()
        } else {
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        },
        exit = if (reducedMotion) {
            androidx.compose.animation.fadeOut()
        } else {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            )
        },
        modifier = modifier
    ) {
        if (currentTrack != null) {
            GlassSurface(
                shape = BarShape,
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerHeight)
                    .offset { IntOffset(0, lift.value.roundToInt()) }
                    .graphicsLayer {
                        // Hint the destination: the surface grows slightly as it rises, so
                        // the frames in between point at the full player rather than just
                        // interpolating toward it.
                        val progress = (abs(lift.value) / expandTravel).coerceIn(0f, 1f)
                        scaleX = 1f + progress * 0.02f
                        scaleY = 1f + progress * 0.02f
                    }
                    /*
                     * `draggable` rather than a raw drag detector, because this surface is
                     * also clickable: the two share one pointer stream, and the platform's
                     * own touch-slop disambiguation is what decides between them. A raw
                     * detector competes with the click instead, and the drag simply never
                     * wins. It also reports the release velocity directly, which is the one
                     * number the settle decision below actually turns on.
                     */
                    .draggable(
                        state = rememberDraggableState { delta ->
                            pointerOffset += delta
                            scope.launch {
                                // Resistance, not a hard stop: there is nothing below the
                                // player and nothing above the open position, and a dead
                                // stop at either end reads as the app having frozen.
                                lift.snapTo(
                                    Projection.clampWithRubberband(
                                        pointerOffset, -expandTravel, 0f, expandTravel
                                    )
                                )
                            }
                        },
                        orientation = Orientation.Vertical,
                        onDragStarted = { pointerOffset = lift.value },
                        onDragStopped = { velocity ->
                            val target = Projection.projectedSnapTarget(
                                pointerOffset, velocity, listOf(0f, -expandTravel)
                            )
                            pointerOffset = 0f
                            if (target == -expandTravel) {
                                haptics.fire(HapticEvent.Tap)
                                onExpand()
                                lift.snapTo(0f)
                            } else {
                                // Continues at the speed the finger left at, so there is no
                                // seam between the drag and the animation after it.
                                lift.animateTo(0f, settle, velocity)
                            }
                        }
                    )
                    /*
                     * One node, with its description and its action declared together.
                     *
                     * A separate `semantics { contentDescription = ... }` beside
                     * `clickable` produced two nodes in the tree: a clickable one that
                     * announced nothing, and the carefully written "Now playing... Open
                     * full player." on a node TalkBack could neither focus nor activate.
                     * Reordering did not help, and clearing the descendants took the play
                     * and next buttons out of the tree with them. Merging leaves those two
                     * as their own stops and folds the title and artist into this row, so
                     * it is one stop rather than three. The phrasing that has to reach the
                     * node carrying the action - "Open full player" - is the click label
                     * on `clickable`, which does land there.
                     *
                     * Verified in the node tree with TalkBack running.
                     */
                    .clickable(onClickLabel = "Open full player") {
                        haptics.fire(HapticEvent.Tap)
                        onExpand()
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = buildString {
                            if (hasError) {
                                append("Playback error: ")
                                append(errorText)
                                append(". ")
                            }
                            append("Now playing: ")
                            append(resolved.title ?: currentTrack.title)
                            resolved.artist?.let { append(" by ").append(it) }
                            /*
                             * The capsule's own words, said here.
                             *
                             * This row is one merged node with an explicit description, so
                             * anything written on a child inside it is never spoken. The
                             * headphone state is worth hearing, so it joins the sentence
                             * that does reach the screen reader rather than being set on a
                             * node that cannot be focused.
                             */
                            headphoneAnnouncement(headphones)?.let { append(". ").append(it) }
                        }
                        role = Role.Button
                        onClick(label = "Open full player") {
                            haptics.fire(HapticEvent.Tap)
                            onExpand()
                            true
                        }
                        // Assertive so a failure interrupts rather than waiting for the
                        // user to navigate back to this row.
                        if (hasError) liveRegion = LiveRegionMode.Assertive
                    },
                level = GlassLevel.Elevated
            ) {
                /*
                 * How far through the track, as a fill behind the row.
                 *
                 * The bar named what was playing but never said where in it playback had
                 * got to, which is the one thing a glance at a mini-player is usually for.
                 * Drawn only when the duration is actually known - a live stream and a
                 * YouTube track still resolving its metadata have no end to be a fraction
                 * of, and a bar that fills against an invented length would be a lie about
                 * the track rather than a missing feature.
                 *
                 * Decorative: the full player's scrubber is the accessible control, and
                 * this row is one merged node that announces what is playing.
                 */
                val duration = state.position.duration?.takeIf { it > 0 }
                if (duration != null) {
                    val progress = (state.position.elapsed.toFloat() / duration).coerceIn(0f, 1f)
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(Aurora.colors.surfaceGlassSecondary)
                                .clearAndSetSemantics {}
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Aurora.spacing.space2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /*
                     * The sleeve, whatever the source.
                     *
                     * This square used to be the YouTube player itself for a YouTube
                     * track, which put a 44dp video where the cover belongs: too small to
                     * be the "user-recognizable YouTube experience" the capability matrix
                     * asks for, and not the picture of the record the row is naming. The
                     * player is docked beside this pill at a size worth calling a player.
                     */
                    ArtworkSurface(
                        // Decorative here: the row announces the title right beside it,
                        // and a cover that says "<title> artwork" first is the same words
                        // twice.
                        altText = "",
                        artworkUri = resolved.artworkUri,
                        modifier = Modifier.size(48.dp),
                    )

                    Spacer(modifier = Modifier.width(Aurora.spacing.space3))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = resolved.title ?: currentTrack.title,
                            style = Aurora.typography.trackTitle,
                            color = Aurora.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            // Carries an icon and words, never colour alone, so the failure
                            // is perceivable without colour vision.
                            text = if (hasError) "⚠  $errorText" else resolved.artist
                                ?: "Unknown Artist",
                            style = Aurora.typography.trackArtistCompact,
                            color = Aurora.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HeadphoneCapsule(
                        state = headphones,
                        // Space on both sides: without the leading gap a long title runs
                        // straight into the capsule and the two read as one shape.
                        modifier = Modifier.padding(horizontal = Aurora.spacing.space2)
                    )

                    val isPlaying = state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering
                    GlassIconButton(
                        onClick = {
                            haptics.fire(HapticEvent.Toggle)
                            if (isPlaying) {
                                coordinator.dispatch(PlayerCommand.Pause)
                            } else {
                                coordinator.dispatch(PlayerCommand.Play)
                            }
                        },
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDesc = if (isPlaying) "Pause" else "Play",
                    )

                    GlassIconButton(
                        onClick = {
                            haptics.fire(HapticEvent.Tap)
                            coordinator.dispatch(PlayerCommand.SkipNext)
                        },
                        icon = Icons.Rounded.SkipNext,
                        contentDesc = "Next track",
                    )
                }
            }
        }
    }
}
