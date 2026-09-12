package dev.aurora.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
                // HapticEngine rate-limits scrub ticks; the spec forbids a pulse per frame.
                haptics.fire(HapticEvent.Scrub)
            },
            onValueChangeFinished = {
                isDragging = false
                haptics.fire(HapticEvent.DragDrop)
                onSeek(dragPosition.roundToLong())
            },
            enabled = !isUnknownDuration && duration > 0,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    // Without this a screen reader reads the raw float ("0.42"). The spec
                    // asks for values, and a position is only meaningful as a time.
                    contentDescription = if (isUnknownDuration) {
                        "Playback position. Duration unknown."
                    } else {
                        "Playback position, ${spokenTime(currentElapsed)} of " +
                            spokenTime(duration)
                    }
                },
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
    // Locale.ROOT: this is a fixed numeric display, not localised prose, and the default
    // locale would render digits unexpectedly in some locales.
    return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds)
}

/** "2 minutes 5 seconds" reads correctly aloud; "02:05" does not. */
internal fun spokenTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val minutePart = when (minutes) {
        0L -> null
        1L -> "1 minute"
        else -> "$minutes minutes"
    }
    val secondPart = when (seconds) {
        1L -> "1 second"
        else -> "$seconds seconds"
    }
    return listOfNotNull(minutePart, secondPart).joinToString(" ")
}
