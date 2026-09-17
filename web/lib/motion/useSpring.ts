"use client";

import { useCallback, useEffect, useRef } from "react";
import { SPRING, Spring, SpringOptions, driveSpring, prefersReducedMotion } from "./spring";

export interface SpringControls {
  /** Animates to a target, optionally handing off a gesture's release velocity. */
  readonly to: (target: number, velocity?: number) => void;
  /** Places the value under direct pointer control, with no animation. */
  readonly track: (value: number, velocity?: number) => void;
  /** Cuts straight to a value, discarding momentum. */
  readonly jump: (value: number) => void;
  /** The current on-screen value, for starting a new gesture from where motion had got to. */
  readonly current: () => number;
}

/**
 * A spring whose value is written straight to the DOM.
 *
 * Deliberately not React state. At 60-120 Hz a state update per frame would re-render the
 * subtree on every frame of every animation; `apply` writes to the element instead, so the
 * cost is one style write per frame and React is not involved in the motion at all.
 *
 * Under reduced motion, targets are applied immediately rather than sprung. The state still
 * changes and the component still updates - what goes away is the travel between states.
 */
export function useSpringValue(
  initialValue: number,
  apply: (value: number) => void,
  options: SpringOptions = SPRING.reposition
): SpringControls {
  const spring = useRef<Spring>(undefined as unknown as Spring);
  if (spring.current === undefined) {
    spring.current = new Spring(initialValue, options);
  }

  // Held in a ref so a caller passing an inline function does not restart the animation on
  // every render.
  const applyRef = useRef(apply);
  useEffect(() => {
    applyRef.current = apply;
  }, [apply]);

  const cancel = useRef<(() => void) | undefined>(undefined);

  // An animation that outlives its component keeps calling into a dead tree and keeps the
  // display awake for nothing.
  useEffect(() => () => cancel.current?.(), []);

  const start = useCallback(() => {
    cancel.current?.();
    cancel.current = driveSpring(spring.current, (value) => applyRef.current(value));
  }, []);

  const to = useCallback(
    (target: number, velocity?: number) => {
      if (prefersReducedMotion()) {
        cancel.current?.();
        spring.current.jumpTo(target);
        applyRef.current(target);
        return;
      }
      // Re-aims from the live value and carries velocity through, so grabbing a moving
      // surface and reversing it stays continuous instead of snapping.
      spring.current.setTarget(target, velocity);
      start();
    },
    [start]
  );

  const track = useCallback((value: number, velocity = 0) => {
    cancel.current?.();
    spring.current.track(value, velocity);
    applyRef.current(value);
  }, []);

  const jump = useCallback((value: number) => {
    cancel.current?.();
    spring.current.jumpTo(value);
    applyRef.current(value);
  }, []);

  const current = useCallback(() => spring.current.value, []);

  return { to, track, jump, current };
}
