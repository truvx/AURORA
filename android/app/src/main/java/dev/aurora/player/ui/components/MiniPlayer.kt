package dev.aurora.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
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

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Aurora.spacing.space3, vertical = Aurora.spacing.space1)
                    .height(64.dp)
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
                    .clickable {
                        haptics.fire(HapticEvent.Tap)
                        onExpand()
                    }
                    .semantics(mergeDescendants = true) {
                        // Merged so the row announces as one control with the track name,
                        // rather than as loose fragments of text and buttons.
                        contentDescription = buildString {
                            if (hasError) {
                                append("Playback error: ")
                                append(errorText)
                                append(". ")
                            }
                            append("Now playing: ")
                            append(currentTrack.title)
                            currentTrack.artist?.let { append(" by ").append(it) }
                            append(". Open full player.")
                        }
                        // Assertive so a failure interrupts rather than waiting for the
                        // user to navigate back to this row.
                        if (hasError) liveRegion = LiveRegionMode.Assertive
                    },
                level = GlassLevel.Elevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Aurora.spacing.space4),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkSurface(
                        altText = "${currentTrack.title} artwork",
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (currentTrack.provider == dev.aurora.player.domain.models.ProviderKind.YOUTUBE) {
                            YouTubePlayerSurface(modifier = Modifier.fillMaxSize())
                        }
                    }

                    Spacer(modifier = Modifier.width(Aurora.spacing.space4))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentTrack.title,
                            style = Aurora.typography.title,
                            color = Aurora.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            // Carries an icon and words, never colour alone, so the failure
                            // is perceivable without colour vision.
                            text = if (hasError) "⚠  $errorText" else currentTrack.artist
                                ?: "Unknown Artist",
                            style = Aurora.typography.body,
                            color = Aurora.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
                }
            }
        }
    }
}
