package dev.aurora.player.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scrim is what makes translucency safe over artwork the app does not control.
 *
 * These check the property that matters - text stays legible whatever the artwork - rather
 * than pinning specific alpha values, which would break on any palette change without
 * meaning anything had regressed.
 */
class ContrastScrimTest {

    private val colors = AuroraDarkColors
    private val text = colors.textPrimary
    private val glass = colors.surfaceGlassPrimary

    private fun contrastAfterScrim(backdrop: Color): Double {
        val alpha = ContrastScrim.requiredAlpha(text, backdrop, glass)
        return ContrastScrim.contrastFor(alpha, text, backdrop, glass)
    }

    @Test
    fun `white artwork gets a scrim that restores legibility`() {
        // The case that made this necessary: white art through 20%-opaque glass leaves
        // near-white text at roughly 1.35:1.
        val unscrimmed = ContrastScrim.contrastFor(0f, text, Color.White, glass)
        assertTrue("expected white artwork to start illegible, was $unscrimmed", unscrimmed < 4.5)

        assertTrue(
            "after scrim, contrast was ${contrastAfterScrim(Color.White)}",
            contrastAfterScrim(Color.White) >= ContrastScrim.BODY_TEXT_TARGET
        )
    }

    @Test
    fun `black artwork needs no scrim at all`() {
        // Dark text-on-dark is already fine; adding dimming would cost contrast elsewhere
        // and darken the design for no benefit.
        assertEquals(0f, ContrastScrim.requiredAlpha(text, Color.Black, glass), 0.001f)
    }

    @Test
    fun `mid grey artwork is handled`() {
        val grey = Color(0.5f, 0.5f, 0.5f)
        assertTrue(
            "after scrim, contrast was ${contrastAfterScrim(grey)}",
            contrastAfterScrim(grey) >= ContrastScrim.BODY_TEXT_TARGET
        )
    }

    @Test
    fun `every backdrop across the full luminance range ends up legible`() {
        // Album art is arbitrary, so the guarantee has to hold across the range rather than
        // at a few sampled colours.
        for (step in 0..20) {
            val value = step / 20f
            val backdrop = Color(value, value, value)
            val ratio = contrastAfterScrim(backdrop)
            assertTrue(
                "backdrop luminance $value ended at $ratio, below target",
                ratio >= ContrastScrim.BODY_TEXT_TARGET
            )
        }
    }

    @Test
    fun `saturated artwork colours are handled too`() {
        // Bright yellow and cyan are the high-luminance colours that catch a scrim tuned
        // only against greyscale.
        for (backdrop in listOf(Color.Yellow, Color.Cyan, Color.Magenta, Color.Red)) {
            val ratio = contrastAfterScrim(backdrop)
            assertTrue("$backdrop ended at $ratio, below target", ratio >= ContrastScrim.BODY_TEXT_TARGET)
        }
    }

    @Test
    fun `a brighter backdrop never needs less scrim than a darker one`() {
        // Monotonicity: if this breaks, the bisection can settle on a wrong answer.
        var previous = 0f
        for (step in 0..20) {
            val value = step / 20f
            val alpha = ContrastScrim.requiredAlpha(text, Color(value, value, value), glass)
            assertTrue(
                "scrim decreased from $previous to $alpha as the backdrop brightened",
                alpha >= previous - 0.001f
            )
            previous = alpha
        }
    }

    @Test
    fun `the scrim used is the smallest one that works`() {
        // An over-strong scrim would wash out the artwork atmosphere the design system wants
        // to keep, so the value must be minimal, not merely sufficient.
        val alpha = ContrastScrim.requiredAlpha(text, Color.White, glass)
        assertTrue("expected a partial scrim, got $alpha", alpha > 0f && alpha <= 1f)

        val justUnder = (alpha - 0.05f).coerceAtLeast(0f)
        assertTrue(
            "a scrim 0.05 lighter should fail, so $alpha is not minimal",
            ContrastScrim.contrastFor(justUnder, text, Color.White, glass) <
                ContrastScrim.BODY_TEXT_TARGET
        )
    }

    @Test
    fun `white artwork does not force the opaque fallback`() {
        // Glass survives: the fallback is for cases a full scrim cannot rescue, not for
        // ordinary bright artwork.
        assertFalse(ContrastScrim.requiresOpaqueFallback(text, Color.White, glass))
    }

    @Test
    fun `text that matches the scrim colour demands the opaque fallback`() {
        // Black text on a black scrim cannot be rescued by more scrim; the caller must drop
        // translucency rather than ship unreadable text.
        assertTrue(ContrastScrim.requiresOpaqueFallback(Color.Black, Color.White, glass))
    }

    @Test
    fun `large text needs less scrim than body text`() {
        val body = ContrastScrim.requiredAlpha(text, Color.White, glass, ContrastScrim.BODY_TEXT_TARGET)
        val large = ContrastScrim.requiredAlpha(text, Color.White, glass, ContrastScrim.LARGE_TEXT_TARGET)

        assertTrue("large-text scrim $large should not exceed body-text scrim $body", large <= body)
    }

    @Test
    fun `compositing a fully transparent layer leaves the backdrop unchanged`() {
        val result = ContrastScrim.composite(Color.Black.copy(alpha = 0f), Color.White)

        assertEquals(1f, result.red, 0.001f)
        assertEquals(1f, result.blue, 0.001f)
    }

    @Test
    fun `contrast of a colour with itself is one to one`() {
        assertEquals(1.0, ContrastScrim.contrastRatio(Color.White, Color.White), 0.001)
    }

    @Test
    fun `black on white is the maximum ratio`() {
        assertEquals(21.0, ContrastScrim.contrastRatio(Color.Black, Color.White), 0.1)
    }
}
