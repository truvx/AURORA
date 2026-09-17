/**
 * Pointer tracking for direct manipulation.
 *
 * Touch and content move together: while a finger is down the surface is glued to it, 1:1,
 * for the whole gesture rather than animating once the gesture completes. Two details do
 * most of the work here, and both are easy to get wrong:
 *
 *  - Pointer capture, so tracking survives the pointer leaving the element's bounds. A drag
 *    that dies when the finger strays outside the original box feels broken.
 *  - A history of recent samples rather than just the current point, because the number
 *    that matters at the end of a drag is the velocity, and velocity needs two points in
 *    time. Taking it from the last single move event makes it noise.
 */

/** Movement required before a drag commits to an axis, in CSS pixels. */
export const DRAG_THRESHOLD = 10;

/** How far back to look when measuring release velocity. */
const VELOCITY_WINDOW_MS = 100;

interface Sample {
  readonly value: number;
  readonly time: number;
}

/**
 * Release velocity from a short history of positions.
 *
 * Measured across a window rather than between the last two events: pointer events arrive
 * irregularly, and a single pair can straddle a 2 ms gap that reports an absurd speed, or a
 * stationary moment at the end of a fast drag that reports zero.
 */
export class VelocityTracker {
  private samples: Sample[] = [];

  add(value: number, time: number): void {
    this.samples.push({ value, time });
    const cutoff = time - VELOCITY_WINDOW_MS;
    while (this.samples.length > 2 && this.samples[0].time < cutoff) {
      this.samples.shift();
    }
  }

  /** Pixels per second. Zero until there are two samples far enough apart to divide by. */
  get velocity(): number {
    if (this.samples.length < 2) return 0;
    const first = this.samples[0];
    const last = this.samples[this.samples.length - 1];
    const elapsed = last.time - first.time;
    if (elapsed <= 0) return 0;
    return ((last.value - first.value) / elapsed) * 1000;
  }

  reset(): void {
    this.samples = [];
  }
}

export interface DragUpdate {
  /** Distance from where the gesture started, in CSS pixels. */
  readonly offset: number;
  /** Pixels per second, signed. Only meaningful at the end of a gesture. */
  readonly velocity: number;
}

export interface DragHandlers {
  onPointerDown(event: React.PointerEvent): void;
  onPointerMove(event: React.PointerEvent): void;
  onPointerUp(event: React.PointerEvent): void;
  onPointerCancel(event: React.PointerEvent): void;
}

export interface DragOptions {
  /** Which axis to measure. */
  readonly axis: "x" | "y";
  /** Called once the gesture passes the threshold and commits. */
  onDragStart?(): void;
  /** Called for every move after the gesture commits, 1:1 with the pointer. */
  onDrag?(update: DragUpdate): void;
  /** Called at release with the velocity the pointer left at. */
  onDragEnd?(update: DragUpdate): void;
  /**
   * Called when the pointer is released without ever passing the threshold, which is a tap
   * rather than a drag. Lets one pointer-down serve both without a disambiguation delay.
   */
  onTap?(): void;
  /** Skips the gesture entirely, e.g. while a surface is disabled. */
  readonly disabled?: boolean;
}

/**
 * Builds pointer handlers implementing a single-axis drag.
 *
 * Both a tap and a drag are recognised from the same pointer-down and resolved once intent
 * is clear, rather than waiting to see which one arrives. Waiting is what puts a delay on
 * every tap, and a tap that lags is the thing users notice first.
 *
 * This is deliberately a plain factory rather than a hook, so it can be exercised directly
 * in tests with synthetic events.
 */
export function createDragHandlers(options: DragOptions): DragHandlers {
  const tracker = new VelocityTracker();
  let pointerId: number | undefined;
  let origin = 0;
  let committed = false;

  const coordinate = (event: React.PointerEvent): number =>
    options.axis === "y" ? event.clientY : event.clientX;

  const finish = (event: React.PointerEvent) => {
    if (pointerId === undefined) return;
    const element = event.currentTarget as Element;
    if (element.hasPointerCapture?.(pointerId)) {
      element.releasePointerCapture(pointerId);
    }

    const offset = coordinate(event) - origin;
    const velocity = tracker.velocity;

    if (committed) {
      options.onDragEnd?.({ offset, velocity });
    } else {
      options.onTap?.();
    }

    pointerId = undefined;
    committed = false;
    tracker.reset();
  };

  return {
    onPointerDown(event) {
      if (options.disabled || pointerId !== undefined) return;
      // Only the primary button; a right-click should open a menu, not drag a sheet.
      if (event.button !== 0) return;

      pointerId = event.pointerId;
      origin = coordinate(event);
      committed = false;
      tracker.reset();
      tracker.add(origin, event.timeStamp);

      // Capture up front so the gesture keeps tracking once the finger leaves the element.
      (event.currentTarget as Element).setPointerCapture?.(event.pointerId);
    },

    onPointerMove(event) {
      if (pointerId !== event.pointerId) return;

      const position = coordinate(event);
      tracker.add(position, event.timeStamp);
      const offset = position - origin;

      if (!committed) {
        // Hysteresis: below this, the user has not said which gesture they mean yet, and
        // treating a shaky tap as a drag makes buttons feel slippery.
        if (Math.abs(offset) < DRAG_THRESHOLD) return;
        committed = true;
        options.onDragStart?.();
      }

      options.onDrag?.({ offset, velocity: tracker.velocity });
    },

    onPointerUp(event) {
      if (pointerId !== event.pointerId) return;
      finish(event);
    },

    onPointerCancel(event) {
      if (pointerId !== event.pointerId) return;
      // A cancelled pointer is not a commit: settle back rather than acting on it.
      const wasCommitted = committed;
      committed = false;
      if (wasCommitted) options.onDragEnd?.({ offset: 0, velocity: 0 });
      pointerId = undefined;
      tracker.reset();
    },
  };
}
