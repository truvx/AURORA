package dev.aurora.player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AURORA spacing tokens: 4-unit rhythm from docs/DESIGN_TOKENS.md.
 *
 * Use larger structural gaps between sections and tighter rhythm inside rows.
 * Avoid arbitrary one-off values.
 */
@Immutable
data class AuroraSpacing(
    /** 4 dp */
    val space1: Dp = 4.dp,
    /** 8 dp */
    val space2: Dp = 8.dp,
    /** 12 dp */
    val space3: Dp = 12.dp,
    /** 16 dp — standard content padding */
    val space4: Dp = 16.dp,
    /** 20 dp */
    val space5: Dp = 20.dp,
    /** 24 dp */
    val space6: Dp = 24.dp,
    /** 32 dp */
    val space8: Dp = 32.dp,
    /** 40 dp */
    val space10: Dp = 40.dp,
    /** 48 dp — minimum touch target */
    val space12: Dp = 48.dp,
    /** 64 dp */
    val space16: Dp = 64.dp,
)

val AuroraSpacingTokens = AuroraSpacing()

val LocalAuroraSpacing = staticCompositionLocalOf { AuroraSpacingTokens }
