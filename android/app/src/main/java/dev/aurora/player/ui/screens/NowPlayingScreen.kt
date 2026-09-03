package dev.aurora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.player.*
import dev.aurora.player.ui.components.*
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coordinator = LocalPlayerCoordinator.current
    val haptics = LocalHapticEngine.current
    val state by coordinator.state.collectAsState()
    
    val currentTrack = state.currentTrack

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Aurora.colors.backgroundPrimary)
            .padding(Aurora.spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Tap)
                    onClose()
                },
                icon = Icons.Rounded.KeyboardArrowDown,
                contentDesc = "Close",
            )
            Text(
                text = "Now Playing",
                style = Aurora.typography.title,
                color = Aurora.colors.textPrimary
            )
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Tap)
                    onOpenQueue()
                },
                icon = Icons.Rounded.QueueMusic,
                contentDesc = "Queue",
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Artwork
        ArtworkSurface(
            altText = "${currentTrack?.title ?: "No track"} artwork",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Aurora.spacing.space4)
        )

        Spacer(modifier = Modifier.height(Aurora.spacing.space10))

        // Title and Artist
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = currentTrack?.title ?: "No track playing",
                style = Aurora.typography.headline,
                color = Aurora.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentTrack?.artist ?: "",
                style = Aurora.typography.title,
                color = Aurora.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(Aurora.spacing.space6))

        // Scrubber
        PlayerScrubber(
            position = state.position,
            onSeek = { coordinator.dispatch(PlayerCommand.Seek(it)) }
        )

        Spacer(modifier = Modifier.height(Aurora.spacing.space6))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            val shuffleMode = state.queue.shuffleMode
            val isShuffleOn = shuffleMode == ShuffleMode.On
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Toggle)
                    coordinator.dispatch(PlayerCommand.SetShuffle(if (isShuffleOn) ShuffleMode.Off else ShuffleMode.On))
                },
                icon = Icons.Rounded.Shuffle,
                contentDesc = "Shuffle",
                selected = isShuffleOn,
            )

            // Previous
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Tap)
                    coordinator.dispatch(PlayerCommand.SkipPrevious)
                },
                icon = Icons.Rounded.SkipPrevious,
                contentDesc = "Previous",
                modifier = Modifier.size(64.dp)
            )

            // Play/Pause
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
                selected = isPlaying, // to give it a highlighted tint maybe
                modifier = Modifier.size(80.dp)
            )

            // Next
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Tap)
                    coordinator.dispatch(PlayerCommand.SkipNext)
                },
                icon = Icons.Rounded.SkipNext,
                contentDesc = "Next",
                modifier = Modifier.size(64.dp)
            )

            // Repeat
            val repeatMode = state.queue.repeatMode
            val repeatIcon = when (repeatMode) {
                RepeatMode.One -> Icons.Rounded.RepeatOne
                else -> Icons.Rounded.Repeat
            }
            val repeatSelected = repeatMode != RepeatMode.Off
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Toggle)
                    val nextMode = when (repeatMode) {
                        RepeatMode.Off -> RepeatMode.All
                        RepeatMode.All -> RepeatMode.One
                        RepeatMode.One -> RepeatMode.Off
                    }
                    coordinator.dispatch(PlayerCommand.SetRepeat(nextMode))
                },
                icon = repeatIcon,
                contentDesc = "Repeat",
                selected = repeatSelected,
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}
