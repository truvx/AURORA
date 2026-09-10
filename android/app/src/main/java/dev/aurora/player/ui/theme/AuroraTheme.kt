package dev.aurora.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * AURORA theme entry point.
 *
 * Provides AURORA-specific design tokens (colors, typography, spacing,
 * shapes, motion, depth) via CompositionLocal. Material 3 is used as
 * a technical foundation only — the AURORA visual identity is defined
 * by the custom tokens, not default M3 styling.
 */
@Composable
fun AuroraTheme(
    darkTheme: Boolean = true, // AURORA is primarily a dark/premium visual experience by default
    content: @Composable () -> Unit,
) {
    val auroraColors = if (darkTheme) AuroraDarkColors else AuroraLightColors

    // Map AURORA tokens into M3 for components that fall through to M3 defaults
    val m3ColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = auroraColors.accentPrimary,
            secondary = auroraColors.accentSecondary,
            background = auroraColors.backgroundPrimary,
            surface = auroraColors.surfaceOpaqueFallback,
            error = auroraColors.statusError,
            onPrimary = auroraColors.controlOnPrimary,
            onBackground = auroraColors.textPrimary,
            onSurface = auroraColors.textPrimary,
            onError = auroraColors.textInverse,
        )
    } else {
        lightColorScheme(
            primary = auroraColors.accentPrimary,
            secondary = auroraColors.accentSecondary,
            background = auroraColors.backgroundPrimary,
            surface = auroraColors.surfaceOpaqueFallback,
            error = auroraColors.statusError,
            onPrimary = auroraColors.controlOnPrimary,
            onBackground = auroraColors.textPrimary,
            onSurface = auroraColors.textPrimary,
            onError = auroraColors.textInverse,
        )
    }

    CompositionLocalProvider(
        LocalAuroraColors provides auroraColors,
        LocalAuroraTypography provides AuroraTypographyTokens,
        LocalAuroraSpacing provides AuroraSpacingTokens,
        LocalAuroraShapes provides AuroraShapeTokens,
        LocalAuroraMotion provides AuroraMotionTokens,
        LocalAuroraDepth provides AuroraDepthTokens,
    ) {
        MaterialTheme(
            colorScheme = m3ColorScheme,
            content = content,
        )
    }
}

/**
 * Accessor for AURORA design tokens within a composable.
 */
object Aurora {
    val colors: AuroraColors
        @Composable @ReadOnlyComposable
        get() = LocalAuroraColors.current

    val typography: AuroraTypography
        @Composable @ReadOnlyComposable
        get() = LocalAuroraTypography.current

    val spacing: AuroraSpacing
        @Composable @ReadOnlyComposable
        get() = LocalAuroraSpacing.current

    val shapes: AuroraShapes
        @Composable @ReadOnlyComposable
        get() = LocalAuroraShapes.current

    val motion: AuroraMotion
        @Composable @ReadOnlyComposable
        get() = LocalAuroraMotion.current

    val depth: AuroraDepth
        @Composable @ReadOnlyComposable
        get() = LocalAuroraDepth.current
}
