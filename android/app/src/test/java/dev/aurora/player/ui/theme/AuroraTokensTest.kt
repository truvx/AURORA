package dev.aurora.player.ui.theme

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
        assertEquals(40f, type.display.fontSize.value)
        assertEquals(30f, type.headline.fontSize.value)
        assertEquals(22f, type.title.fontSize.value)
        assertEquals(16f, type.body.fontSize.value)
        assertEquals(14f, type.label.fontSize.value)
        assertEquals(12f, type.caption.fontSize.value)
    }
}
