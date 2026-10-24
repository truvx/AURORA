package dev.aurora.player.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Works out how much scrim a backdrop needs before text on glass stays legible.
 *
 * The design system's stack is: canvas, artwork atmosphere, contrast-protecting scrim, glass
 * surface, content. The scrim is the layer that makes translucency safe - without it, a
 * bright album cover composites through 20%-opaque glass and near-white text lands at about
 * 1.35:1, which is illegible.
 *
 * docs/AURORA_MASTER_DESIGN_SYSTEM.md requires the scrim to be raised "until all required
 * content passes contrast", so this measures the real composite rather than applying a fixed
 * dimming value that would be too weak for white art and needlessly heavy for black art.
 */
object ContrastScrim {

    /** WCAG 2.1 AA for body text. */
    const val BODY_TEXT_TARGET = 4.5

    /** WCAG 2.1 AA for large text and meaningful non-text elements. */
    const val LARGE_TEXT_TARGET = 3.0

    /**
     * The smallest scrim alpha that brings [foreground] to [targetRatio] over the composite
     * of [glass] on [scrim] on [backdrop].
     *
     * Returns 1.0 when even a full scrim cannot reach the target, which is the caller's
     * signal to drop translucency entirely and use the opaque fallback rather than ship
     * unreadable text.
     */
    fun requiredAlpha(
        foreground: Color,
        backdrop: Color,
        glass: Color,
        targetRatio: Double = BODY_TEXT_TARGET,
        scrim: Color = Color.Black
    ): Float {
        if (contrastFor(0f, foreground, backdrop, glass, scrim) >= targetRatio) return 0f

        // Contrast is monotonic in scrim alpha for a scrim that moves the backdrop away from
        // the foreground, so a bisection converges without scanning every value.
        var low = 0f
        var high = 1f
        repeat(20) {
            val mid = (low + high) / 2f
            if (contrastFor(mid, foreground, backdrop, glass, scrim) >= targetRatio) {
                high = mid
            } else {
                low = mid
            }
        }

        return if (contrastFor(high, foreground, backdrop, glass, scrim) >= targetRatio) {
            high
        } else {
            1f
        }
    }

    /**
     * True when even a full scrim leaves the text below target, so the surface must fall back
     * to opaque rather than staying translucent.
     */
    fun requiresOpaqueFallback(
        foreground: Color,
        backdrop: Color,
        glass: Color,
        targetRatio: Double = BODY_TEXT_TARGET,
        scrim: Color = Color.Black
    ): Boolean = contrastFor(1f, foreground, backdrop, glass, scrim) < targetRatio

    /** Contrast of [foreground] over the finished stack at a given scrim alpha. */
    fun contrastFor(
        scrimAlpha: Float,
        foreground: Color,
        backdrop: Color,
        glass: Color,
        scrim: Color = Color.Black
    ): Double {
        val scrimmed = composite(scrim.copy(alpha = scrimAlpha), backdrop)
        val surface = composite(glass, scrimmed)
        return contrastRatio(foreground, surface)
    }

    /** WCAG 2.1 contrast ratio. Both colours are treated as opaque. */
    fun contrastRatio(foreground: Color, background: Color): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /** Source-over composite of [top] onto an opaque [bottom]. */
    fun composite(top: Color, bottom: Color): Color = Color(
        red = top.red * top.alpha + bottom.red * (1 - top.alpha),
        green = top.green * top.alpha + bottom.green * (1 - top.alpha),
        blue = top.blue * top.alpha + bottom.blue * (1 - top.alpha),
        alpha = 1f
    )

    fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
