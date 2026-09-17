/**
 * Momentum projection and soft boundaries.
 *
 * The difference between a flick that feels thrown and one that feels merely released is
 * where you aim it. Snapping to whatever boundary is nearest the point the finger left
 * ignores how fast it was moving - a hard flick and a slow drag ending in the same place
 * do the same thing, which is exactly what a physical object would not do.
 */

/**
 * Where a flick would come to rest if it decelerated freely from here.
 *
 * This is the exponential-decay form used for scroll deceleration, not the textbook
 * v^2 / (2a): the two disagree, and this is the one that matches the feel of every native
 * scroll view, carousel and bottom sheet.
 *
 * @param initialVelocity pixels per second at release
 * @param decelerationRate 0.998 for normal scroll feel, 0.99 for something snappier
 * @returns the distance travelled past the release point, in pixels
 */
export function project(initialVelocity: number, decelerationRate = 0.998): number {
  return ((initialVelocity / 1000) * decelerationRate) / (1 - decelerationRate);
}

/** The candidate nearest `value`. Returns `value` itself when there are no candidates. */
export function nearestSnapPoint(value: number, points: readonly number[]): number {
  if (points.length === 0) return value;
  return points.reduce((best, point) =>
    Math.abs(point - value) < Math.abs(best - value) ? point : best
  );
}

/**
 * Picks the resting state a gesture is heading for.
 *
 * Projects the release velocity forward and snaps to whatever is nearest that projected
 * point, rather than nearest the release point. A small movement with real speed behind it
 * can therefore carry all the way to the far state, which is what makes a flick feel like
 * it throws the surface rather than nudging it.
 */
export function projectedSnapTarget(
  position: number,
  velocity: number,
  snapPoints: readonly number[],
  decelerationRate = 0.998
): number {
  return nearestSnapPoint(position + project(velocity, decelerationRate), snapPoints);
}

/**
 * Progressive resistance past a boundary.
 *
 * A hard stop at the edge reads as frozen - the user cannot tell the difference between
 * "there is nothing more here" and "the app stopped responding". Continuous resistance
 * answers that question while still refusing to go further: the surface keeps following
 * the finger, just less and less the further out it gets.
 *
 * @param overshoot how far past the boundary the pointer has travelled
 * @param dimension the size of the surface being dragged, which sets the scale of the pull
 * @param constant lower resists harder
 */
export function rubberband(overshoot: number, dimension: number, constant = 0.55): number {
  if (dimension <= 0) return 0;
  return (
    (overshoot * dimension * constant) / (dimension + constant * Math.abs(overshoot))
  );
}

/**
 * Clamps to [min, max], but with rubber-banding instead of a hard stop at each end.
 */
export function clampWithRubberband(
  value: number,
  min: number,
  max: number,
  dimension: number,
  constant = 0.55
): number {
  if (value < min) return min - rubberband(min - value, dimension, constant);
  if (value > max) return max + rubberband(value - max, dimension, constant);
  return value;
}
