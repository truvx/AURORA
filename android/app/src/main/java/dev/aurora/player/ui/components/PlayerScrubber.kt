package dev.aurora.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.player.PlaybackPosition
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The position, drawn as a row of bars.
 *
 * The bars are all one height. They are a scrubber, not a waveform: AURORA never sees the
 * audio - YouTube's bytes are the provider's and local files are not analysed for shape -
 * so a bar pattern that rose and fell would be inventing a picture of a sound nobody
 * measured. The reference's varying heights are the one thing here that could not be
 * copied honestly. What is real is where playback has reached, and that is what the colour
 * and the taller playhead show.
 *
 * Drawn rather than built on Material's Slider, which keeps its semantics on an inner node
 * that modifier semantics cannot reach - a screen reader was left with an unnamed seek bar
 * announcing a percentage. This owns its node, so it can carry a name, a spoken time, a
 * range and a seek action.
 */
@Composable
fun PlayerScrubber(
    position: PlaybackPosition,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val duration = position.duration ?: 0L
    val isSeekable = position.duration != null && duration > 0
    val playedFraction = when {
        isDragging -> dragFraction
        isSeekable -> (position.elapsed.toFloat() / duration).coerceIn(0f, 1f)
        else -> 0f
    }
    val currentElapsed = if (isDragging) (dragFraction * duration).roundToLong() else position.elapsed

    fun seekTo(fraction: Float) {
        if (!isSeekable) return
        onSeek((fraction.coerceIn(0f, 1f) * duration).roundToLong())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                // The bars are 28dp, but the target is the full 48dp the spec asks for.
                .height(48.dp)
                .pointerInput(isSeekable) {
                    if (!isSeekable) return@pointerInput
                    detectTapGestures { offset ->
                        haptics.fire(HapticEvent.DragDrop)
                        seekTo(offset.x / size.width)
                    }
                }
                .pointerInput(isSeekable) {
                    if (!isSeekable) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            haptics.fire(HapticEvent.DragStart)
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            // HapticEngine rate-limits these; the spec forbids one per frame.
                            haptics.fire(HapticEvent.Scrub)
                        },
                        onDragEnd = {
                            isDragging = false
                            haptics.fire(HapticEvent.DragDrop)
                            seekTo(dragFraction)
                        },
                        onDragCancel = { isDragging = false }
                    )
                }
                .semantics {
                    contentDescription = "Playback position"
                    // A time, never the raw "42 percent" a bare progress bar would say.
                    stateDescription = if (isSeekable) {
                        "${spokenTime(currentElapsed)} of ${spokenTime(duration)}"
                    } else {
                        "Duration unknown"
                    }
                    if (isSeekable) {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = currentElapsed.toFloat(),
                            range = 0f..duration.toFloat()
                        )
                        // Seeking by screen reader, in the same units the label speaks.
                        setProgress { target ->
                            onSeek(target.coerceIn(0f, duration.toFloat()).roundToLong())
                            true
                        }
                    }
                }
        ) {
            val barWidth = BAR_WIDTH.toPx()
            val gap = BAR_GAP.toPx()
            val stride = barWidth + gap
            val count = max(1, ((size.width + gap) / stride).toInt())
            val barHeight = BAR_HEIGHT.toPx()
            val playheadHeight = PLAYHEAD_HEIGHT.toPx()
            val radius = CornerRadius(barWidth / 2f)
            val playedCount = (playedFraction * count).roundToInt()

            for (index in 0 until count) {
                val played = index < playedCount
                // One bar marks where playback is. It is taller because it is the
                // playhead, not because the audio is louder there.
                val isPlayhead = index == playedCount - 1 && playedCount > 0
                val height = if (isPlayhead) playheadHeight else barHeight
                drawRoundRect(
                    color = if (played) colors.playerProgress else colors.textPrimary.copy(alpha = 0.28f),
                    topLeft = Offset(index * stride, (size.height - height) / 2f),
                    size = Size(barWidth, height),
                    cornerRadius = radius
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = Aurora.spacing.space1)) {
            Text(
                text = formatTime(currentElapsed),
                style = Aurora.typography.label,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (isSeekable) formatTime(duration) else "--:--",
                style = Aurora.typography.label,
                color = colors.textSecondary
            )
        }
    }
}

private val BAR_WIDTH = 3.dp
private val BAR_GAP = 3.dp
private val BAR_HEIGHT = 18.dp
private val PLAYHEAD_HEIGHT = 28.dp

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
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
