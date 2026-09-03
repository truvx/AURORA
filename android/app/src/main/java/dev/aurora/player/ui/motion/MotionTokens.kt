package dev.aurora.player.ui.motion

import dev.aurora.player.ui.theme.AuroraMotionTokens

/**
 * Centralized motion token accessors.
 *
 * Components reference these instead of defining per-component
 * arbitrary curves. See docs/AURORA_MOTION_SPEC.md.
 */
object MotionTokens {
    val instant get() = AuroraMotionTokens.durationInstant
    val quick get() = AuroraMotionTokens.durationQuick
    val standard get() = AuroraMotionTokens.durationStandard
    val emphasis get() = AuroraMotionTokens.durationEmphasis
    val ambient get() = AuroraMotionTokens.durationAmbient

    val easingStandard get() = AuroraMotionTokens.easingStandard
    val easingDecelerate get() = AuroraMotionTokens.easingDecelerate
    val easingAccelerate get() = AuroraMotionTokens.easingAccelerate
    val easingEmphasized get() = AuroraMotionTokens.easingEmphasized
}
