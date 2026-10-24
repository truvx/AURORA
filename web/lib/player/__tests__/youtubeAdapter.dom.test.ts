import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdapterListener, MediaItem } from "../types";
import { YouTubeIframeAdapter } from "../youtubeAdapter";

/**
 * The adapter's behaviour against a stand-in for the IFrame API.
 *
 * Coverage stopped at id parsing, which left the parts that carry the policy rules -
 * cue versus load, and a position timer that only reports while playing - untested.
 */

interface FakePlayer {
  playVideo: ReturnType<typeof vi.fn>;
  pauseVideo: ReturnType<typeof vi.fn>;
  seekTo: ReturnType<typeof vi.fn>;
  setVolume: ReturnType<typeof vi.fn>;
  loadVideoById: ReturnType<typeof vi.fn>;
  cueVideoById: ReturnType<typeof vi.fn>;
  getCurrentTime: ReturnType<typeof vi.fn>;
  getDuration: ReturnType<typeof vi.fn>;
  getPlayerState: ReturnType<typeof vi.fn>;
  destroy: ReturnType<typeof vi.fn>;
}

let created: FakePlayer;
let events: {
  onReady: () => void;
  onStateChange: (event: { data: number }) => void;
  onError: () => void;
};
/** Held back so a test can decide when the player becomes ready. */
let readyImmediately = true;

function listener(): AdapterListener {
  return {
    onPlaying: vi.fn(),
    onPaused: vi.fn(),
    onBuffering: vi.fn(),
    onCompleted: vi.fn(),
    onPosition: vi.fn(),
    onError: vi.fn(),
  } as unknown as AdapterListener;
}

beforeEach(() => {
  vi.useFakeTimers();
  readyImmediately = true;

  class Player {
    constructor(_elementId: string, options: { events: typeof events }) {
      events = options.events;
      created = {
        playVideo: vi.fn(),
        pauseVideo: vi.fn(),
        seekTo: vi.fn(),
        setVolume: vi.fn(),
        loadVideoById: vi.fn(),
        cueVideoById: vi.fn(),
        getCurrentTime: vi.fn(() => 0),
        getDuration: vi.fn(() => 0),
        getPlayerState: vi.fn(() => 1),
        destroy: vi.fn(),
      };
      Object.assign(this, created);
      if (readyImmediately) options.events.onReady();
    }
  }
  (window as unknown as { YT: unknown }).YT = { Player };
});

afterEach(() => {
  vi.useRealTimers();
});

const track: MediaItem = {
  id: "youtube:dQw4w9WgXcQ",
  provider: "YOUTUBE",
  title: "A video",
  isAvailable: true,
};

const localFile: MediaItem = {
  id: "local:42",
  provider: "LOCAL",
  title: "A file",
  isAvailable: true,
};

describe("YouTubeIframeAdapter", () => {
  it("cues rather than autoplays when there was no explicit intent", async () => {
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(listener());

    await adapter.load(track, "", false);

    // The capability matrix requires intent before playback starts; cueing prepares the
    // video without starting it.
    expect(created.cueVideoById).toHaveBeenCalledWith("dQw4w9WgXcQ");
    expect(created.loadVideoById).not.toHaveBeenCalled();
  });

  it("loads and plays when the person asked for it", async () => {
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(listener());

    await adapter.load(track, "", true);

    expect(created.loadVideoById).toHaveBeenCalledWith("dQw4w9WgXcQ");
    expect(created.cueVideoById).not.toHaveBeenCalled();
  });

  it("holds a load until the player is ready instead of dropping it", async () => {
    readyImmediately = false;
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(listener());

    await adapter.load(track, "", true);
    // Loading before the player exists silently does nothing, so it has to be queued.
    expect(created.loadVideoById).not.toHaveBeenCalled();

    events.onReady();
    expect(created.loadVideoById).toHaveBeenCalledWith("dQw4w9WgXcQ");
  });

  it("refuses a track with no usable video id, and says so", async () => {
    const heard = listener();
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(heard);

    await adapter.load(localFile, "", true);

    expect(heard.onError).toHaveBeenCalledWith("This YouTube track has no usable video id.");
  });

  it("reports a provider restriction as a restriction, not a crash", async () => {
    const heard = listener();
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(heard);
    await adapter.load(track, "", true);

    events.onError();

    expect(heard.onError).toHaveBeenCalledWith(
      "YouTube cannot play this video here. It may be restricted, private, or not embeddable."
    );
  });

  it("maps the player's states onto the coordinator's", async () => {
    const heard = listener();
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(heard);
    await adapter.load(track, "", true);

    events.onStateChange({ data: 3 });
    expect(heard.onBuffering).toHaveBeenLastCalledWith(true);

    events.onStateChange({ data: 1 });
    expect(heard.onPlaying).toHaveBeenCalled();
    expect(heard.onBuffering).toHaveBeenLastCalledWith(false);

    events.onStateChange({ data: 2 });
    expect(heard.onPaused).toHaveBeenCalled();

    events.onStateChange({ data: 0 });
    expect(heard.onCompleted).toHaveBeenCalled();
  });

  it("reports position only while it is actually playing", async () => {
    const heard = listener();
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(heard);
    await adapter.load(track, "", true);

    created.getCurrentTime.mockReturnValue(12.34);
    created.getDuration.mockReturnValue(200);
    events.onStateChange({ data: 1 });

    vi.advanceTimersByTime(500);
    expect(heard.onPosition).toHaveBeenCalledWith(12340, 200000, undefined);

    // An unguarded timer reports over the top of a locally playing track; this one checks
    // the player's own state before saying anything.
    (heard.onPosition as ReturnType<typeof vi.fn>).mockClear();
    created.getPlayerState.mockReturnValue(5);
    vi.advanceTimersByTime(1000);
    expect(heard.onPosition).not.toHaveBeenCalled();
  });

  it("leaves an unknown duration unknown", async () => {
    const heard = listener();
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(heard);
    await adapter.load(track, "", true);

    created.getDuration.mockReturnValue(0); // metadata has not arrived yet
    events.onStateChange({ data: 1 });
    vi.advanceTimersByTime(500);

    expect(heard.onPosition).toHaveBeenCalledWith(0, undefined, undefined);
  });

  it("stops reporting once paused", async () => {
    const heard = listener();
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(heard);
    await adapter.load(track, "", true);

    events.onStateChange({ data: 1 });
    events.onStateChange({ data: 2 });
    (heard.onPosition as ReturnType<typeof vi.fn>).mockClear();

    vi.advanceTimersByTime(5000);
    expect(heard.onPosition).not.toHaveBeenCalled();
  });

  it("converts the units the rest of the app uses", async () => {
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(listener());
    await adapter.load(track, "", true);

    adapter.seek(90_000);
    expect(created.seekTo).toHaveBeenCalledWith(90, true);

    adapter.setVolume(0.42);
    expect(created.setVolume).toHaveBeenCalledWith(42);
  });

  it("destroys the player and its timer on release", async () => {
    const heard = listener();
    const adapter = new YouTubeIframeAdapter("host");
    adapter.setListener(heard);
    await adapter.load(track, "", true);
    events.onStateChange({ data: 1 });

    adapter.release();
    (heard.onPosition as ReturnType<typeof vi.fn>).mockClear();
    vi.advanceTimersByTime(5000);

    expect(created.destroy).toHaveBeenCalled();
    expect(heard.onPosition).not.toHaveBeenCalled();
  });
});
