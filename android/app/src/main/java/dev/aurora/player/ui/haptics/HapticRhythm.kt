package dev.aurora.player.ui.haptics

import android.annotation.SuppressLint
import android.os.VibrationEffect

/*
 * The vocabulary, kept apart from the device that speaks it.
 *
 * Its own file so a plain JVM test can read the rhythm table. Sharing a file with the
 * engine meant sharing a class, and that class builds AudioAttributes when it loads -
 * which on a JVM is a stub returning null, so merely naming a rhythm threw.
 */

@SuppressLint("InlinedApi")
internal enum class BeatKind(
    val primitive: Int,
    val fallback: Int,
    val pulseMs: Long,
    val amplitude: Int
) {
    LowTick(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, VibrationEffect.Composition.PRIMITIVE_TICK, 10L, 70),
    Tick(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_TICK, 12L, 110),
    Click(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_CLICK, 18L, 180),
    Thud(VibrationEffect.Composition.PRIMITIVE_THUD, VibrationEffect.Composition.PRIMITIVE_CLICK, 30L, 230),
    Rise(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_CLICK, 24L, 150);

    fun coarsePulseMs(): Long = if (pulseMs >= Click.pulseMs) 25L else 12L
}

internal class Beat(val kind: BeatKind, val scale: Float, val gapMs: Int)

/**
 * The rhythm each event is played as.
 *
 * `internal` rather than private so a test can assert that no two events compile to the
 * same beats. Two names for one sensation is the state this set was cut down from, and
 * nothing but a test stops it growing back.
 */
internal fun rhythm(event: HapticEvent): List<Beat> = when (event) {
    HapticEvent.Tap -> listOf(Beat(BeatKind.Click, 0.7f, 0))
    HapticEvent.Selection -> listOf(Beat(BeatKind.Tick, 0.6f, 0))
    HapticEvent.Toggle -> listOf(Beat(BeatKind.Tick, 0.5f, 0), Beat(BeatKind.Click, 0.8f, 24))
    // Quietest on purpose: it is the one that repeats.
    HapticEvent.Tick -> listOf(Beat(BeatKind.LowTick, 0.45f, 0))
    HapticEvent.Lift -> listOf(Beat(BeatKind.Rise, 0.6f, 0))
    HapticEvent.Commit -> listOf(Beat(BeatKind.Tick, 0.5f, 0), Beat(BeatKind.Thud, 0.7f, 20))
    // Two heavy beats, well apart. One beat of anything reads as "done".
    HapticEvent.Refused -> listOf(Beat(BeatKind.Thud, 0.9f, 0), Beat(BeatKind.Thud, 0.7f, 100))
}
