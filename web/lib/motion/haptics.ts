/**
 * Haptic feedback.
 *
 * Three rules decide whether a haptic earns its place:
 *
 *  Causality  - it must be obvious what caused it, so it fires on the causal event itself
 *               (the gesture committing, the item landing), not on the animation finishing.
 *  Harmony    - the visual and the haptic land on the same frame. A haptic that trails the
 *               motion reads as a second, unrelated event.
 *  Utility    - only meaningful moments. Feedback on everything trains people to ignore all
 *               of it, which costs the moments that actually mattered.
 *
 * Which is why there is no `tap()` here. A haptic on every button press is exactly the
 * over-feedback that makes the useful ones stop registering.
 */

type HapticWeight = "commit" | "select" | "warn";

/** Short, distinguishable patterns. Long buzzes read as errors whatever caused them. */
const PATTERNS: Record<HapticWeight, number | number[]> = {
  /** A gesture reached its destination: a sheet settling open, a flick landing. */
  commit: 12,
  /** A discrete value changed under a continuing gesture, e.g. crossing a snap point. */
  select: 6,
  /** Something was refused. Two pulses, because one reads as success. */
  warn: [8, 40, 8],
};

/**
 * Fires a haptic, where the platform has one and the user has not asked for less motion.
 *
 * Reduced motion covers this too: someone who has asked the interface to stop moving has
 * not asked it to start buzzing instead.
 */
export function haptic(weight: HapticWeight): void {
  if (typeof navigator === "undefined" || typeof navigator.vibrate !== "function") return;
  if (typeof window !== "undefined" && window.matchMedia) {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  }

  try {
    navigator.vibrate(PATTERNS[weight]);
  } catch {
    // Blocked by permissions policy, or the page has never been interacted with. Nothing
    // here is load-bearing, so a failed buzz must never surface anywhere.
  }
}
