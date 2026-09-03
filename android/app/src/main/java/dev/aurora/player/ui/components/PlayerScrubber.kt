package dev.aurora.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.aurora.player.domain.player.PlaybackPosition
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora
import kotlin.math.roundToLong

@Composable
fun PlayerScrubber(
    position: PlaybackPosition,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticEngine.current
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val currentElapsed = if (isDragging) dragPosition.toLong() else position.elapsed
    val duration = position.duration ?: 0L
    val isUnknownDuration = position.duration == null

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = if (duration > 0) currentElapsed.toFloat() else 0f,
            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
            onValueChange = { value ->
                if (!isDragging) {
                    isDragging = true
                    haptics.fire(HapticEvent.DragStart)
                }
                dragPosition = value
                haptics.fire(HapticEvent.Scrub)
            },
            onValueChangeFinished = {
                isDragging = false
                haptics.fire(HapticEvent.DragDrop)
                onSeek(dragPosition.roundToLong())
            },
            enabled = !isUnknownDuration && duration > 0,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Aurora.colors.textPrimary,
                activeTrackColor = Aurora.colors.textPrimary,
                inactiveTrackColor = Aurora.colors.surfaceGlassElevated
            )
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(currentElapsed),
                style = Aurora.typography.label,
                color = Aurora.colors.textSecondary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (isUnknownDuration) "--:--" else formatTime(duration),
                style = Aurora.typography.label,
                color = Aurora.colors.textSecondary
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
