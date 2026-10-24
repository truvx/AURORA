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

        // The only event that arrives under a moving finger, and so the only one that has
        // to be held back. Everything else is already once per deliberate action.
        if (event is HapticEvent.Tick) {
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
