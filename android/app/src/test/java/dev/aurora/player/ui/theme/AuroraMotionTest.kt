package dev.aurora.player.ui.theme

import androidx.compose.animation.core.Spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The spring families are declared in the two terms that decide how a spring feels -
 * response and damping ratio - and converted to the stiffness Compose wants. These check
 * the conversion and the character of each family, because a stiffness picked by hand is
 * impossible to review and easy to get an order of magnitude wrong.
 */
class AuroraMotionTest {

    private val motion = AuroraMotionTokens

    @Test
    fun `stiffness is the square of the natural frequency`() {
        // response r means an undamped natural frequency of 2*pi/r, and Compose's stiffness
        // is that frequency squared for a unit mass.
        val expected = (2.0 * Math.PI / 0.4).let { (it * it).toFloat() }

        assertEquals(expected, stiffnessFor(0.4f), 0.01f)
    }

    @Test
    fun `halving the response quadruples the stiffness`() {
        // The relationship that makes hand-picked stiffness numbers so misleading: twice as
        // fast is four times as stiff, not twice.
        assertEquals(4f, stiffnessFor(0.2f) / stiffnessFor(0.4f), 0.001f)
    }

    @Test
    fun `a shorter response is stiffer`() {
        assertTrue(stiffnessFor(0.2f) > stiffnessFor(0.6f))
    }

    @Test
    fun `a non-positive response is rejected`() {
        // Zero would divide by zero and a negative response is meaningless; both would
        // surface far away from here as a spring that never settles.
        for (bad in listOf(0f, -0.3f)) {
            try {
                stiffnessFor(bad)
                throw AssertionError("expected $bad to be rejected")
            } catch (expected: IllegalArgumentException) {
                // as intended
            }
        }
    }

    @Test
    fun `controls settle faster than ambient motion`() {
        // Controls respond quickly; ambient artwork moves slowly. Stiffness is inverse to
        // response, so the ordering inverts.
        assertTrue(motion.springResponsiveStiffness > motion.springStandardStiffness)
        assertTrue(motion.springStandardStiffness > motion.springSoftStiffness)
    }

    @Test
    fun `only drag release bounces`() {
        // Overshoot belongs to motion a gesture put momentum into. A surface that merely
        // repositioned itself and then bounced reads as sloppy rather than physical.
        assertEquals(Spring.DampingRatioNoBouncy, motion.springResponsiveDamping, 0.0001f)
        assertEquals(Spring.DampingRatioNoBouncy, motion.springStandardDamping, 0.0001f)
        assertEquals(Spring.DampingRatioNoBouncy, motion.springSoftDamping, 0.0001f)
        assertTrue(motion.springSettleDamping < Spring.DampingRatioNoBouncy)
    }

    @Test
    fun `durations are ordered from instant to ambient`() {
        assertTrue(motion.durationInstant < motion.durationQuick)
        assertTrue(motion.durationQuick < motion.durationStandard)
        assertTrue(motion.durationStandard < motion.durationEmphasis)
        assertTrue(motion.durationEmphasis < motion.durationAmbient)
    }
}
