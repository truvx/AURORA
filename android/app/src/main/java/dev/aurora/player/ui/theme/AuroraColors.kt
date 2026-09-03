package dev.aurora.player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * AURORA semantic color tokens.
 *
 * Seeds from docs/DESIGN_TOKENS.md. Components consume semantic tokens,
 * never raw color primitives. Contrast must be validated after compositing
 * over actual surfaces.
 */
@Immutable
data class AuroraColors(
    // Backgrounds
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,

    // Surfaces — glass materials
    val surfaceGlassPrimary: Color,
    val surfaceGlassSecondary: Color,
    val surfaceGlassElevated: Color,
    val surfaceOpaqueFallback: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textInverse: Color,

    // Accent
    val accentPrimary: Color,
    val accentSecondary: Color,

    // Controls
    val controlPrimary: Color,
    val controlOnPrimary: Color,

    // Focus
    val focusRing: Color,

    // Divider
    val dividerSubtle: Color,

    // Player
    val playerBackground: Color,
    val playerControl: Color,
    val playerProgress: Color,

    // Status — semantic, never color-only
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,

    // Glass recipes
    val glassTint: Color,
    val glassHighlight: Color,
    val glassScrim: Color,
)

// --- Primitive palette from DESIGN_TOKENS.md ---

private object LightPrimitives {
    val neutral0 = Color(0xFFFFFFFF)
    val neutral10 = Color(0xFFF7F8FA)
    val neutral20 = Color(0xFFECEEF2)
    val neutral40 = Color(0xFFD2D6DE)
    val neutral70 = Color(0xFF606875)
    val neutral90 = Color(0xFF252A33)
    val accentCyan = Color(0xFF087E9B)
    val accentViolet = Color(0xFF6C51C9)
    val success = Color(0xFF197A4A)
    val warning = Color(0xFF8B5A00)
    val error = Color(0xFFB3261E)
}

private object DarkPrimitives {
    val neutral0 = Color(0xFF000000)
    val neutral10 = Color(0xFF111318)
    val neutral20 = Color(0xFF1B1E25)
    val neutral40 = Color(0xFF363B46)
    val neutral70 = Color(0xFFA7AFBD)
    val neutral90 = Color(0xFFF0F2F6)
    val accentCyan = Color(0xFF66D9F2)
    val accentViolet = Color(0xFFBBA8FF)
    val success = Color(0xFF67D49A)
    val warning = Color(0xFFF2C66D)
    val error = Color(0xFFFF9F99)
}

val AuroraLightColors = AuroraColors(
    backgroundPrimary = LightPrimitives.neutral0,
    backgroundSecondary = LightPrimitives.neutral10,
    surfaceGlassPrimary = LightPrimitives.neutral0.copy(alpha = 0.72f),
    surfaceGlassSecondary = LightPrimitives.neutral10.copy(alpha = 0.64f),
    surfaceGlassElevated = LightPrimitives.neutral0.copy(alpha = 0.84f),
    surfaceOpaqueFallback = LightPrimitives.neutral10,
    textPrimary = LightPrimitives.neutral90,
    textSecondary = LightPrimitives.neutral70,
    textTertiary = LightPrimitives.neutral70.copy(alpha = 0.72f),
    textInverse = LightPrimitives.neutral0,
    accentPrimary = LightPrimitives.accentCyan,
    accentSecondary = LightPrimitives.accentViolet,
    controlPrimary = LightPrimitives.accentCyan,
    controlOnPrimary = LightPrimitives.neutral0,
    focusRing = LightPrimitives.accentCyan.copy(alpha = 0.56f),
    dividerSubtle = LightPrimitives.neutral40.copy(alpha = 0.24f),
    playerBackground = LightPrimitives.neutral10,
    playerControl = LightPrimitives.neutral90,
    playerProgress = LightPrimitives.accentCyan,
    statusSuccess = LightPrimitives.success,
    statusWarning = LightPrimitives.warning,
    statusError = LightPrimitives.error,
    glassTint = LightPrimitives.neutral0.copy(alpha = 0.12f),
    glassHighlight = LightPrimitives.neutral0.copy(alpha = 0.36f),
    glassScrim = LightPrimitives.neutral90.copy(alpha = 0.20f),
)

val AuroraDarkColors = AuroraColors(
    backgroundPrimary = DarkPrimitives.neutral0,
    backgroundSecondary = DarkPrimitives.neutral10,
    surfaceGlassPrimary = DarkPrimitives.neutral20.copy(alpha = 0.72f),
    surfaceGlassSecondary = DarkPrimitives.neutral20.copy(alpha = 0.56f),
    surfaceGlassElevated = DarkPrimitives.neutral20.copy(alpha = 0.84f),
    surfaceOpaqueFallback = DarkPrimitives.neutral20,
    textPrimary = DarkPrimitives.neutral90,
    textSecondary = DarkPrimitives.neutral70,
    textTertiary = DarkPrimitives.neutral70.copy(alpha = 0.72f),
    textInverse = DarkPrimitives.neutral0,
    accentPrimary = DarkPrimitives.accentCyan,
    accentSecondary = DarkPrimitives.accentViolet,
    controlPrimary = DarkPrimitives.accentCyan,
    controlOnPrimary = DarkPrimitives.neutral0,
    focusRing = DarkPrimitives.accentCyan.copy(alpha = 0.56f),
    dividerSubtle = DarkPrimitives.neutral40.copy(alpha = 0.24f),
    playerBackground = DarkPrimitives.neutral10,
    playerControl = DarkPrimitives.neutral90,
    playerProgress = DarkPrimitives.accentCyan,
    statusSuccess = DarkPrimitives.success,
    statusWarning = DarkPrimitives.warning,
    statusError = DarkPrimitives.error,
    glassTint = DarkPrimitives.neutral20.copy(alpha = 0.16f),
    glassHighlight = DarkPrimitives.neutral40.copy(alpha = 0.20f),
    glassScrim = DarkPrimitives.neutral0.copy(alpha = 0.40f),
)

val LocalAuroraColors = staticCompositionLocalOf { AuroraLightColors }
