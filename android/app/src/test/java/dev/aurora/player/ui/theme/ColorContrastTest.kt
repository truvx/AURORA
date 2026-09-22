package dev.aurora.player.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrast of the token pairs the UI actually draws, in both themes.
 *
 * The palette is neutral: colour comes from artwork refracting through the glass, and the
 * primary action is the solid white pill from the playlist reference. An earlier attempt
 * used the reference app's blue as a button fill, which carries white text at 3.45:1 - fine
 * to look at, below AA to read. These pin the pairs so a later colour tweak cannot quietly
 * undo that.
 */
class ColorContrastTest {

    private val body = 4.5
    private val nonText = 3.0

    private fun assertContrast(name: String, fg: Color, bg: Color, minimum: Double) {
        val ratio = ContrastScrim.contrastRatio(fg, bg)
        assertTrue("$name: ${"%.2f".format(ratio)}:1, needs $minimum:1", ratio >= minimum)
    }

    private fun checkTheme(theme: String, colors: AuroraColors) {
        val surfaces = mapOf(
            "canvas" to colors.backgroundPrimary,
            "secondary canvas" to colors.backgroundSecondary,
            "opaque surface" to colors.surfaceOpaqueFallback,
        )
        for ((surfaceName, surface) in surfaces) {
            assertContrast("$theme textPrimary on $surfaceName", colors.textPrimary, surface, body)
            assertContrast("$theme textSecondary on $surfaceName", colors.textSecondary, surface, body)
            // Links, the selected tab label and "See all" are drawn in the accent as text.
            assertContrast("$theme accentPrimary on $surfaceName", colors.accentPrimary, surface, body)
            assertContrast("$theme favorite on $surfaceName", colors.favorite, surface, nonText)
        }
        // Filled buttons: "Ask", "Connect", the play button.
        assertContrast("$theme button label", colors.controlOnPrimary, colors.controlPrimary, body)
        // The filled button has to stand out from the canvas it sits on.
        assertContrast("$theme button against canvas", colors.controlPrimary, colors.backgroundPrimary, nonText)
    }

    @Test
    fun `dark theme pairs meet AA`() = checkTheme("dark", AuroraDarkColors)

    @Test
    fun `light theme pairs meet AA`() = checkTheme("light", AuroraLightColors)

    @Test
    fun `a blue button fill with white text would not have passed`() {
        // Recorded so the reason primary actions are a neutral pill, not a blue one, survives
        // the next "just use the blue from the mockup".
        val referenceBlue = Color(0xFF5184FE)
        val ratio = ContrastScrim.contrastRatio(Color.White, referenceBlue)
        assertTrue("expected < 4.5, was $ratio", ratio < body)
    }

    @Test
    fun `the accent stays legible as text, in its quiet role`() {
        // Selection and progress only, but a selected tab label is still text.
        listOf("dark" to AuroraDarkColors, "light" to AuroraLightColors).forEach { (name, colors) ->
            assertContrast("$name accent on canvas", colors.accentPrimary, colors.backgroundPrimary, body)
        }
    }
}
