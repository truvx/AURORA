package dev.aurora.player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * AURORA typography scale from docs/TYPOGRAPHY_FINAL.md.
 *
 * Uses Inter as the primary family with system sans-serif fallback.
 * Letter spacing is 0 for all tokens. Tabular numerals should be
 * enabled where timing/count values are displayed.
 */

val InterFontFamily = FontFamily.Default // Phase 1: system sans-serif fallback.
// Future: load Inter variable font via GoogleFont or bundled resource.

@Immutable
data class AuroraTypography(
    /** Rare hero / now-playing title: 40/46, bold */
    val display: TextStyle,
    /** Screen title: 30/36, bold */
    val headline: TextStyle,
    /** Section / card title: 22/28, semibold-bold */
    val title: TextStyle,
    /** Primary reading / action text: 16/24, regular-medium */
    val body: TextStyle,
    /** Controls and metadata: 14/20, semibold */
    val label: TextStyle,
    /** Tertiary metadata: 12/16, regular-medium */
    val caption: TextStyle,
    /** Duration, position, counters: 14/20, medium-semibold */
    val numeric: TextStyle,
    /** Large player timing values: 32/36, medium-semibold */
    val playerNumeric: TextStyle,
)

val AuroraTypographyTokens = AuroraTypography(
    display = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp,
    ),
    headline = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    title = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    body = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    label = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    caption = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    numeric = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    playerNumeric = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
)

val LocalAuroraTypography = staticCompositionLocalOf { AuroraTypographyTokens }
