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

    // Spring families — stiffness/damping for Compose SpringSpec
    val springResponsiveStiffness: Float = Spring.StiffnessMediumLow,
    val springResponsiveDamping: Float = Spring.DampingRatioNoBouncy,
    val springStandardStiffness: Float = Spring.StiffnessMediumLow,
    val springStandardDamping: Float = Spring.DampingRatioLowBouncy,
    val springSoftStiffness: Float = Spring.StiffnessLow,
    val springSoftDamping: Float = Spring.DampingRatioLowBouncy,
    val springSettleStiffness: Float = Spring.StiffnessMedium,
    val springSettleDamping: Float = Spring.DampingRatioMediumBouncy,
)

val AuroraMotionTokens = AuroraMotion()

val LocalAuroraMotion = staticCompositionLocalOf { AuroraMotionTokens }
