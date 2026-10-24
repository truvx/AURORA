package dev.aurora.player.ui.theme

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Validates AURORA design token values match the specification.
 */
class AuroraTokensTest {

    @Test
    fun `light and dark colors are distinct palettes`() {
        assertNotEquals(
            AuroraLightColors.backgroundPrimary,
            AuroraDarkColors.backgroundPrimary,
        )
        assertNotEquals(
            AuroraLightColors.textPrimary,
            AuroraDarkColors.textPrimary,
        )
    }

    @Test
    fun `accent colors are present in both themes`() {
        assertNotEquals(
            AuroraLightColors.accentPrimary,
            AuroraLightColors.backgroundPrimary,
        )
        assertNotEquals(
            AuroraDarkColors.accentPrimary,
            AuroraDarkColors.backgroundPrimary,
        )
    }

    @Test
    fun `spacing follows 4-unit rhythm`() {
        val spacing = AuroraSpacingTokens
        assertEquals(4, spacing.space1.value.toInt())
        assertEquals(8, spacing.space2.value.toInt())
        assertEquals(12, spacing.space3.value.toInt())
        assertEquals(16, spacing.space4.value.toInt())
        assertEquals(20, spacing.space5.value.toInt())
        assertEquals(24, spacing.space6.value.toInt())
        assertEquals(32, spacing.space8.value.toInt())
        assertEquals(40, spacing.space10.value.toInt())
        assertEquals(48, spacing.space12.value.toInt())
        assertEquals(64, spacing.space16.value.toInt())
    }

    @Test
    fun `motion durations follow spec ordering`() {
        val motion = AuroraMotionTokens
        assert(motion.durationInstant < motion.durationQuick)
        assert(motion.durationQuick < motion.durationStandard)
        assert(motion.durationStandard < motion.durationEmphasis)
        assert(motion.durationEmphasis < motion.durationAmbient)
    }

    @Test
    fun `motion duration values match spec`() {
        val motion = AuroraMotionTokens
        assertEquals(80, motion.durationInstant)
        assertEquals(140, motion.durationQuick)
        assertEquals(220, motion.durationStandard)
        assertEquals(360, motion.durationEmphasis)
        assertEquals(600, motion.durationAmbient)
    }

    @Test
    fun `typography sizes match spec`() {
        val type = AuroraTypographyTokens
        assertEquals(34f, type.display.fontSize.value)
        assertEquals(26f, type.headline.fontSize.value)
        assertEquals(20f, type.title.fontSize.value)
        assertEquals(16f, type.body.fontSize.value)
        assertEquals(14f, type.label.fontSize.value)
        assertEquals(12f, type.caption.fontSize.value)
    }

    @Test
    fun `no role asks for a weight the family does not ship`() {
        // AURORA bundles Regular and Bold and nothing between. A role asking for a 500 or
        // a 300 gets a weight the renderer has to invent, which is a smeared Regular - the
        // exact failure docs/TYPOGRAPHY_FINAL.md exists to prevent.
        val shipped = setOf(FontWeight.Normal, FontWeight.Bold)
        val type = AuroraTypographyTokens
        val roles = mapOf(
            "display" to type.display,
            "headline" to type.headline,
            "title" to type.title,
            "body" to type.body,
            "label" to type.label,
            "caption" to type.caption,
            "section" to type.section,
            "numeric" to type.numeric,
            "playerNumeric" to type.playerNumeric,
            "trackTitleLarge" to type.trackTitleLarge,
            "trackTitle" to type.trackTitle,
            "trackArtist" to type.trackArtist,
            "trackArtistCompact" to type.trackArtistCompact,
        )
        for ((name, style) in roles) {
            assertEquals("$name weight", true, style.fontWeight in shipped)
        }
    }

    @Test
    fun `every role is set in the one family`() {
        val type = AuroraTypographyTokens
        val roles = listOf(
            type.display, type.headline, type.title, type.body, type.label, type.caption,
            type.section, type.numeric, type.playerNumeric, type.trackTitleLarge,
            type.trackTitle, type.trackArtist, type.trackArtistCompact,
        )
        for (style in roles) {
            assertEquals(AuroraFontFamily, style.fontFamily)
        }
    }
}
