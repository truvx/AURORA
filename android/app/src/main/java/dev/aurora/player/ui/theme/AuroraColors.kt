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

    // Favorite — the "liked" heart. Always paired with a filled/outlined icon change.
    val favorite: Color,

    // AI orb — the gradient that marks the assistant everywhere it appears. Core to rim,
    // plus the soft halo around it.
    val aiOrbCore: Color,
    val aiOrbMid: Color,
    val aiOrbRim: Color,
    val aiOrbGlow: Color,

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
//
// Neutral, sampled from the approved reference designs (2026-09-22). Colour in AURORA comes
// from album artwork refracting through the glass, not from the chrome: the reference app's
// page is a neutral near-black (#151515) and its floating bars take their warmth from the
// cover behind them.
//
// The glass tint is white at a low alpha, because these materials *lighten* what is behind
// them - the reference nav bar reads ~22% white over near-black content. An earlier navy
// tint darkened instead, which is why the surfaces never looked like glass.
//
// Blue survives only as a quiet accent for selection and progress. Primary actions are the
// white pill from the playlist reference, which also happens to be the most legible fill
// available (near-black on white, 18:1).

private object LightPrimitives {
    val neutral0 = Color(0xFFFFFFFF)
    val neutral10 = Color(0xFFF6F6F8)
    val neutral20 = Color(0xFFECECEF)
    val neutral40 = Color(0xFFD2D2D8)
    val neutral70 = Color(0xFF5A5A63)
    val neutral90 = Color(0xFF17171A)
    val accentBlue = Color(0xFF2458D6)
    val favorite = Color(0xFFD7263D)
    val success = Color(0xFF197A4A)
    val warning = Color(0xFF8B5A00)
    val error = Color(0xFFB3261E)
}

private object DarkPrimitives {
    val neutral0 = Color(0xFF0E0E0F)
    val neutral10 = Color(0xFF151517)
    val neutral20 = Color(0xFF1F1F22)
    val neutral40 = Color(0xFF3A3A3F)
    val neutral70 = Color(0xFFABABB2)
    val neutral90 = Color(0xFFF5F5F7)
    val white = Color(0xFFFFFFFF)
    // Measured, not picked: as a selected tab label on the floating chrome the previous
    // #6A98FF came to 4.15:1 on web's lighter pill, below the 4.5 floor for body text.
    // This is the same value on both clients so one accent does not mean two colours.
    val accentBlue = Color(0xFF7EA6FF)
    val favorite = Color(0xFFF9555B)
    val success = Color(0xFF67D49A)
    val warning = Color(0xFFF2C66D)
    val error = Color(0xFFFF9F99)
}

/** The orb reads the same in both themes: it is a lit object, not a surface. */
private object OrbPrimitives {
    val core = Color(0xFF28A4FE)
    val mid = Color(0xFF3D6FF8)
    val rim = Color(0xFF2A3FC7)
    val glow = Color(0xFFB5C3FC)
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
    accentPrimary = LightPrimitives.accentBlue,
    accentSecondary = LightPrimitives.accentBlue,
    // The reference's primary action is a solid pill that carries the most contrast the
    // theme has: near-black on light, white on dark.
    controlPrimary = LightPrimitives.neutral90,
    controlOnPrimary = LightPrimitives.neutral0,
    focusRing = LightPrimitives.accentBlue.copy(alpha = 0.56f),
    dividerSubtle = LightPrimitives.neutral40.copy(alpha = 0.24f),
    playerBackground = LightPrimitives.neutral10,
    playerControl = LightPrimitives.neutral90,
    playerProgress = LightPrimitives.accentBlue,
    favorite = LightPrimitives.favorite,
    aiOrbCore = OrbPrimitives.core,
    aiOrbMid = OrbPrimitives.mid,
    aiOrbRim = OrbPrimitives.rim,
    aiOrbGlow = OrbPrimitives.glow,
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
    // White, not a dark tint: these materials lighten what is behind them.
    surfaceGlassPrimary = DarkPrimitives.white.copy(alpha = 0.12f),
    surfaceGlassSecondary = DarkPrimitives.white.copy(alpha = 0.07f),
    surfaceGlassElevated = DarkPrimitives.white.copy(alpha = 0.10f),
    surfaceOpaqueFallback = DarkPrimitives.neutral20,
    textPrimary = DarkPrimitives.neutral90,
    textSecondary = DarkPrimitives.neutral70,
    textTertiary = DarkPrimitives.neutral70.copy(alpha = 0.72f),
    textInverse = DarkPrimitives.neutral0,
    accentPrimary = DarkPrimitives.accentBlue,
    accentSecondary = DarkPrimitives.accentBlue,
    // The white "Play" pill from the playlist reference, with near-black content on it.
    controlPrimary = DarkPrimitives.white,
    controlOnPrimary = DarkPrimitives.neutral0,
    focusRing = DarkPrimitives.accentBlue.copy(alpha = 0.56f),
    dividerSubtle = DarkPrimitives.neutral40.copy(alpha = 0.24f),
    playerBackground = DarkPrimitives.neutral10,
    playerControl = DarkPrimitives.neutral90,
    playerProgress = DarkPrimitives.accentBlue,
    favorite = DarkPrimitives.favorite,
    aiOrbCore = OrbPrimitives.core,
    aiOrbMid = OrbPrimitives.mid,
    aiOrbRim = OrbPrimitives.rim,
    aiOrbGlow = OrbPrimitives.glow,
    statusSuccess = DarkPrimitives.success,
    statusWarning = DarkPrimitives.warning,
    statusError = DarkPrimitives.error,
    glassTint = DarkPrimitives.white.copy(alpha = 0.08f),
    glassHighlight = DarkPrimitives.white.copy(alpha = 0.22f), // Bright edge: light on the material
    glassScrim = DarkPrimitives.neutral0.copy(alpha = 0.30f),
)

val LocalAuroraColors = staticCompositionLocalOf { AuroraLightColors }
