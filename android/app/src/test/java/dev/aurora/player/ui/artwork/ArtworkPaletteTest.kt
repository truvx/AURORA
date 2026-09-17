package dev.aurora.player.ui.artwork

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Album art is arbitrary, so these assert properties that must hold for any cover rather
 * than pinning the output for particular images: that the colour returned belongs to the
 * artwork, that it always lands in a range a backdrop can occupy, and that images with
 * nothing to say produce nothing rather than a misleading colour.
 */
class ArtworkPaletteTest {

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun image(vararg pixels: Int) = pixels

    private fun fill(count: Int, pixel: Int) = IntArray(count) { pixel }

    // --- identifying the colour ---------------------------------------------------------

    @Test
    fun `a solid colour resolves to that hue`() {
        val result = ArtworkPalette.dominantColor(fill(64, argb(200, 40, 40)))

        assertNotNull(result)
        val (hue, _, _) = ArtworkPalette.toHsl(result!!)
        // Red sits near 0/360; either end is the same hue.
        assertTrue("expected a red hue, got $hue", hue < 20f || hue > 340f)
    }

    @Test
    fun `a colour surrounded by black is still found`() {
        // Most covers are mostly dark. Counting black would resolve nearly all of them to
        // the same backdrop and the atmosphere would stop meaning anything.
        val pixels = fill(900, argb(0, 0, 0)) + fill(100, argb(30, 90, 210))

        val result = ArtworkPalette.dominantColor(pixels)

        assertNotNull(result)
        val (hue, _, _) = ArtworkPalette.toHsl(result!!)
        assertTrue("expected a blue hue, got $hue", hue in 190f..250f)
    }

    @Test
    fun `a colour surrounded by white is still found`() {
        val pixels = fill(900, argb(255, 255, 255)) + fill(100, argb(20, 160, 80))

        val result = ArtworkPalette.dominantColor(pixels)

        assertNotNull(result)
        val (hue, _, _) = ArtworkPalette.toHsl(result!!)
        assertTrue("expected a green hue, got $hue", hue in 100f..180f)
    }

    @Test
    fun `a vivid accent beats a larger dull region`() {
        // A cover that is mostly muted grey with one strong accent takes its identity from
        // the accent - that is what someone looking at it would say the record's colour is.
        val pixels = fill(600, argb(122, 120, 124)) + fill(400, argb(230, 60, 160))

        val result = ArtworkPalette.dominantColor(pixels)

        assertNotNull(result)
        val (hue, saturation, _) = ArtworkPalette.toHsl(result!!)
        assertTrue("expected a saturated result, got $saturation", saturation > 0.2f)
        assertTrue("expected a magenta hue, got $hue", hue in 290f..350f)
    }

    @Test
    fun `overwhelming dominance is not overturned by a tiny vivid speck`() {
        // The vividness weighting tips ties; it must not let a handful of pixels rename the
        // whole cover.
        val pixels = fill(5000, argb(40, 90, 200)) + fill(5, argb(255, 0, 255))

        val result = ArtworkPalette.dominantColor(pixels)

        assertNotNull(result)
        val (hue, _, _) = ArtworkPalette.toHsl(result!!)
        assertTrue("expected the dominant blue, got $hue", hue in 190f..250f)
    }

    @Test
    fun `shading across one region resolves to a single colour`() {
        // Quantisation exists for this: a gradient over one area is one colour to the eye,
        // and should not split into buckets that each lose to a flat region elsewhere.
        val shaded = IntArray(300) { i -> argb(180 + i % 8, 60 + i % 8, 60 + i % 8) }
        val flat = fill(200, argb(70, 70, 72))

        val result = ArtworkPalette.dominantColor(shaded + flat)

        assertNotNull(result)
        val (hue, _, _) = ArtworkPalette.toHsl(result!!)
        assertTrue("expected the shaded red region to win, got $hue", hue < 20f || hue > 340f)
    }

    // --- nothing to say -----------------------------------------------------------------

    @Test
    fun `an empty image has no colour`() {
        assertNull(ArtworkPalette.dominantColor(IntArray(0)))
    }

    @Test
    fun `a fully transparent image has no colour`() {
        // Decoding can hand back an empty bitmap; inventing a colour from it would put a
        // wash on screen that belongs to no artwork at all.
        assertNull(ArtworkPalette.dominantColor(fill(100, argb(200, 100, 50, a = 0))))
    }

    @Test
    fun `pure black and white artwork has no dominant colour`() {
        val pixels = fill(500, argb(0, 0, 0)) + fill(500, argb(255, 255, 255))

        assertNull(ArtworkPalette.dominantColor(pixels))
    }

    @Test
    fun `mid grey artwork still resolves rather than failing`() {
        // Greyscale is not "nothing" - it is a legitimate cover, and it should produce a
        // neutral backdrop rather than being discarded.
        val result = ArtworkPalette.dominantColor(fill(200, argb(128, 128, 128)))

        assertNotNull(result)
        val (_, saturation, _) = ArtworkPalette.toHsl(result!!)
        assertTrue("expected a near-neutral result, got $saturation", saturation < 0.1f)
    }

    // --- staying inside what a backdrop can be ------------------------------------------

    @Test
    fun `saturated artwork is clamped rather than washing the screen in neon`() {
        val result = ArtworkPalette.dominantColor(fill(100, argb(255, 0, 0)))

        assertNotNull(result)
        val (_, saturation, _) = ArtworkPalette.toHsl(result!!)
        // Tolerance is one 8-bit step: Color quantises channels to 1/255, so a value clamped
        // to exactly the ceiling reads back a fraction above it. Anything tighter is
        // asserting a precision the colour type does not have.
        assertTrue(
            "saturation $saturation exceeded the ceiling ${ArtworkPalette.MAX_CHROMA}",
            saturation <= ArtworkPalette.MAX_CHROMA + 0.005f
        )
    }

    @Test
    fun `every plausible colour lands inside the lightness band`() {
        // The band is what leaves room for content above the backdrop. It has to hold for
        // arbitrary artwork, not just the samples above.
        for (r in 0..255 step 51) {
            for (g in 0..255 step 51) {
                for (b in 0..255 step 51) {
                    val result = ArtworkPalette.dominantColor(fill(40, argb(r, g, b)))
                        ?: continue
                    val (_, _, lightness) = ArtworkPalette.toHsl(result)
                    assertTrue(
                        "rgb($r,$g,$b) produced lightness $lightness, outside the band",
                        lightness >= ArtworkPalette.MIN_LIGHTNESS - 0.001f &&
                            lightness <= ArtworkPalette.MAX_LIGHTNESS + 0.001f
                    )
                }
            }
        }
    }

    @Test
    fun `clamping preserves hue`() {
        // Saturation and lightness are negotiable; hue is the part that carries the
        // artwork's identity, and changing it would make the backdrop belong to nothing.
        val vivid = Color(1f, 0.85f, 0f)
        val (originalHue, _, _) = ArtworkPalette.toHsl(vivid)

        val (clampedHue, _, _) = ArtworkPalette.toHsl(ArtworkPalette.clampForAtmosphere(vivid))

        assertEquals(originalHue, clampedHue, 1f)
    }

    @Test
    fun `clamping a colour already in range leaves it alone`() {
        val inRange = ArtworkPalette.fromHsl(hue = 210f, saturation = 0.3f, lightness = 0.4f)

        val clamped = ArtworkPalette.clampForAtmosphere(inRange)

        assertEquals(inRange.red, clamped.red, 0.01f)
        assertEquals(inRange.green, clamped.green, 0.01f)
        assertEquals(inRange.blue, clamped.blue, 0.01f)
    }

    // --- colour space round trip --------------------------------------------------------

    @Test
    fun `hsl survives a round trip`() {
        for (hue in 0..350 step 30) {
            val original = ArtworkPalette.fromHsl(hue.toFloat(), 0.6f, 0.45f)
            val (h, s, l) = ArtworkPalette.toHsl(original)

            assertEquals("hue $hue", hue.toFloat(), h, 1f)
            assertEquals("saturation at hue $hue", 0.6f, s, 0.02f)
            assertEquals("lightness at hue $hue", 0.45f, l, 0.02f)
        }
    }

    @Test
    fun `a fully desaturated colour reports no saturation`() {
        val (_, saturation, lightness) = ArtworkPalette.toHsl(Color(0.5f, 0.5f, 0.5f))

        assertEquals(0f, saturation, 0.001f)
        // 0.5 is not 8-bit representable: it stores as 128/255, so this reads 0.50196.
        assertEquals(0.5f, lightness, 0.005f)
    }
}
