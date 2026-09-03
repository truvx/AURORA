package dev.aurora.player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AURORA depth and glass recipe tokens from docs/DESIGN_TOKENS.md.
 *
 * Glass recipes combine background tint, backdrop blur, readable scrim,
 * soft highlight, and optional shadow. Blur is never a requirement for
 * contrast or state.
 */
@Immutable
data class AuroraDepth(
    // Blur radii
    val blurNone: Dp = 0.dp,
    val blurSubtle: Dp = 8.dp,
    val blurSurface: Dp = 16.dp,
    val blurFloating: Dp = 24.dp,

    // Opacity steps for glass recipes
    val tintOpacity08: Float = 0.08f,
    val tintOpacity12: Float = 0.12f,
    val tintOpacity16: Float = 0.16f,
    val tintOpacity24: Float = 0.24f,

    val highlightOpacity20: Float = 0.20f,
    val highlightOpacity36: Float = 0.36f,
    val highlightOpacity56: Float = 0.56f,

    val scrimOpacity20: Float = 0.20f,
    val scrimOpacity40: Float = 0.40f,
    val scrimOpacity64: Float = 0.64f,

    val disabledOpacity: Float = 0.48f,

    // Shadow elevation for depth levels
    val elevationCanvas: Dp = 0.dp,
    val elevationSurface: Dp = 1.dp,
    val elevationElevated: Dp = 4.dp,
    val elevationFloating: Dp = 8.dp,
)

val AuroraDepthTokens = AuroraDepth()

val LocalAuroraDepth = staticCompositionLocalOf { AuroraDepthTokens }
