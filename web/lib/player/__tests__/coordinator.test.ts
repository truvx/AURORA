import { beforeEach, describe, expect, it, vi } from "vitest";
import { PlayerCoordinator } from "../coordinator";
import { AdapterListener, MediaItem, PlaybackAdapter } from "../types";

/**
 * The coordinator owns canonical state, so these cover the rules that were learned the hard
 * way on Android: a loaded track must be in the queue, and a terminal status must survive
 * the engine settling afterwards.
 */

class FakeAdapter implements PlaybackAdapter {
  listener!: AdapterListener;
  loaded: Array<{ track: MediaItem; src: string; playWhenReady: boolean }> = [];
  paused = 0;
  seeks: number[] = [];
  volume = 1;

  setListener(listener: AdapterListener) {
    this.listener = listener;
  }
  async load(track: MediaItem, src: string, playWhenReady: boolean) {
    this.loaded.push({ track, src, playWhenReady });
  }
  async play() {
    this.listener.onPlaying();
  }
  pause() {
    this.paused += 1;
    this.listener.onPaused();
  }
  seek(positionMs: number) {
    this.seeks.push(positionMs);
  }
  setVolume(volume: number) {
    this.volume = volume;
  }
  release() {}
}

function track(id: string): MediaItem {
  return { id, provider: "LOCAL", title: `Track ${id}`, isAvailable: true };
}

describe("PlayerCoordinator", () => {
  let adapter: FakeAdapter;
  let coordinator: PlayerCoordinator;

  beforeEach(() => {
    adapter = new FakeAdapter();
    coordinator = new PlayerCoordinator(adapter, async () => "blob:fake");
    vi.stubGlobal("URL", { ...URL, revokeObjectURL: vi.fn() });
  });

  it("places a loaded track into the queue", async () => {
    await coordinator.dispatch({ type: "load", track: track("a") });

    const { queue } = coordinator.getState();
    expect(queue.items.map((i) => i.id)).toEqual(["a"]);
    expect(queue.currentIndex).toBe(0);
  });

  it("selects an already-queued track rather than duplicating it", async () => {
    await coordinator.dispatch({ type: "addToQueue", track: track("a") });
    await coordinator.dispatch({ type: "addToQueue", track: track("b") });
    await coordinator.dispatch({ type: "load", track: track("b") });

    const { queue } = coordinator.getState();
    expect(queue.items.map((i) => i.id)).toEqual(["a", "b"]);
    expect(queue.currentIndex).toBe(1);
  });

  it("keeps a completed status when the engine settles afterwards", async () => {
    await coordinator.dispatch({ type: "load", track: track("a") });
    adapter.listener.onCompleted();
    await Promise.resolve();

    // skipNext with nothing to advance to pauses the element, which reports Paused.
    // That must not erase the completion.
    adapter.listener.onPaused();
    adapter.listener.onBuffering(false);

    expect(coordinator.getState().status).toBe("Completed");
  });

  it("reports an unresolvable source as an error instead of hanging", async () => {
    const failing = new PlayerCoordinator(new FakeAdapter(), async () => undefined);
    await failing.dispatch({ type: "load", track: track("gone") });

    const state = failing.getState();
    expect(state.status).toBe("Error");
    expect(state.error).toMatch(/could not be opened/i);
  });

  it("treats blocked autoplay as recoverable rather than a failure", async () => {
    await coordinator.dispatch({ type: "load", track: track("a") });
    adapter.listener.onAutoplayBlocked();

    const state = coordinator.getState();
    // Paused and flagged, never Error: the user just needs to press play.
    expect(state.status).toBe("Paused");
    expect(state.capabilities.autoplayBlocked).toBe(true);
  });

  it("clears the autoplay flag once playback actually starts", async () => {
    await coordinator.dispatch({ type: "load", track: track("a") });
    adapter.listener.onAutoplayBlocked();
    adapter.listener.onPlaying();

    expect(coordinator.getState().capabilities.autoplayBlocked).toBe(false);
  });

  it("advances to the next queued track when one finishes", async () => {
    await coordinator.dispatch({ type: "addToQueue", track: track("a") });
    await coordinator.dispatch({ type: "addToQueue", track: track("b") });
    await coordinator.dispatch({ type: "load", track: track("a") });

    adapter.listener.onCompleted();
    await Promise.resolve();
    await Promise.resolve();

    expect(coordinator.getState().currentTrack?.id).toBe("b");
  });

  it("restarts the track when skipping back mid-play", async () => {
    await coordinator.dispatch({ type: "load", track: track("a") });
    adapter.listener.onPosition(9000, 200000);

    await coordinator.dispatch({ type: "skipPrevious" });

    expect(adapter.seeks).toContain(0);
  });

  it("keeps duration undefined when the browser does not report one", async () => {
    await coordinator.dispatch({ type: "load", track: track("a") });
    adapter.listener.onReady(undefined);

    // Unknown must stay unknown rather than becoming a zero-length track.
    expect(coordinator.getState().position.durationMs).toBeUndefined();
  });

  it("removes a queued track and keeps the current index pointing at the same item", async () => {
    await coordinator.dispatch({ type: "addToQueue", track: track("a") });
    await coordinator.dispatch({ type: "addToQueue", track: track("b") });
    await coordinator.dispatch({ type: "addToQueue", track: track("c") });
    await coordinator.dispatch({ type: "load", track: track("c") });
    await coordinator.dispatch({ type: "removeFromQueue", trackId: "a" });

    const { queue } = coordinator.getState();
    expect(queue.items.map((i) => i.id)).toEqual(["b", "c"]);
    expect(queue.items[queue.currentIndex].id).toBe("c");
  });

  it("clamps volume to the valid range", async () => {
    await coordinator.dispatch({ type: "setVolume", volume: 5 });
    expect(coordinator.getState().volume).toBe(1);

    await coordinator.dispatch({ type: "setVolume", volume: -2 });
    expect(coordinator.getState().volume).toBe(0);
  });

  it("notifies subscribers and stops after unsubscribe", async () => {
    const seen: string[] = [];
    const unsubscribe = coordinator.subscribe((state) => seen.push(state.status));

    await coordinator.dispatch({ type: "load", track: track("a") });
    const countWhileSubscribed = seen.length;
    unsubscribe();
    await coordinator.dispatch({ type: "load", track: track("b") });

    expect(countWhileSubscribed).toBeGreaterThan(0);
    expect(seen.length).toBe(countWhileSubscribed);
  });
});
