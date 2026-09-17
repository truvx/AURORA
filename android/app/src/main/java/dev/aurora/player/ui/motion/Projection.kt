package dev.aurora.player.ui.motion

import kotlin.math.abs

/**
 * Momentum projection and soft boundaries.
 *
 * docs/AURORA_MOTION_SPEC.md's gesture model asks for direct manipulation that uses
 * "velocity only at release, and settle to a deterministic target". These are the two
 * functions that make that concrete, and they are deliberately the same maths the web
 * client uses in lib/motion/projection.ts, so a flick behaves the same on both.
 *
 * Pure and frame-independent, so the behaviour is unit-testable without a device: what
 * matters here is the decision a gesture leads to, not how it was drawn.
 */
object Projection {

    /** Normal scroll feel. Lower values stop sooner. */
    const val DECELERATION_RATE = 0.998f

    /**
     * Where a flick would come to rest if it decelerated freely from here.
     *
     * The exponential-decay form used by scroll views, not the textbook `v^2 / 2a`. The two
     * disagree, and only this one matches how a real flick settles.
     *
     * @param initialVelocity pixels per second at release
     * @return distance travelled past the release point, in pixels
     */
    fun project(initialVelocity: Float, decelerationRate: Float = DECELERATION_RATE): Float =
        (initialVelocity / 1000f) * decelerationRate / (1f - decelerationRate)

    /** The candidate nearest [value]; [value] itself when there are none. */
    fun nearestSnapPoint(value: Float, points: List<Float>): Float =
        points.minByOrNull { abs(it - value) } ?: value

    /**
     * The resting state a gesture is heading for.
     *
     * Snapping to whatever is nearest the *release point* throws away the speed the user
     * applied: a hard flick and a slow drag ending in the same place would do the same
     * thing, which is exactly what a physical object would not do. Projecting first is what
     * makes a short, fast flick carry all the way.
     */
    fun projectedSnapTarget(
        position: Float,
        velocity: Float,
        snapPoints: List<Float>,
        decelerationRate: Float = DECELERATION_RATE
    ): Float = nearestSnapPoint(position + project(velocity, decelerationRate), snapPoints)

    /**
     * Progressive resistance past a boundary.
     *
     * A hard stop reads as frozen - the user cannot tell "there is nothing more here" from
     * "the app stopped responding". Continuous resistance answers that while still refusing
     * to go further.
     *
     * @param overshoot how far past the boundary the pointer has travelled
     * @param dimension the scale of the movement, which sets how hard it pulls back
     */
    fun rubberband(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float {
        if (dimension <= 0f) return 0f
        return (overshoot * dimension * constant) / (dimension + constant * abs(overshoot))
    }

    /** Clamps to [min]..[max] with rubber-banding at each end rather than a hard stop. */
    fun clampWithRubberband(
        value: Float,
        min: Float,
        max: Float,
        dimension: Float,
        constant: Float = 0.55f
    ): Float = when {
        value < min -> min - rubberband(min - value, dimension, constant)
        value > max -> max + rubberband(value - max, dimension, constant)
        else -> value
    }
}
