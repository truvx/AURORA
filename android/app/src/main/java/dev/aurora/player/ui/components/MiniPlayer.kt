package dev.aurora.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
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
                    .clickable {
                        haptics.fire(HapticEvent.Tap)
                        onExpand()
                    }
                    .semantics(mergeDescendants = true) {
                        // Merged so the row announces as one control with the track name,
                        // rather than as loose fragments of text and buttons.
                        contentDescription = buildString {
                            append("Now playing: ")
                            append(currentTrack.title)
                            currentTrack.artist?.let { append(" by ").append(it) }
                            append(". Open full player.")
                        }
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
                            text = currentTrack.artist ?: "Unknown Artist",
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
