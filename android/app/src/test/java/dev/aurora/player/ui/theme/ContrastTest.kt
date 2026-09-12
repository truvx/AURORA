package dev.aurora.player.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 contrast for text drawn on AURORA's glass surfaces.
 *
 * docs/ACCESSIBILITY_SPEC.md requires validating contrast "against the final artwork, blur,
 * scrim, and surface combination", and states that glass is never the only contrast
 * mechanism. Album artwork can be any colour, so these pin the two worst cases: pure black
 * and pure white behind a translucent surface.
 *
 * A failure here is a real finding about the scrim, not a reason to lower the threshold.
 */
class ContrastTest {

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /** Composites [top] over [bottom] using top's alpha, as the renderer does. */
    private fun over(top: Color, bottom: Color): Color = Color(
        red = top.red * top.alpha + bottom.red * (1 - top.alpha),
        green = top.green * top.alpha + bottom.green * (1 - top.alpha),
        blue = top.blue * top.alpha + bottom.blue * (1 - top.alpha),
        alpha = 1f
    )

    private val minimumBodyContrast = 4.5

    @Test
    fun `dark theme text is legible over the darkest possible artwork`() {
        val colors = AuroraDarkColors
        val surface = over(colors.surfaceGlassPrimary, Color.Black)
        val ratio = contrastRatio(colors.textPrimary, surface)
        assertTrue("contrast over black artwork was $ratio, below $minimumBodyContrast", ratio >= minimumBodyContrast)
    }

    @Test
    fun `text is legible over the background actually rendered today`() {
        // AmbientArtworkLayer currently draws a synthetic gradient over backgroundPrimary;
        // it does not render album artwork yet. This pins the composite that ships.
        val colors = AuroraDarkColors
        val surface = over(colors.surfaceGlassPrimary, colors.backgroundPrimary)
        val ratio = contrastRatio(colors.textPrimary, surface)
        assertTrue("contrast over the rendered background was $ratio, below $minimumBodyContrast", ratio >= minimumBodyContrast)
    }

    @Test
    fun `bright artwork behind glass would need a scrim before artwork rendering ships`() {
        // Measured, not asserted as passing: dark glass is only 20% opaque, so a white album
        // cover composites to roughly white and near-white text falls to about 1.35:1 -
        // effectively illegible. AmbientArtworkLayer renders no artwork today, so this is a
        // prediction rather than a live defect, and the scrim the design system calls for
        // does not exist yet.
        val colors = AuroraDarkColors
        val overWhite = over(colors.surfaceGlassPrimary, Color.White)
        val ratio = contrastRatio(colors.textPrimary, overWhite)

        assertTrue(
            "This test exists to fail the moment artwork rendering lands without a scrim. " +
                "Measured contrast over white artwork: $ratio",
            ratio < minimumBodyContrast
        )
    }

    @Test
    fun `the opaque fallback meets contrast without depending on artwork at all`() {
        // Reduced transparency replaces glass with this surface, so it has to stand alone.
        val colors = AuroraDarkColors
        val ratio = contrastRatio(colors.textPrimary, colors.surfaceOpaqueFallback)
        assertTrue("opaque fallback contrast was $ratio, below $minimumBodyContrast", ratio >= minimumBodyContrast)
    }

    @Test
    fun `light theme opaque fallback meets contrast`() {
        val colors = AuroraLightColors
        val ratio = contrastRatio(colors.textPrimary, colors.surfaceOpaqueFallback)
        assertTrue("light opaque fallback contrast was $ratio, below $minimumBodyContrast", ratio >= minimumBodyContrast)
    }
}
