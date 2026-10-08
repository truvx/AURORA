package dev.aurora.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import kotlin.math.roundToLong

/**
 * The position, as NomaTune's Immersive player draws it: a flat white track over the
 * artwork's colour, the times underneath, and room between them for the codec chip.
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
    modifier: Modifier = Modifier,
    /** Drawn between the two times - the codec chip, where there is one. */
    centerContent: (@Composable () -> Unit)? = null,
) {
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
                // The track is 9dp, but the target is the full 48dp the spec asks for.
                .height(48.dp)
                .pointerInput(isSeekable) {
                    if (!isSeekable) return@pointerInput
                    detectTapGestures { offset ->
                        haptics.fire(HapticEvent.Commit)
                        seekTo(offset.x / size.width)
                    }
                }
                .pointerInput(isSeekable) {
                    if (!isSeekable) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            haptics.fire(HapticEvent.Lift)
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            // HapticEngine rate-limits these; the spec forbids one per frame.
                            haptics.fire(HapticEvent.Tick)
                        },
                        onDragEnd = {
                            isDragging = false
                            haptics.fire(HapticEvent.Commit)
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
            val track = TRACK_HEIGHT.toPx()
            val top = (size.height - track) / 2f
            val radius = CornerRadius(track / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.32f),
                topLeft = Offset(0f, top),
                size = Size(size.width, track),
                cornerRadius = radius
            )
            if (playedFraction > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.88f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width * playedFraction, track),
                    cornerRadius = radius
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(currentElapsed),
                style = Aurora.typography.numeric,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            if (centerContent != null) {
                Box(modifier = Modifier.align(Alignment.Center)) { centerContent() }
            }
            Text(
                text = if (isSeekable) formatTime(duration) else "--:--",
                style = Aurora.typography.numeric,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

/** NomaTune's V7 track height. */
private val TRACK_HEIGHT = 9.dp

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
