package dev.aurora.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.player.PlaybackPosition
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora
import kotlin.math.roundToLong

// The thumb and track slots are still experimental in Material3 1.4, and they are the
// documented way to restyle a slider. The alternative is hand-written drag handling, which
// would mean re-implementing focus, keyboard stepping and the accessibility actions that
// Slider already gets right.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
                    /*
                     * A name and a state, not a description.
                     *
                     * Material3 1.4 keeps the slider's own semantics on an inner node that modifier
                     * semantics do not reach, so this names a sibling node rather than the
                     * seek bar itself - checked in the node tree, including with
                     * mergeDescendants, which does not change it. The seek bar is still
                     * focusable and adjustable, and the elapsed and total times are read
                     * from the labels beneath it, so the position is reachable; it is
                     * announced as a percentage rather than as a time.
                     */
                    contentDescription = "Playback position"
                    stateDescription = if (isUnknownDuration) {
                        "Duration unknown"
                    } else {
                        "${spokenTime(currentElapsed)} of ${spokenTime(duration)}"
                    }
                },
            colors = SliderDefaults.colors(
                thumbColor = Aurora.colors.textPrimary,
                activeTrackColor = Aurora.colors.textPrimary,
                inactiveTrackColor = Aurora.colors.surfaceGlassElevated
            ),
            /*
             * Drawn rather than taking the Material default.
             *
             * Material3 1.4's slider is the expressive one: a wide pill thumb, a gap cut
             * out of the track around it, and a stop indicator at the far end. Three
             * shapes where this design has one, and on a dark player it read as a stray
             * white tab floating beside a broken bar. The gesture handling, focus and
             * accessibility all still come from Slider - only the two drawing slots are
             * replaced.
             */
            thumb = {
                Box(
                    modifier = Modifier
                        .size(THUMB_SIZE)
                        .background(Aurora.colors.textPrimary, CircleShape)
                )
            },
            track = { state ->
                val fraction = with(state) {
                    val span = valueRange.endInclusive - valueRange.start
                    if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TRACK_HEIGHT)
                        .background(Aurora.colors.surfaceGlassElevated, CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(Aurora.colors.textPrimary, CircleShape)
                    )
                }
            }
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

/** Small enough to sit on the line rather than straddle it, large enough to drag. */
private val THUMB_SIZE = 14.dp
private val TRACK_HEIGHT = 4.dp

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
