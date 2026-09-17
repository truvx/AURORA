package dev.aurora.player.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * AURORA motion tokens from docs/AURORA_MOTION_SPEC.md and docs/DESIGN_TOKENS.md.
 *
 * Motion explains ownership, hierarchy, cause/effect, and continuity.
 * One dominant movement per interaction. Use springs for connected surfaces
 * and time-based easing for opacity/disclosure/transient menus.
 */
@Immutable
data class AuroraMotion(
    // Durations (ms)
    val durationInstant: Int = 80,
    val durationQuick: Int = 140,
    val durationStandard: Int = 220,
    val durationEmphasis: Int = 360,
    val durationAmbient: Int = 600,

    // Easing curves
    val easingStandard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
    val easingDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f),
    val easingAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f),
    val easingEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),

    /*
     * Spring families.
     *
     * Expressed in damping ratio and response rather than raw stiffness, because those are
     * the two things a spring's feel actually depends on: response is how quickly it
     * reaches the target in seconds, damping ratio is whether it overshoots. Stiffness is
     * derived below; picking stiffness numbers directly makes every family a guess.
     *
     * Bounce is reserved for motion a gesture put momentum into. Overshoot on a menu that
     * merely appeared reads as sloppy; overshoot on a sheet that was flicked reads as
     * physical - which is why `responsive` and `standard` are critically damped and only
     * `settle`, used at drag release, bounces.
     */
    val springResponsiveDamping: Float = Spring.DampingRatioNoBouncy,
    val springResponsiveResponse: Float = 0.3f,
    val springStandardDamping: Float = Spring.DampingRatioNoBouncy,
    val springStandardResponse: Float = 0.4f,
    val springSoftDamping: Float = Spring.DampingRatioNoBouncy,
    val springSoftResponse: Float = 0.6f,
    val springSettleDamping: Float = 0.8f,
    val springSettleResponse: Float = 0.3f,
) {
    val springResponsiveStiffness: Float get() = stiffnessFor(springResponsiveResponse)
    val springStandardStiffness: Float get() = stiffnessFor(springStandardResponse)
    val springSoftStiffness: Float get() = stiffnessFor(springSoftResponse)
    val springSettleStiffness: Float get() = stiffnessFor(springSettleResponse)
}

/**
 * Converts a response time into the stiffness Compose's [androidx.compose.animation.core.SpringSpec]
 * expects.
 *
 * Compose takes stiffness, which is the square of the undamped natural frequency for a unit
 * mass, while a response of `r` seconds means that frequency is `2*pi / r`. Halving the
 * response therefore quadruples the stiffness, which is exactly the relationship that makes
 * hand-picked stiffness values so hard to reason about.
 */
fun stiffnessFor(responseSeconds: Float): Float {
    require(responseSeconds > 0f) { "response must be positive, was $responseSeconds" }
    val naturalFrequency = (2.0 * Math.PI / responseSeconds).toFloat()
    return naturalFrequency * naturalFrequency
}

val AuroraMotionTokens = AuroraMotion()

val LocalAuroraMotion = staticCompositionLocalOf { AuroraMotionTokens }
