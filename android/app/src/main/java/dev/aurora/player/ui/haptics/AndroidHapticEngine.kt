package dev.aurora.player.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

import android.annotation.SuppressLint

/**
 * Android implementation of HapticEngine using Vibrator and View's haptic feedback.
 */
@SuppressLint("InlinedApi", "ObsoleteSdkInt")
class AndroidHapticEngine(
    private val context: Context,
    private val view: View? = null
) : HapticEngine {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override val capabilityTier: HapticCapabilityTier
        get() = if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) HapticCapabilityTier.Rich else HapticCapabilityTier.Basic
        } else {
            HapticCapabilityTier.None
        }

    override val isEnabled: Boolean
        get() = vibrator?.hasVibrator() == true

    private var lastScrubTickTime = 0L

    override fun fire(event: HapticEvent) {
        if (!isEnabled) return

        when (event) {
            is HapticEvent.Tap -> performViewFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            is HapticEvent.Selection -> performViewFeedback(HapticFeedbackConstants.CLOCK_TICK)
            is HapticEvent.Toggle -> performViewFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            is HapticEvent.SliderTick, is HapticEvent.Scrub -> {
                // Rate limit scrub ticks
                val now = System.currentTimeMillis()
                if (now - lastScrubTickTime > 100) {
                    performViewFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    lastScrubTickTime = now
                }
            }
            is HapticEvent.DragStart, is HapticEvent.DragDrop -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    performViewFeedback(HapticFeedbackConstants.DRAG_START)
                } else {
                    performViewFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
            is HapticEvent.QueueReorder, is HapticEvent.DragMove -> performViewFeedback(HapticFeedbackConstants.CLOCK_TICK)
            is HapticEvent.Favorite, is HapticEvent.Success -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    performViewFeedback(HapticFeedbackConstants.CONFIRM)
                } else {
                    vibratePredefined(VibrationEffect.EFFECT_TICK)
                }
            }
            is HapticEvent.Warning -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    performViewFeedback(HapticFeedbackConstants.REJECT)
                } else {
                    vibratePredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                }
            }
            is HapticEvent.Error -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    performViewFeedback(HapticFeedbackConstants.REJECT)
                } else {
                    vibratePredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                }
            }
            is HapticEvent.DownloadComplete -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    performViewFeedback(HapticFeedbackConstants.CONFIRM)
                } else {
                    vibratePredefined(VibrationEffect.EFFECT_TICK)
                }
            }
        }
    }

    private fun performViewFeedback(constant: Int) {
        view?.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            ?: vibratePredefined(VibrationEffect.EFFECT_TICK)
    }

    private fun vibratePredefined(effectId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(effectId))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(20)
        }
    }
}
