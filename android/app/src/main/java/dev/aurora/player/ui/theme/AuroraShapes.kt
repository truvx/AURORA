package dev.aurora.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * AURORA shape tokens from docs/DESIGN_TOKENS.md.
 *
 * Rounded shapes are expressive at surfaces and restrained in dense rows.
 * Full pills are reserved for compact controls/status, not every container.
 */
@Immutable
data class AuroraShapes(
    /** 12 dp — buttons, controls, compact containers */
    val control: Shape = RoundedCornerShape(12.dp),
    /** 20 dp — cards, grouped content */
    val card: Shape = RoundedCornerShape(20.dp),
    /** 28 dp — bottom sheets */
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    /** 24 dp — dialogs */
    val dialog: Shape = RoundedCornerShape(24.dp),
    /** 16 dp — artwork */
    val artwork: Shape = RoundedCornerShape(16.dp),
)

val AuroraShapeTokens = AuroraShapes()

val LocalAuroraShapes = staticCompositionLocalOf { AuroraShapeTokens }
