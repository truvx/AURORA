import { describe, expect, it, vi } from "vitest";
import { DRAG_THRESHOLD, VelocityTracker, createDragHandlers } from "../gesture";

/**
 * A stand-in for the element the gesture is attached to. Pointer capture is the part that
 * actually matters here: without it a drag stops the moment the finger leaves the element,
 * which is most of the time on a small control.
 */
function makeTarget() {
  const captured = new Set<number>();
  return {
    setPointerCapture: vi.fn((id: number) => captured.add(id)),
    releasePointerCapture: vi.fn((id: number) => captured.delete(id)),
    hasPointerCapture: vi.fn((id: number) => captured.has(id)),
    captured,
  };
}

type FakeEvent = {
  pointerId: number;
  button: number;
  clientX: number;
  clientY: number;
  timeStamp: number;
  currentTarget: unknown;
};

function event(partial: Partial<FakeEvent> & { currentTarget: unknown }): React.PointerEvent {
  return {
    pointerId: 1,
    button: 0,
    clientX: 0,
    clientY: 0,
    timeStamp: 0,
    ...partial,
  } as unknown as React.PointerEvent;
}

describe("VelocityTracker", () => {
  it("reports nothing from a single sample", () => {
    const tracker = new VelocityTracker();
    tracker.add(0, 0);

    expect(tracker.velocity).toBe(0);
  });

  it("measures pixels per second", () => {
    const tracker = new VelocityTracker();
    tracker.add(0, 0);
    tracker.add(100, 100);

    expect(tracker.velocity).toBeCloseTo(1000, 5);
  });

  it("keeps the direction of travel", () => {
    const tracker = new VelocityTracker();
    tracker.add(100, 0);
    tracker.add(0, 100);

    expect(tracker.velocity).toBeCloseTo(-1000, 5);
  });

  it("survives two samples sharing a timestamp", () => {
    // Coalesced pointer events can report identical timestamps; dividing by that gap
    // would produce Infinity and fling the surface off-screen.
    const tracker = new VelocityTracker();
    tracker.add(0, 50);
    tracker.add(80, 50);

    expect(tracker.velocity).toBe(0);
  });

  it("measures over a window rather than the last two events", () => {
    // A fast drag that pauses for one event before release still has to read as fast. The
    // last pair alone would report a near-stop and the flick would die at the finger.
    const tracker = new VelocityTracker();
    for (let t = 0; t <= 80; t += 20) tracker.add(t * 2, t);
    tracker.add(160, 90);

    expect(tracker.velocity).toBeGreaterThan(1500);
  });

  it("forgets samples older than the window", () => {
    // A drag that stalls for a while before release should not be thrown by speed the
    // user has already stopped applying.
    const tracker = new VelocityTracker();
    tracker.add(0, 0);
    tracker.add(500, 50);
    tracker.add(505, 400);
    tracker.add(506, 450);

    expect(Math.abs(tracker.velocity)).toBeLessThan(100);
  });

  it("resets to nothing", () => {
    const tracker = new VelocityTracker();
    tracker.add(0, 0);
    tracker.add(100, 100);
    tracker.reset();

    expect(tracker.velocity).toBe(0);
  });
});

describe("createDragHandlers", () => {
  it("captures the pointer on press", () => {
    const target = makeTarget();
    const handlers = createDragHandlers({ axis: "y" });

    handlers.onPointerDown(event({ currentTarget: target }));

    expect(target.setPointerCapture).toHaveBeenCalledWith(1);
  });

  it("does not start a drag below the threshold", () => {
    // Fingers are not steady. A few pixels of wobble during a tap must not be read as a
    // drag, or every button feels like it slips.
    const target = makeTarget();
    const onDragStart = vi.fn();
    const handlers = createDragHandlers({ axis: "y", onDragStart });

    handlers.onPointerDown(event({ currentTarget: target, clientY: 0 }));
    handlers.onPointerMove(
      event({ currentTarget: target, clientY: DRAG_THRESHOLD - 1, timeStamp: 16 })
    );

    expect(onDragStart).not.toHaveBeenCalled();
  });

  it("commits once the threshold is passed", () => {
    const target = makeTarget();
    const onDragStart = vi.fn();
    const onDrag = vi.fn();
    const handlers = createDragHandlers({ axis: "y", onDragStart, onDrag });

    handlers.onPointerDown(event({ currentTarget: target, clientY: 0 }));
    handlers.onPointerMove(
      event({ currentTarget: target, clientY: DRAG_THRESHOLD + 5, timeStamp: 16 })
    );

    expect(onDragStart).toHaveBeenCalledOnce();
    expect(onDrag).toHaveBeenCalledWith(
      expect.objectContaining({ offset: DRAG_THRESHOLD + 5 })
    );
  });

  it("reports offset from the grab point, not from the element", () => {
    // Respecting where the user actually grabbed is what keeps the surface glued to the
    // finger. Snapping to the element's own origin breaks the illusion instantly.
    const target = makeTarget();
    const onDrag = vi.fn();
    const handlers = createDragHandlers({ axis: "y", onDrag });

    handlers.onPointerDown(event({ currentTarget: target, clientY: 300 }));
    handlers.onPointerMove(event({ currentTarget: target, clientY: 260, timeStamp: 16 }));

    expect(onDrag).toHaveBeenCalledWith(expect.objectContaining({ offset: -40 }));
  });

  it("treats a press and release without movement as a tap", () => {
    // One pointer-down serves both gestures. Waiting to see which arrives is what puts a
    // delay on every tap.
    const target = makeTarget();
    const onTap = vi.fn();
    const onDragEnd = vi.fn();
    const handlers = createDragHandlers({ axis: "y", onTap, onDragEnd });

    handlers.onPointerDown(event({ currentTarget: target }));
    handlers.onPointerUp(event({ currentTarget: target, timeStamp: 40 }));

    expect(onTap).toHaveBeenCalledOnce();
    expect(onDragEnd).not.toHaveBeenCalled();
  });

  it("does not fire a tap after a real drag", () => {
    const target = makeTarget();
    const onTap = vi.fn();
    const onDragEnd = vi.fn();
    const handlers = createDragHandlers({ axis: "y", onTap, onDragEnd });

    handlers.onPointerDown(event({ currentTarget: target, clientY: 0 }));
    handlers.onPointerMove(event({ currentTarget: target, clientY: 120, timeStamp: 50 }));
    handlers.onPointerUp(event({ currentTarget: target, clientY: 120, timeStamp: 60 }));

    expect(onTap).not.toHaveBeenCalled();
    expect(onDragEnd).toHaveBeenCalledOnce();
  });

  it("hands the release velocity to the end of the gesture", () => {
    const target = makeTarget();
    const onDragEnd = vi.fn();
    const handlers = createDragHandlers({ axis: "y", onDragEnd });

    handlers.onPointerDown(event({ currentTarget: target, clientY: 0, timeStamp: 0 }));
    handlers.onPointerMove(event({ currentTarget: target, clientY: 50, timeStamp: 25 }));
    handlers.onPointerMove(event({ currentTarget: target, clientY: 100, timeStamp: 50 }));
    handlers.onPointerUp(event({ currentTarget: target, clientY: 100, timeStamp: 50 }));

    expect(onDragEnd.mock.calls[0][0].velocity).toBeGreaterThan(1000);
  });

  it("releases pointer capture when the gesture ends", () => {
    // A leaked capture swallows every later pointer event on the page.
    const target = makeTarget();
    const handlers = createDragHandlers({ axis: "y" });

    handlers.onPointerDown(event({ currentTarget: target }));
    handlers.onPointerUp(event({ currentTarget: target, timeStamp: 20 }));

    expect(target.releasePointerCapture).toHaveBeenCalledWith(1);
    expect(target.captured.size).toBe(0);
  });

  it("ignores events from a second pointer", () => {
    // A second finger landing mid-drag must not retarget the gesture.
    const target = makeTarget();
    const onDrag = vi.fn();
    const handlers = createDragHandlers({ axis: "y", onDrag });

    handlers.onPointerDown(event({ currentTarget: target, pointerId: 1, clientY: 0 }));
    handlers.onPointerMove(
      event({ currentTarget: target, pointerId: 2, clientY: 200, timeStamp: 16 })
    );

    expect(onDrag).not.toHaveBeenCalled();
  });

  it("ignores non-primary buttons", () => {
    // Right-click should open a context menu, not drag a sheet.
    const target = makeTarget();
    const handlers = createDragHandlers({ axis: "y" });

    handlers.onPointerDown(event({ currentTarget: target, button: 2 }));

    expect(target.setPointerCapture).not.toHaveBeenCalled();
  });

  it("settles back rather than committing when the pointer is cancelled", () => {
    // A cancelled pointer is the system taking over - a system gesture, a call arriving.
    // Treating it as a release would commit something the user never finished.
    const target = makeTarget();
    const onDragEnd = vi.fn();
    const handlers = createDragHandlers({ axis: "y", onDragEnd });

    handlers.onPointerDown(event({ currentTarget: target, clientY: 0 }));
    handlers.onPointerMove(event({ currentTarget: target, clientY: 150, timeStamp: 30 }));
    handlers.onPointerCancel(event({ currentTarget: target, clientY: 150, timeStamp: 40 }));

    expect(onDragEnd).toHaveBeenCalledWith({ offset: 0, velocity: 0 });
  });

  it("does nothing at all when disabled", () => {
    const target = makeTarget();
    const onDrag = vi.fn();
    const handlers = createDragHandlers({ axis: "y", onDrag, disabled: true });

    handlers.onPointerDown(event({ currentTarget: target, clientY: 0 }));
    handlers.onPointerMove(event({ currentTarget: target, clientY: 200, timeStamp: 16 }));

    expect(target.setPointerCapture).not.toHaveBeenCalled();
    expect(onDrag).not.toHaveBeenCalled();
  });

  it("tracks the horizontal axis when asked to", () => {
    const target = makeTarget();
    const onDrag = vi.fn();
    const handlers = createDragHandlers({ axis: "x", onDrag });

    handlers.onPointerDown(event({ currentTarget: target, clientX: 10, clientY: 500 }));
    handlers.onPointerMove(
      event({ currentTarget: target, clientX: 90, clientY: 500, timeStamp: 16 })
    );

    expect(onDrag).toHaveBeenCalledWith(expect.objectContaining({ offset: 80 }));
  });

  it("can start a new gesture after the previous one ended", () => {
    const target = makeTarget();
    const onDragStart = vi.fn();
    const handlers = createDragHandlers({ axis: "y", onDragStart });

    handlers.onPointerDown(event({ currentTarget: target, clientY: 0 }));
    handlers.onPointerMove(event({ currentTarget: target, clientY: 100, timeStamp: 30 }));
    handlers.onPointerUp(event({ currentTarget: target, clientY: 100, timeStamp: 40 }));

    handlers.onPointerDown(event({ currentTarget: target, clientY: 0, timeStamp: 100 }));
    handlers.onPointerMove(event({ currentTarget: target, clientY: 100, timeStamp: 130 }));

    expect(onDragStart).toHaveBeenCalledTimes(2);
  });
});
