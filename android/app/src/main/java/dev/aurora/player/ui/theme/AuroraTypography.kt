package dev.aurora.player.ui.theme

import androidx.compose.runtime.Immutable
import dev.aurora.player.R
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * AURORA typography scale from docs/TYPOGRAPHY_FINAL.md.
 *
 * Montserrat, with a system sans-serif fallback. One variable file carries every weight,
 * so Light, Regular and Black cost one download and one resource rather than three.
 *
 * The weights carry meaning rather than decoration: headings are Black, and the name of a
 * song and of whoever made it are Light to Regular. A track title set in the same weight
 * as the heading above it competes with it, and a screen of uniformly heavy type has no
 * hierarchy at all - only volume.
 *
 * Tracking is size-specific, never one value across the scale. Letters read too far
 * apart as type grows, so display and headline tighten; small text needs a little air
 * to stay legible, so label and caption open up. A single letter-spacing is wrong at
 * one end of the scale or the other. Values are em-derived against each role's own
 * size and expressed in sp so they scale with the user's font-size setting.
 *
 * Tabular numerals should be enabled where timing/count values are displayed.
 */

/**
 * One variable face, asked for at three weights.
 *
 * Variable font settings need API 26, which is the project's minimum, so every device
 * that runs AURORA can render all three. A device that somehow cannot falls back to the
 * system sans rather than to no text.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val AuroraFontFamily = FontFamily(
    Font(
        R.font.montserrat,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(FontVariation.weight(300)),
    ),
    Font(
        R.font.montserrat,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.montserrat,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(900)),
    ),
)

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
    /** Small-caps section labels, which are headings: 14/20, black */
    val section: TextStyle,
    /** Duration, position, counters: 14/20, medium-semibold */
    val numeric: TextStyle,
    /** Large player timing values: 32/36, medium-semibold */
    val playerNumeric: TextStyle,
    /** A song's name on the player, where it is the subject: 30/36, regular */
    val trackTitleLarge: TextStyle,
    /** A song's name in a row or a card: 22/28, regular */
    val trackTitle: TextStyle,
    /** Whoever made it, under the name: 16/24, light */
    val trackArtist: TextStyle,
    /** The same, in a compact row where 16sp would crowd the title: 14/20, regular */
    val trackArtistCompact: TextStyle,
)

val AuroraTypographyTokens = AuroraTypography(
    display = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = -0.88.sp,
    ),
    headline = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = -0.48.sp,
    ),
    title = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = -0.2.sp,
    ),
    body = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.0.sp,
    ),
    label = TextStyle(
        fontFamily = AuroraFontFamily,
        // Regular, not semibold: the family ships Light, Regular and Black, because those
        // are the three that mean something here. Asking for a 600 it does not have gets
        // the nearest one, which is a decision made by a matcher rather than by anyone.
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.08.sp,
    ),
    caption = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.17.sp,
    ),
    numeric = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.08.sp,
    ),
    playerNumeric = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = -0.7.sp,
    ),
    section = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.2.sp,
    ),
    trackTitleLarge = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = -0.48.sp,
    ),
    trackTitle = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = -0.2.sp,
    ),
    trackArtist = TextStyle(
        fontFamily = AuroraFontFamily,
        // Light, and only at this size: below 16sp a 300 weight over glass starts to
        // disappear, which is why the compact one below is Regular instead.
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.0.sp,
    ),
    trackArtistCompact = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.08.sp,
    ),
)

val LocalAuroraTypography = staticCompositionLocalOf { AuroraTypographyTokens }
