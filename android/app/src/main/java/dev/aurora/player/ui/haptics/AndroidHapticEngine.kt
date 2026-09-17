package dev.aurora.player.ui.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Android implementation of HapticEngine.
 *
 * Semantic events compile to rhythms of beats, rendered through the richest
 * tier the actuator supports: primitive composition, amplitude-scaled waveform,
 * or plain on/off pulses.
 */
@SuppressLint("InlinedApi", "ObsoleteSdkInt")
class AndroidHapticEngine(context: Context) : HapticEngine {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val canCompose: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK
        ) == true

    private val canScaleAmplitude: Boolean = vibrator?.hasAmplitudeControl() == true

    override val capabilityTier: HapticCapabilityTier
        get() = when {
            vibrator?.hasVibrator() != true -> HapticCapabilityTier.None
            canCompose -> HapticCapabilityTier.Rich
            else -> HapticCapabilityTier.Basic
        }

    private var userEnabled = true

    override val isEnabled: Boolean
        get() = userEnabled && vibrator?.hasVibrator() == true

    override fun setEnabled(enabled: Boolean) {
        userEnabled = enabled
    }

    private val compiled = mutableMapOf<HapticEvent, VibrationEffect>()
    private var lastTickTime = 0L

    override fun fire(event: HapticEvent) {
        if (!isEnabled) return

        if (event is HapticEvent.Scrub || event is HapticEvent.SliderTick || event is HapticEvent.DragMove) {
            val now = System.currentTimeMillis()
            if (now - lastTickTime < TICK_INTERVAL_MS) return
            lastTickTime = now
        }

        val effect = synchronized(compiled) {
            compiled.getOrPut(event) { compile(rhythm(event)) }
        }
        vibrate(effect)
    }

    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator?.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(effect, TOUCH_AUDIO_ATTRIBUTES)
        }
    }

    private fun compile(beats: List<Beat>): VibrationEffect = when {
        canCompose -> {
            val composition = VibrationEffect.startComposition()
            beats.forEach { composition.addPrimitive(resolvePrimitive(it.kind), it.scale, it.gapMs) }
            composition.compose()
        }

        canScaleAmplitude -> VibrationEffect.createWaveform(
            beats.flatMap { listOf(it.gapMs.toLong(), it.kind.pulseMs) }.toLongArray(),
            beats.flatMap { listOf(0, (it.kind.amplitude * it.scale).toInt().coerceIn(1, 255)) }.toIntArray(),
            -1
        )

        else -> VibrationEffect.createWaveform(
            beats.flatMap { listOf(it.gapMs.toLong(), it.kind.coarsePulseMs()) }.toLongArray(),
            -1
        )
    }

    private fun resolvePrimitive(kind: BeatKind): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator?.areAllPrimitivesSupported(kind.primitive) == true
        ) {
            kind.primitive
        } else {
            kind.fallback
        }
}

private const val TICK_INTERVAL_MS = 100L

private val TOUCH_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

@SuppressLint("InlinedApi")
private enum class BeatKind(
    val primitive: Int,
    val fallback: Int,
    val pulseMs: Long,
    val amplitude: Int
) {
    LowTick(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, VibrationEffect.Composition.PRIMITIVE_TICK, 10L, 70),
    Tick(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_TICK, 12L, 110),
    Click(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_CLICK, 18L, 180),
    Thud(VibrationEffect.Composition.PRIMITIVE_THUD, VibrationEffect.Composition.PRIMITIVE_CLICK, 30L, 230),
    Rise(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_CLICK, 24L, 150),
    SlowRise(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, VibrationEffect.Composition.PRIMITIVE_CLICK, 44L, 130),
    Fall(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, VibrationEffect.Composition.PRIMITIVE_CLICK, 24L, 130);

    fun coarsePulseMs(): Long = if (pulseMs >= Click.pulseMs) 25L else 12L
}

private class Beat(val kind: BeatKind, val scale: Float, val gapMs: Int)

private fun rhythm(event: HapticEvent): List<Beat> = when (event) {
    HapticEvent.Tap -> listOf(Beat(BeatKind.Click, 0.7f, 0))
    HapticEvent.Selection -> listOf(Beat(BeatKind.Tick, 0.6f, 0))
    HapticEvent.Toggle -> listOf(Beat(BeatKind.Tick, 0.5f, 0), Beat(BeatKind.Click, 0.8f, 24))
    HapticEvent.Scrub, HapticEvent.SliderTick -> listOf(Beat(BeatKind.LowTick, 0.45f, 0))
    HapticEvent.DragMove -> listOf(Beat(BeatKind.LowTick, 0.35f, 0))
    HapticEvent.DragStart -> listOf(Beat(BeatKind.Rise, 0.6f, 0))
    HapticEvent.DragDrop, HapticEvent.QueueReorder ->
        listOf(Beat(BeatKind.Tick, 0.5f, 0), Beat(BeatKind.Thud, 0.7f, 20))

    HapticEvent.Favorite -> listOf(Beat(BeatKind.Tick, 0.4f, 0), Beat(BeatKind.Click, 0.85f, 14))
    HapticEvent.Success ->
        listOf(Beat(BeatKind.Tick, 0.4f, 0), Beat(BeatKind.Tick, 0.55f, 16), Beat(BeatKind.Click, 0.7f, 16))

    HapticEvent.DownloadComplete -> listOf(Beat(BeatKind.Tick, 0.4f, 0), Beat(BeatKind.Click, 0.75f, 18))
    HapticEvent.Warning -> listOf(Beat(BeatKind.Click, 0.6f, 0), Beat(BeatKind.Click, 0.6f, 90))
    HapticEvent.Error -> listOf(Beat(BeatKind.Thud, 0.9f, 0), Beat(BeatKind.Thud, 0.7f, 100))
    // Accelerating: gaps contract into the landing beat.
    HapticEvent.SkipNext ->
        listOf(Beat(BeatKind.Tick, 0.35f, 0), Beat(BeatKind.Tick, 0.5f, 40), Beat(BeatKind.Click, 0.75f, 24))
    // Decelerating mirror of SkipNext.
    HapticEvent.SkipPrevious ->
        listOf(Beat(BeatKind.Click, 0.75f, 0), Beat(BeatKind.Tick, 0.5f, 24), Beat(BeatKind.Tick, 0.35f, 40))

    HapticEvent.Resume -> listOf(Beat(BeatKind.SlowRise, 0.5f, 0), Beat(BeatKind.Click, 0.8f, 30))
    HapticEvent.Pause -> listOf(Beat(BeatKind.Click, 0.8f, 0), Beat(BeatKind.Fall, 0.5f, 30))
    HapticEvent.Expand -> listOf(Beat(BeatKind.Rise, 0.7f, 0), Beat(BeatKind.Tick, 0.4f, 30))
}
