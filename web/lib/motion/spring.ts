/**
 * Spring motion.
 *
 * A fixed-duration animation cannot respond to new input: once it starts, the only way to
 * react is to cancel it and start another, which shows up as a visible jump and a velocity
 * discontinuity. A spring has no duration - new input just moves the target and the motion
 * stays continuous - which is why anything the user can touch is animated with one of these.
 *
 * Parameterised the way Apple exposes it to designers, in damping ratio and response,
 * rather than in mass/stiffness/damping:
 *
 *   dampingRatio  controls overshoot. 1.0 is critically damped and settles without bounce;
 *                 below 1.0 overshoots, and lower bounces more.
 *   response      how quickly the value reaches the target, in seconds. This is not a
 *                 duration - the settle time emerges from the parameters.
 *
 * Solved analytically rather than integrated step by step. That matters for two reasons:
 * the result does not drift with frame rate, and evaluating at an arbitrary time is exact,
 * so the behaviour can be tested without running an animation loop.
 */

export interface SpringOptions {
  /** 1.0 = no overshoot. Below 1.0 bounces. */
  readonly dampingRatio: number;
  /** Seconds to reach the target. Lower is snappier. */
  readonly response: number;
}

/**
 * Named families, so components pick a behaviour rather than inventing numbers.
 *
 * Bounce is reserved for motion the user's own gesture put momentum into. Overshoot on a
 * menu that merely appeared reads as sloppy; overshoot on a card that was flicked reads as
 * physical. Values are the ones Apple ships for the equivalent interactions.
 */
export const SPRING = {
  /** Controls and anything repositioning on its own. No bounce. */
  reposition: { dampingRatio: 1.0, response: 0.4 },
  /** Quick control feedback. No bounce. */
  control: { dampingRatio: 1.0, response: 0.3 },
  /** Drawers and sheets settling after a drag. Carries the gesture's momentum. */
  sheet: { dampingRatio: 0.8, response: 0.3 },
  /** Rotation, which reads better with a little overshoot. */
  rotation: { dampingRatio: 0.8, response: 0.4 },
} as const satisfies Record<string, SpringOptions>;

/** Below these, the motion is finished as far as the eye is concerned. */
const REST_DISPLACEMENT = 0.01;
const REST_VELOCITY = 0.05;

/**
 * A single scalar under spring motion.
 *
 * Two-dimensional motion uses one of these per axis rather than a single spring on the
 * distance: a shared spring desynchronises as soon as X and Y carry different velocities,
 * and the path visibly bends.
 */
export class Spring {
  private options: SpringOptions;

  /** Displacement from the target, not the absolute value: the maths is all relative. */
  private displacement: number;
  private currentVelocity: number;
  private targetValue: number;

  constructor(initialValue: number, options: SpringOptions = SPRING.reposition) {
    this.options = options;
    this.targetValue = initialValue;
    this.displacement = 0;
    this.currentVelocity = 0;
  }

  get value(): number {
    return this.targetValue + this.displacement;
  }

  get velocity(): number {
    return this.currentVelocity;
  }

  get target(): number {
    return this.targetValue;
  }

  get isSettled(): boolean {
    return (
      Math.abs(this.displacement) < REST_DISPLACEMENT &&
      Math.abs(this.currentVelocity) < REST_VELOCITY
    );
  }

  /**
   * Re-aims at a new target from wherever the motion currently is.
   *
   * The current position and velocity are carried into the new motion rather than reset.
   * That is the whole point: replacing one animation with another at a reversal creates a
   * velocity discontinuity the user feels as a brick wall, and starting from the logical
   * value rather than the on-screen one causes a visible jump.
   *
   * @param velocity overrides the carried velocity - hand it the pointer's release
   *                 velocity so the animation continues at the speed the finger left at,
   *                 with no seam between dragging and animating.
   */
  setTarget(target: number, velocity?: number): void {
    const from = this.value;
    this.currentVelocity = velocity ?? this.currentVelocity;
    this.targetValue = target;
    this.displacement = from - target;
  }

  /** Changes the spring's character mid-flight without disturbing position or velocity. */
  setOptions(options: SpringOptions): void {
    const from = this.value;
    const speed = this.currentVelocity;
    this.options = options;
    this.displacement = from - this.targetValue;
    this.currentVelocity = speed;
  }

  /** Hard cut to a value, discarding momentum. For reduced motion, and for gesture grabs. */
  jumpTo(value: number): void {
    this.targetValue = value;
    this.displacement = 0;
    this.currentVelocity = 0;
  }

  /**
   * Places the value under the user's direct control.
   *
   * Used while a finger is down: position comes from the pointer, and velocity is tracked
   * separately so it can be handed back at release.
   */
  track(value: number, velocity: number): void {
    this.targetValue = value;
    this.displacement = 0;
    this.currentVelocity = velocity;
  }

  /**
   * Advances by a frame.
   *
   * Each step solves forward from the current state rather than from the moment the target
   * was set. The solution is time-invariant, so stepping is exact either way - but carrying
   * a running clock alongside state that is also being rewritten each frame compounds the
   * decay and makes the motion depend on how it was sliced up.
   */
  advance(deltaSeconds: number): void {
    if (deltaSeconds <= 0) return;
    const { displacement, velocity } = this.evaluate(deltaSeconds);
    this.displacement = displacement;
    this.currentVelocity = velocity;

    if (this.isSettled) {
      this.displacement = 0;
      this.currentVelocity = 0;
    }
  }

  /**
   * Closed-form solution of the damped harmonic oscillator, `seconds` on from right now.
   *
   * Exposed for testing: it is the only way to assert the shape of the motion - that a
   * critically damped spring never crosses its target, that an underdamped one does -
   * without depending on a frame clock.
   */
  evaluate(seconds: number): { displacement: number; velocity: number } {
    const { dampingRatio: zeta, response } = this.options;
    const omega = (2 * Math.PI) / response;
    const x0 = this.displacement;
    const v0 = this.currentVelocity;

    if (Math.abs(zeta - 1) < 1e-6) {
      // Critically damped: the fastest return to target that never overshoots it.
      const decay = Math.exp(-omega * seconds);
      const c2 = v0 + omega * x0;
      const displacement = (x0 + c2 * seconds) * decay;
      return {
        displacement,
        velocity: (c2 - omega * (x0 + c2 * seconds)) * decay,
      };
    }

    if (zeta < 1) {
      // Underdamped: overshoots and rings down.
      const damped = omega * Math.sqrt(1 - zeta * zeta);
      const decay = Math.exp(-zeta * omega * seconds);
      const a = x0;
      const b = (v0 + zeta * omega * x0) / damped;
      const cos = Math.cos(damped * seconds);
      const sin = Math.sin(damped * seconds);
      return {
        displacement: decay * (a * cos + b * sin),
        velocity:
          decay *
          (-zeta * omega * (a * cos + b * sin) + damped * (b * cos - a * sin)),
      };
    }

    // Overdamped: two real roots, crawls home without ever crossing.
    const root = omega * Math.sqrt(zeta * zeta - 1);
    const r1 = -zeta * omega + root;
    const r2 = -zeta * omega - root;
    const c1 = (v0 - r2 * x0) / (r1 - r2);
    const c2 = x0 - c1;
    const e1 = Math.exp(r1 * seconds);
    const e2 = Math.exp(r2 * seconds);
    return {
      displacement: c1 * e1 + c2 * e2,
      velocity: c1 * r1 * e1 + c2 * r2 * e2,
    };
  }
}

/** True when the user has asked for reduced motion. Safe to call on the server. */
export function prefersReducedMotion(): boolean {
  if (typeof window === "undefined" || !window.matchMedia) return false;
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/**
 * Drives a spring from the display clock until it settles.
 *
 * requestAnimationFrame is the web's display-synced clock, so frames land when the screen
 * is actually about to draw. Returns a cancel function; callers must call it on unmount,
 * because an animation outliving its element is a leak that also keeps waking the display.
 */
export function driveSpring(
  spring: Spring,
  onFrame: (value: number) => void,
  onSettle?: () => void
): () => void {
  if (typeof window === "undefined") return () => {};

  let frame = 0;
  let last = performance.now();
  let cancelled = false;

  const step = (now: number) => {
    if (cancelled) return;
    // Clamped: a backgrounded tab resumes with a huge delta, which would teleport the
    // value and discard the motion the user was watching.
    const delta = Math.min((now - last) / 1000, 1 / 20);
    last = now;

    spring.advance(delta);
    onFrame(spring.value);

    if (spring.isSettled) {
      onSettle?.();
      return;
    }
    frame = requestAnimationFrame(step);
  };

  frame = requestAnimationFrame(step);

  return () => {
    cancelled = true;
    cancelAnimationFrame(frame);
  };
}
