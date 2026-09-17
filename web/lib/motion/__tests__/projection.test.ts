import { describe, expect, it } from "vitest";
import {
  clampWithRubberband,
  nearestSnapPoint,
  project,
  projectedSnapTarget,
  rubberband,
} from "../projection";

describe("project", () => {
  it("returns nothing for no velocity", () => {
    expect(project(0)).toBe(0);
  });

  it("projects further the faster the flick", () => {
    expect(project(1000)).toBeGreaterThan(project(500));
  });

  it("keeps the sign of the velocity", () => {
    expect(project(-800)).toBeLessThan(0);
    expect(project(800)).toBeGreaterThan(0);
  });

  it("is symmetric about zero", () => {
    expect(project(-640)).toBeCloseTo(-project(640), 6);
  });

  it("travels less with a snappier deceleration rate", () => {
    // 0.99 is the snappier setting; it should stop sooner than the 0.998 scroll feel.
    expect(Math.abs(project(1000, 0.99))).toBeLessThan(Math.abs(project(1000, 0.998)));
  });

  it("matches the deceleration curve rather than the textbook formula", () => {
    // v^2/(2a) and the exponential-decay form disagree, and only this one matches how
    // native scrolling, carousels and sheets actually come to rest. At the default rate a
    // 1000 px/s flick carries about 499 px.
    expect(project(1000)).toBeCloseTo(499, 0);
  });
});

describe("nearestSnapPoint", () => {
  it("picks the closest candidate", () => {
    expect(nearestSnapPoint(30, [0, 100])).toBe(0);
    expect(nearestSnapPoint(70, [0, 100])).toBe(100);
  });

  it("returns the value unchanged when there is nothing to snap to", () => {
    expect(nearestSnapPoint(42, [])).toBe(42);
  });

  it("handles unsorted candidates", () => {
    expect(nearestSnapPoint(90, [100, 0, 50])).toBe(100);
  });
});

describe("projectedSnapTarget", () => {
  it("snaps to the near state when released slowly", () => {
    // Barely moving and only a third of the way: this is a release, not a throw.
    expect(projectedSnapTarget(30, 0, [0, 300])).toBe(0);
  });

  it("carries a small movement all the way when it is flicked", () => {
    // The point of projection. Same position as above, but thrown - a position-only rule
    // would snap it back, which is exactly the flick feeling wrong.
    expect(projectedSnapTarget(30, 900, [0, 300])).toBe(300);
  });

  it("reverses on a flick back even from most of the way across", () => {
    // Velocity decides, not position: the user changed their mind late in the gesture.
    expect(projectedSnapTarget(270, -900, [0, 300])).toBe(0);
  });

  it("uses position when velocity is negligible", () => {
    expect(projectedSnapTarget(280, 5, [0, 300])).toBe(300);
  });
});

describe("rubberband", () => {
  it("does not resist inside the boundary", () => {
    expect(rubberband(0, 400)).toBe(0);
  });

  it("gives less movement than was asked for", () => {
    // Resistance: the surface still follows, but not all the way.
    expect(rubberband(100, 400)).toBeLessThan(100);
  });

  it("resists progressively rather than stopping dead", () => {
    // Each further pixel of drag must still produce *some* movement, or the surface reads
    // as frozen - which is the thing rubber-banding exists to avoid.
    const near = rubberband(50, 400);
    const far = rubberband(200, 400);

    expect(far).toBeGreaterThan(near);
    expect(far - near).toBeLessThan(150);
  });

  it("stays bounded no matter how far the drag goes", () => {
    // The asymptote is what keeps an aggressive drag from flinging a surface off-screen.
    expect(rubberband(100_000, 400)).toBeLessThan(400);
  });

  it("keeps the sign of the overshoot", () => {
    expect(rubberband(-100, 400)).toBeLessThan(0);
  });

  it("resists harder on a smaller surface", () => {
    expect(rubberband(100, 200)).toBeLessThan(rubberband(100, 800));
  });

  it("returns no movement for a zero-sized surface", () => {
    // Guards a divide-by-zero during first layout, before the element has been measured.
    expect(rubberband(100, 0)).toBe(0);
  });
});

describe("clampWithRubberband", () => {
  it("leaves values inside the range alone", () => {
    expect(clampWithRubberband(50, 0, 100, 400)).toBe(50);
  });

  it("softens an overshoot past the top", () => {
    const result = clampWithRubberband(160, 0, 100, 400);

    expect(result).toBeGreaterThan(100);
    expect(result).toBeLessThan(160);
  });

  it("softens an overshoot past the bottom", () => {
    const result = clampWithRubberband(-60, 0, 100, 400);

    expect(result).toBeLessThan(0);
    expect(result).toBeGreaterThan(-60);
  });

  it("is continuous at the boundary", () => {
    // A step at the edge would be felt as a snag exactly where the surface should feel
    // smoothest.
    expect(clampWithRubberband(100.001, 0, 100, 400)).toBeCloseTo(100, 2);
  });
});
