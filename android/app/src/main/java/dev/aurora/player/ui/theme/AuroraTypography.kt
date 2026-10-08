package dev.aurora.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import dev.aurora.player.R
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * AURORA typography scale from docs/TYPOGRAPHY_FINAL.md.
 *
 * Source Sans Pro (Adobe, SIL OFL 1.1), with the system sans behind it. One family for the
 * whole interface: hierarchy comes from size, weight and colour, never from a second
 * typeface.
 *
 * AURORA bundles Regular and Bold, and nothing between. Roles asked for a 500 get 400
 * rather than a synthesised weight, which is a smeared Regular and looks like one.
 *
 * Numbers are proportional in this face, so the roles that show a running time switch on
 * its tabular figures: otherwise "1:11" is narrower than "0:00" and the layout shifts as
 * it ticks.
 *
 * Tracking is near zero at large sizes. It opens only on the small uppercase labels, where
 * a little air is what makes capitals legible at 12-14sp.
 */

/** The two weights AURORA bundles. Nothing asks for a third. */
val AuroraFontFamily = FontFamily(
    Font(R.font.source_sans_pro_regular, weight = FontWeight.Normal),
    Font(R.font.source_sans_pro_bold, weight = FontWeight.Bold),
)

/** Equal-width digits, for anything that counts. */
private const val TABULAR_FIGURES = "tnum"

@Immutable
data class AuroraTypography(
    /** Rare hero / screen title: 34/42, bold */
    val display: TextStyle,
    /** Screen title: 26/34, bold */
    val headline: TextStyle,
    /** Section / card title: 20/28, bold */
    val title: TextStyle,
    /** Primary reading / action text: 16/24, regular */
    val body: TextStyle,
    /** Controls and metadata: 14/20, regular */
    val label: TextStyle,
    /** Tertiary metadata: 12/16, regular */
    val caption: TextStyle,
    /** Small-caps section labels, which are headings: 12/18, bold, uppercase in use */
    val section: TextStyle,
    /** Duration, position, counters: 14/20, regular, tabular figures. */
    val numeric: TextStyle,
    /** Large player timing values: 30/36, regular */
    val playerNumeric: TextStyle,
    /** A song's name on the player, where it is the subject: 26/34, regular */
    val trackTitleLarge: TextStyle,
    /** A song's name in a row or a card: 18/26, regular */
    val trackTitle: TextStyle,
    /** Whoever made it, under the name: 15/22, regular */
    val trackArtist: TextStyle,
    /** The same, in a compact row where 15sp would crowd the title: 13/18, regular */
    val trackArtistCompact: TextStyle,
)

val AuroraTypographyTokens = AuroraTypography(
    display = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    headline = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    ),
    title = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.0.sp,
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
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.0.sp,
    ),
    caption = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    numeric = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.0.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    playerNumeric = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    section = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        // The one place tracking opens: small capitals need it, and this is the label that
        // is always set in them.
        letterSpacing = 0.9.sp,
    ),
    trackTitleLarge = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    ),
    trackTitle = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.0.sp,
    ),
    trackArtist = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.0.sp,
    ),
    trackArtistCompact = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.0.sp,
    ),
)

/**
 * The same family for everything Material draws on its own.
 *
 * AURORA styles its own text from [AuroraTypographyTokens], but Material's components -
 * menu items, dialogs, anything that falls through to a default - carry M3's own scale,
 * and that scale's family is the platform sans. Without this the app is one typeface with
 * Roboto leaking out of the overflow menus, which is exactly the mixed-family look the
 * design asks to avoid. Setting it here means no component has to ask for the font.
 */
val AuroraMaterialTypography: Typography = Typography().run {
    fun TextStyle.aurora() = copy(fontFamily = AuroraFontFamily)
    copy(
        displayLarge = displayLarge.aurora(),
        displayMedium = displayMedium.aurora(),
        displaySmall = displaySmall.aurora(),
        headlineLarge = headlineLarge.aurora(),
        headlineMedium = headlineMedium.aurora(),
        headlineSmall = headlineSmall.aurora(),
        titleLarge = titleLarge.aurora(),
        titleMedium = titleMedium.aurora(),
        titleSmall = titleSmall.aurora(),
        bodyLarge = bodyLarge.aurora(),
        bodyMedium = bodyMedium.aurora(),
        bodySmall = bodySmall.aurora(),
        labelLarge = labelLarge.aurora(),
        labelMedium = labelMedium.aurora(),
        labelSmall = labelSmall.aurora(),
    )
}

val LocalAuroraTypography = staticCompositionLocalOf { AuroraTypographyTokens }
