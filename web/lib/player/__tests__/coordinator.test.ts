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
  it("keeps the queue you built when you play something new", async () => {
    // Pressing play on a fourth track used to replace the three you had queued.
    await coordinator.dispatch({ type: "addToQueue", track: track("a") });
    await coordinator.dispatch({ type: "addToQueue", track: track("b") });
    await coordinator.dispatch({ type: "addToQueue", track: track("c") });

    await coordinator.dispatch({ type: "load", track: track("d") });

    const { queue } = coordinator.getState();
    expect(queue.items.map((i) => i.id)).toEqual(["a", "b", "c", "d"]);
    expect(queue.currentIndex).toBe(3);
  });

  it("advances past a duplicate instead of looping back to its first copy", async () => {
    // [A, B, A] used to play A, B, A, B forever: skipping to index 2 re-derived the
    // position from the track id and landed on index 0.
    await coordinator.dispatch({ type: "addToQueue", track: track("a") });
    await coordinator.dispatch({ type: "addToQueue", track: track("b") });
    await coordinator.dispatch({ type: "addToQueue", track: track("a") });
    await coordinator.dispatch({ type: "load", track: track("a") });
    expect(coordinator.getState().queue.currentIndex).toBe(0);

    await coordinator.dispatch({ type: "skipNext" });
    expect(coordinator.getState().queue.currentIndex).toBe(1);

    await coordinator.dispatch({ type: "skipNext" });
    expect(coordinator.getState().queue.currentIndex).toBe(2);
  });

  it("does not let a slow resolve overwrite a newer load", async () => {
    // Two rapid loads where the first resolves slower: the adapter used to end up playing
    // the slow one while the screen named the fast one.
    const resolved: Record<string, number> = { slow: 40, fast: 0 };
    const racing = new PlayerCoordinator(adapter, async (t: MediaItem) => {
      await new Promise((r) => setTimeout(r, resolved[t.id] ?? 0));
      return `blob:${t.id}`;
    });

    const first = racing.dispatch({ type: "load", track: track("slow") });
    const second = racing.dispatch({ type: "load", track: track("fast") });
    await Promise.all([first, second]);

    expect(racing.getState().currentTrack?.id).toBe("fast");
    expect(adapter.loaded.at(-1)?.track.id).toBe("fast");
  });

  it("leaves Seeking when a paused track finishes seeking", async () => {
    // A paused HTMLAudioElement emits seeking/seeked/canplay and no playing event, so the
    // status stayed "Seeking…" forever and the live region announced it.
    await coordinator.dispatch({ type: "load", track: track("a") });
    adapter.listener.onPaused();
    await coordinator.dispatch({ type: "seek", positionMs: 5000 });
    expect(coordinator.getState().status).toBe("Seeking");

    adapter.listener.onSeeked();

    expect(coordinator.getState().status).toBe("Paused");
  });

  it("restores a queue whole, with its pointer and its modes", async () => {
    // Restoring used to append track by track, so the pointer and both modes were never
    // applied - and each append rewrote the saved snapshot with the defaults, which made
    // the loss permanent.
    await coordinator.dispatch({
      type: "restoreQueue",
      items: [track("a"), track("b"), track("c")],
      currentIndex: 2,
      repeatMode: "All",
      shuffleMode: "On",
    });

    const { queue, status } = coordinator.getState();
    expect(queue.items.map((i) => i.id)).toEqual(["a", "b", "c"]);
    expect(queue.currentIndex).toBe(2);
    expect(queue.repeatMode).toBe("All");
    expect(queue.shuffleMode).toBe("On");
    // Restoring is not playing: a page that makes noise on its own after a refresh is a
    // worse surprise than a quiet one.
    expect(status).toBe("Idle");
    expect(adapter.loaded).toHaveLength(0);
  });

  it("plays a different order when shuffle is on", async () => {
    // setShuffle used to write the mode and nothing read it: skipNext stayed strictly
    // linear, so shuffle was a label on a control that changed nothing.
    for (const id of ["a", "b", "c", "d", "e"]) {
      await coordinator.dispatch({ type: "addToQueue", track: track(id) });
    }
    await coordinator.dispatch({ type: "load", track: track("a") });
    await coordinator.dispatch({ type: "setShuffle", mode: "On" });

    const played: string[] = [];
    for (let i = 0; i < 4; i += 1) {
      await coordinator.dispatch({ type: "skipNext" });
      played.push(coordinator.getState().currentTrack!.id);
    }

    // Every other track exactly once - the property that actually matters, and the one a
    // shuffle must never break. Asserting the order differs from the queue would be a
    // one-in-twenty-four flake, since a fair shuffle is entitled to return queue order.
    expect([...played].sort()).toEqual(["b", "c", "d", "e"]);
  });

  it("keeps the track you are on when shuffle is switched on", async () => {
    // Turning shuffle on must not jump you somewhere else mid-track.
    for (const id of ["a", "b", "c", "d"]) {
      await coordinator.dispatch({ type: "addToQueue", track: track(id) });
    }
    await coordinator.dispatch({ type: "load", track: track("c") });

    await coordinator.dispatch({ type: "setShuffle", mode: "On" });

    expect(coordinator.getState().currentTrack?.id).toBe("c");
    expect(coordinator.getState().queue.currentIndex).toBe(2);
  });

  it("returns to queue order when shuffle is switched off", async () => {
    for (const id of ["a", "b", "c"]) {
      await coordinator.dispatch({ type: "addToQueue", track: track(id) });
    }
    await coordinator.dispatch({ type: "load", track: track("a") });
    await coordinator.dispatch({ type: "setShuffle", mode: "On" });
    await coordinator.dispatch({ type: "setShuffle", mode: "Off" });

    await coordinator.dispatch({ type: "skipNext" });

    expect(coordinator.getState().currentTrack?.id).toBe("b");
  });

  it("clears a failed track's error when you try something else", async () => {
    // The banner outlived the condition: error was only ever cleared inside loadTrack, so
    // after a failure the transport looked inert and the message stayed on screen through
    // Next, Previous and Play alike.
    const failing = new PlayerCoordinator(adapter, async () => undefined);
    await failing.dispatch({ type: "load", track: track("gone") });
    expect(failing.getState().error).toBeDefined();

    await failing.dispatch({ type: "play" });

    expect(failing.getState().error).toBeUndefined();
  });

  it("shuffles rather than returning the queue order", async () => {
    // Math.random stubbed, so this asserts the ordering itself without a flake: a fair
    // shuffle is allowed to produce queue order, which is why the test above only checks
    // that every track is played once.
    const random = vi.spyOn(Math, "random").mockReturnValue(0);
    try {
      for (const id of ["a", "b", "c", "d"]) {
        await coordinator.dispatch({ type: "addToQueue", track: track(id) });
      }
      await coordinator.dispatch({ type: "load", track: track("a") });
      await coordinator.dispatch({ type: "setShuffle", mode: "On" });

      const played: string[] = [];
      for (let i = 0; i < 3; i += 1) {
        await coordinator.dispatch({ type: "skipNext" });
        played.push(coordinator.getState().currentTrack!.id);
      }

      // Pinned random makes this exact; the claim that matters is the second line.
      expect(played).toEqual(["c", "d", "b"]);
      expect(played).not.toEqual(["b", "c", "d"]);
    } finally {
      random.mockRestore();
    }
  });

});
