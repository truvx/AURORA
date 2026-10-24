package dev.aurora.player.ui.theme

import dev.aurora.player.ui.artwork.ArtworkPalette
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Glass is only safe if text stays readable through it.
 *
 * Now that the atmosphere takes its colour from whatever is playing, the backdrop behind
 * every translucent surface is chosen by the user's music rather than by the design system.
 * These check the two things that has to keep being true for artwork the app has never
 * seen: that the contrast-protecting scrim can always bring text to AA, and that it never
 * has to give up and drop translucency to do it.
 *
 * The palette's clamping range and the glass opacities are set independently, so this is
 * what stops one of them drifting away from the other.
 */
class GlassContrastTest {

    private val colors = AuroraDarkColors

    /** Every colour ArtworkPalette is allowed to produce, swept across hue and lightness. */
    private fun atmosphereColors(): List<androidx.compose.ui.graphics.Color> = buildList {
        var hue = 0f
        while (hue < 360f) {
            var lightness = ArtworkPalette.MIN_LIGHTNESS
            while (lightness <= ArtworkPalette.MAX_LIGHTNESS + 1e-4f) {
                for (saturation in listOf(0f, 0.25f, ArtworkPalette.MAX_CHROMA)) {
                    add(ArtworkPalette.fromHsl(hue, saturation, lightness))
                }
                lightness += 0.05f
            }
            hue += 15f
        }
    }

    @Test
    fun `primary text stays legible over any artwork atmosphere`() {
        for (backdrop in atmosphereColors()) {
            val alpha = ContrastScrim.requiredAlpha(
                foreground = colors.textPrimary,
                backdrop = backdrop,
                glass = colors.surfaceGlassPrimary
            )
            val ratio = ContrastScrim.contrastFor(
                scrimAlpha = alpha,
                foreground = colors.textPrimary,
                backdrop = backdrop,
                glass = colors.surfaceGlassPrimary
            )

            assertTrue(
                "backdrop $backdrop ended at $ratio with scrim $alpha",
                ratio >= ContrastScrim.BODY_TEXT_TARGET
            )
        }
    }

    @Test
    fun `secondary text stays legible over any artwork atmosphere`() {
        // The dimmer of the two text colours, and the one that fails first.
        for (backdrop in atmosphereColors()) {
            val alpha = ContrastScrim.requiredAlpha(
                foreground = colors.textSecondary,
                backdrop = backdrop,
                glass = colors.surfaceGlassPrimary
            )
            val ratio = ContrastScrim.contrastFor(
                scrimAlpha = alpha,
                foreground = colors.textSecondary,
                backdrop = backdrop,
                glass = colors.surfaceGlassPrimary
            )

            assertTrue(
                "backdrop $backdrop ended at $ratio with scrim $alpha",
                ratio >= ContrastScrim.BODY_TEXT_TARGET
            )
        }
    }

    @Test
    fun `no artwork forces translucency to be abandoned`() {
        // If a full scrim still could not reach the target, the surface would have to go
        // opaque and the glass identity would vanish for that album. The palette's clamping
        // exists partly to make sure that never happens.
        for (backdrop in atmosphereColors()) {
            assertFalse(
                "backdrop $backdrop could not be rescued by any scrim",
                ContrastScrim.requiresOpaqueFallback(
                    foreground = colors.textPrimary,
                    backdrop = backdrop,
                    glass = colors.surfaceGlassPrimary
                )
            )
        }
    }

    @Test
    fun `the elevated surface is legible too`() {
        // The mini-player and the navigation bar use the elevated glass, which is more
        // opaque - it should be at least as safe, and this catches it drifting.
        for (backdrop in atmosphereColors()) {
            val alpha = ContrastScrim.requiredAlpha(
                foreground = colors.textPrimary,
                backdrop = backdrop,
                glass = colors.surfaceGlassElevated
            )
            val ratio = ContrastScrim.contrastFor(
                scrimAlpha = alpha,
                foreground = colors.textPrimary,
                backdrop = backdrop,
                glass = colors.surfaceGlassElevated
            )

            assertTrue("backdrop $backdrop ended at $ratio", ratio >= ContrastScrim.BODY_TEXT_TARGET)
        }
    }

    @Test
    fun `a dark atmosphere needs no scrim at all`() {
        // The common case for this palette. Spending scrim on it would darken the design
        // for nothing and wash out the artwork wash the glass exists to show.
        val dark = ArtworkPalette.fromHsl(210f, 0.4f, ArtworkPalette.MIN_LIGHTNESS)

        val alpha = ContrastScrim.requiredAlpha(
            foreground = colors.textPrimary,
            backdrop = dark,
            glass = colors.surfaceGlassPrimary
        )

        assertTrue("expected no scrim for a dark backdrop, got $alpha", alpha == 0f)
    }

    @Test
    fun `the brightest allowed atmosphere still keeps the artwork visible`() {
        // A scrim strong enough to hide the atmosphere entirely would be technically legible
        // and visually pointless. This holds the ceiling that keeps glass meaning something.
        val brightest = ArtworkPalette.fromHsl(
            hue = 50f,
            saturation = ArtworkPalette.MAX_CHROMA,
            lightness = ArtworkPalette.MAX_LIGHTNESS
        )

        val alpha = ContrastScrim.requiredAlpha(
            foreground = colors.textPrimary,
            backdrop = brightest,
            glass = colors.surfaceGlassPrimary
        )

        assertTrue("scrim $alpha would black out the artwork", alpha < 0.85f)
    }
}
