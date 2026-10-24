import { describe, expect, it } from "vitest";
import { SPRING, Spring } from "../spring";

/**
 * These assert the *shape* of the motion rather than positions at fixed times.
 *
 * Pinning "after 100 ms the value is 47.3" would fail on any parameter change without
 * telling anyone whether the motion got better or worse. What must never change is the
 * behaviour the interface depends on: that a critically damped spring does not overshoot,
 * that velocity is carried through a re-target, and that everything eventually rests.
 */

/**
 * Runs the spring at a fixed frame rate and reports what happened along the way.
 *
 * The step count is computed rather than accumulated: `for (t = 0; t < seconds; t += step)`
 * drifts on floating point and silently runs an extra frame at some frame rates, which
 * makes two rates look like they disagree when only the harness did.
 */
function simulate(spring: Spring, seconds: number, fps = 60) {
  const step = 1 / fps;
  const frames = Math.round(seconds * fps);
  const values: number[] = [spring.value];
  for (let frame = 0; frame < frames; frame += 1) {
    spring.advance(step);
    values.push(spring.value);
  }
  return values;
}

describe("Spring", () => {
  it("starts at the value it was constructed with and stays there untouched", () => {
    const spring = new Spring(42);

    expect(spring.value).toBe(42);
    expect(spring.isSettled).toBe(true);

    spring.advance(1);
    expect(spring.value).toBe(42);
  });

  it("reaches its target and comes to rest", () => {
    const spring = new Spring(0);
    spring.setTarget(100);

    simulate(spring, 2);

    expect(spring.isSettled).toBe(true);
    expect(spring.value).toBeCloseTo(100, 5);
  });

  it("never overshoots when critically damped", () => {
    // The default for anything that did not arrive with momentum. Overshoot on a surface
    // that merely repositioned reads as sloppy rather than physical.
    const spring = new Spring(0, SPRING.reposition);
    spring.setTarget(100);

    const values = simulate(spring, 2);

    expect(Math.max(...values)).toBeLessThanOrEqual(100.0001);
  });

  it("overshoots when under-damped", () => {
    // The sheet family, used only where the gesture itself carried momentum.
    const spring = new Spring(0, SPRING.sheet);
    spring.setTarget(100);

    const values = simulate(spring, 2);

    expect(Math.max(...values)).toBeGreaterThan(100);
    expect(values[values.length - 1]).toBeCloseTo(100, 5);
  });

  it("settles faster with a shorter response", () => {
    const quick = new Spring(0, { dampingRatio: 1, response: 0.2 });
    const slow = new Spring(0, { dampingRatio: 1, response: 0.6 });
    quick.setTarget(100);
    slow.setTarget(100);

    // Sampled mid-flight: run either of them to completion and both read exactly 100,
    // which would pass whatever the response was.
    simulate(quick, 0.1);
    simulate(slow, 0.1);

    expect(quick.value).toBeGreaterThan(slow.value);
  });

  it("carries the handed-off velocity into the motion", () => {
    // The seam between dragging and animating: the animation has to continue at the speed
    // the finger left at, or the release is visible as a stutter.
    const withThrow = new Spring(0, SPRING.sheet);
    const fromRest = new Spring(0, SPRING.sheet);
    withThrow.setTarget(100, 800);
    fromRest.setTarget(100, 0);

    withThrow.advance(1 / 60);
    fromRest.advance(1 / 60);

    expect(withThrow.value).toBeGreaterThan(fromRest.value);
  });

  it("keeps moving away from a target it was thrown past", () => {
    // Thrown hard downward while the target is above: real momentum carries it the wrong
    // way first. A spring that snapped back immediately would be fighting the finger.
    const spring = new Spring(0, SPRING.sheet);
    spring.setTarget(0, -600);

    spring.advance(1 / 60);

    expect(spring.value).toBeLessThan(0);
  });

  it("re-targets from the current value without jumping", () => {
    // Interruption is the whole point. Starting the new motion from the logical value
    // rather than the on-screen one is what produces a visible jump.
    const spring = new Spring(0, SPRING.reposition);
    spring.setTarget(100);
    simulate(spring, 0.15);

    const beforeRetarget = spring.value;
    expect(beforeRetarget).toBeGreaterThan(0);
    expect(beforeRetarget).toBeLessThan(100);

    spring.setTarget(0);

    expect(spring.value).toBeCloseTo(beforeRetarget, 5);
  });

  it("carries velocity through a reversal instead of hard-cutting it", () => {
    // A velocity discontinuity at a reversal is felt as a brick wall. Immediately after
    // re-aiming, the surface must still be travelling the way the finger sent it.
    const spring = new Spring(0, SPRING.sheet);
    spring.setTarget(100);
    simulate(spring, 0.1);

    const travellingUp = spring.velocity;
    expect(travellingUp).toBeGreaterThan(0);

    spring.setTarget(0);

    expect(spring.velocity).toBeCloseTo(travellingUp, 5);
  });

  it("is frame-rate independent", () => {
    // Solved analytically rather than integrated, so a 120 Hz display and a 30 Hz one land
    // in the same place. A step-integrated spring drifts between the two.
    const at30 = new Spring(0, SPRING.sheet);
    const at120 = new Spring(0, SPRING.sheet);
    at30.setTarget(100, 400);
    at120.setTarget(100, 400);

    simulate(at30, 0.2, 30);
    simulate(at120, 0.2, 120);

    expect(at30.value).toBeCloseTo(at120.value, 1);
  });

  it("does not teleport when a frame delta is enormous", () => {
    // A backgrounded tab resumes with a delta of seconds. Clamping lives in driveSpring,
    // but the maths must stay finite regardless of what it is handed.
    const spring = new Spring(0, SPRING.sheet);
    spring.setTarget(100, 500);
    spring.advance(30);

    expect(Number.isFinite(spring.value)).toBe(true);
    expect(spring.value).toBeCloseTo(100, 5);
  });

  it("jumpTo discards momentum", () => {
    const spring = new Spring(0, SPRING.sheet);
    spring.setTarget(100, 900);
    spring.advance(1 / 60);

    spring.jumpTo(50);

    expect(spring.value).toBe(50);
    expect(spring.velocity).toBe(0);
    expect(spring.isSettled).toBe(true);
  });

  it("track hands position to the pointer while keeping velocity for release", () => {
    const spring = new Spring(0, SPRING.sheet);

    spring.track(37, 450);

    expect(spring.value).toBe(37);
    expect(spring.velocity).toBe(450);
  });

  it("settles from an overdamped spring without crossing the target", () => {
    const spring = new Spring(0, { dampingRatio: 2.5, response: 0.3 });
    spring.setTarget(100);

    const values = simulate(spring, 3);

    expect(Math.max(...values)).toBeLessThanOrEqual(100.0001);
    expect(spring.isSettled).toBe(true);
  });
});
