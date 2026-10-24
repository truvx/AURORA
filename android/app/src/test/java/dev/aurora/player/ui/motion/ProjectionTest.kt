package dev.aurora.player.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gesture model in docs/AURORA_MOTION_SPEC.md turns on these two decisions: where a
 * flick is heading, and how a boundary resists. Both are pure, so they are worth testing
 * here rather than on a device - what matters is the decision a gesture leads to, and that
 * is exactly what an instrumented test would find hardest to pin down.
 *
 * These mirror web/lib/motion/__tests__/projection.test.ts on purpose: the two clients
 * should agree about what a flick means.
 */
class ProjectionTest {

    @Test
    fun `no velocity projects nowhere`() {
        assertEquals(0f, Projection.project(0f), 0.0001f)
    }

    @Test
    fun `a faster flick projects further`() {
        assertTrue(Projection.project(1000f) > Projection.project(500f))
    }

    @Test
    fun `projection keeps the direction of travel`() {
        assertTrue(Projection.project(-800f) < 0f)
        assertTrue(Projection.project(800f) > 0f)
    }

    @Test
    fun `projection is symmetric about zero`() {
        assertEquals(-Projection.project(640f), Projection.project(-640f), 0.001f)
    }

    @Test
    fun `projection follows the deceleration curve rather than the textbook formula`() {
        // v^2/(2a) and the exponential-decay form disagree, and only this one matches how a
        // real flick settles. At the default rate a 1000 px/s flick carries about 499 px.
        assertEquals(499f, Projection.project(1000f), 1f)
    }

    @Test
    fun `a snappier deceleration rate stops sooner`() {
        assertTrue(Projection.project(1000f, 0.99f) < Projection.project(1000f, 0.998f))
    }

    @Test
    fun `nearest snap point picks the closest candidate`() {
        assertEquals(0f, Projection.nearestSnapPoint(30f, listOf(0f, 100f)), 0.0001f)
        assertEquals(100f, Projection.nearestSnapPoint(70f, listOf(0f, 100f)), 0.0001f)
    }

    @Test
    fun `nearest snap point returns the value when there is nothing to snap to`() {
        assertEquals(42f, Projection.nearestSnapPoint(42f, emptyList()), 0.0001f)
    }

    @Test
    fun `a slow release falls back to the nearest state`() {
        // Barely moving and only a third of the way across: a release, not a throw.
        assertEquals(0f, Projection.projectedSnapTarget(-40f, 0f, listOf(0f, -120f)), 0.0001f)
    }

    @Test
    fun `a short flick still carries all the way`() {
        // The point of projecting. Same position as above, but thrown - judging on position
        // alone would snap it back, which is exactly what makes a flick feel wrong.
        assertEquals(
            -120f,
            Projection.projectedSnapTarget(-40f, -900f, listOf(0f, -120f)),
            0.0001f
        )
    }

    @Test
    fun `a late reversal is respected`() {
        // Velocity decides, not position: the user changed their mind near the end.
        assertEquals(
            0f,
            Projection.projectedSnapTarget(-110f, 900f, listOf(0f, -120f)),
            0.0001f
        )
    }

    @Test
    fun `rubberband does not resist inside the boundary`() {
        assertEquals(0f, Projection.rubberband(0f, 400f), 0.0001f)
    }

    @Test
    fun `rubberband gives back less than was asked for`() {
        assertTrue(Projection.rubberband(100f, 400f) < 100f)
    }

    @Test
    fun `rubberband keeps responding rather than stopping dead`() {
        // Every further pixel of drag must still move the surface some amount, or it reads
        // as frozen - which is the thing rubber-banding exists to avoid.
        val near = Projection.rubberband(50f, 400f)
        val far = Projection.rubberband(200f, 400f)

        assertTrue(far > near)
        assertTrue(far - near < 150f)
    }

    @Test
    fun `rubberband stays bounded however hard the drag`() {
        assertTrue(Projection.rubberband(100_000f, 400f) < 400f)
    }

    @Test
    fun `rubberband keeps the sign of the overshoot`() {
        assertTrue(Projection.rubberband(-100f, 400f) < 0f)
    }

    @Test
    fun `rubberband returns nothing for an unmeasured surface`() {
        // Guards a divide-by-zero during the first layout pass.
        assertEquals(0f, Projection.rubberband(100f, 0f), 0.0001f)
    }

    @Test
    fun `clamping leaves values inside the range alone`() {
        assertEquals(50f, Projection.clampWithRubberband(50f, 0f, 100f, 400f), 0.0001f)
    }

    @Test
    fun `clamping softens an overshoot at each end`() {
        val above = Projection.clampWithRubberband(160f, 0f, 100f, 400f)
        val below = Projection.clampWithRubberband(-60f, 0f, 100f, 400f)

        assertTrue(above > 100f && above < 160f)
        assertTrue(below < 0f && below > -60f)
    }

    @Test
    fun `clamping is continuous at the boundary`() {
        // A step at the edge would be felt as a snag exactly where the surface should feel
        // smoothest.
        assertEquals(100f, Projection.clampWithRubberband(100.001f, 0f, 100f, 400f), 0.01f)
    }
}
