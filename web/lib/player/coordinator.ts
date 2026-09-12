import {
  AdapterListener,
  MediaItem,
  PlaybackAdapter,
  PlayerCommand,
  PlayerState,
  QueueState,
  SourceResolver,
  initialPlayerState,
} from "./types";

/**
 * The single owner of canonical web playback state.
 *
 * One coordinator per client, exactly as on Android: the mini player, Now Playing, queue,
 * and Media Session all read projections of this state and send commands back. Nothing else
 * is allowed to hold a media element.
 *
 * State is published by subscription rather than through React state so that position ticks
 * - which arrive several times a second - do not re-render the whole tree. Consumers
 * subscribe to the slice they need. See docs/WEB_PRODUCT_ARCHITECTURE.md.
 */
export class PlayerCoordinator {
  private state: PlayerState = initialPlayerState;
  private listeners = new Set<(state: PlayerState) => void>();
  /** Revoked when a track is replaced, so object URLs do not accumulate. */
  private currentObjectUrl?: string;

  constructor(
    private readonly adapter: PlaybackAdapter,
    private readonly resolveSource: SourceResolver
  ) {
    this.adapter.setListener(this.adapterListener);
  }

  getState(): PlayerState {
    return this.state;
  }

  subscribe(listener: (state: PlayerState) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  setCapabilities(capabilities: Partial<PlayerState["capabilities"]>): void {
    this.update((state) => ({
      ...state,
      capabilities: { ...state.capabilities, ...capabilities },
    }));
  }

  async dispatch(command: PlayerCommand): Promise<void> {
    switch (command.type) {
      case "load":
        await this.loadTrack(command.track, command.playWhenReady ?? false);
        break;
      case "play":
        await this.play();
        break;
      case "pause":
        this.adapter.pause();
        break;
      case "seek":
        this.update((state) => ({ ...state, status: "Seeking" }));
        this.adapter.seek(command.positionMs);
        break;
      case "skipNext":
        await this.skipNext();
        break;
      case "skipPrevious":
        await this.skipPrevious();
        break;
      case "addToQueue":
        this.update((state) => ({
          ...state,
          queue: { ...state.queue, items: [...state.queue.items, command.track] },
        }));
        break;
      case "removeFromQueue":
        this.update((state) => removeFromQueue(state, command.trackId));
        break;
      case "clearQueue":
        this.update((state) => ({
          ...state,
          queue: { ...state.queue, items: [], currentIndex: -1 },
        }));
        break;
      case "setVolume": {
        const volume = clamp(command.volume, 0, 1);
        this.adapter.setVolume(volume);
        this.update((state) => ({ ...state, volume }));
        break;
      }
      case "setRepeat":
        this.update((state) => ({
          ...state,
          queue: { ...state.queue, repeatMode: command.mode },
        }));
        break;
      case "setShuffle":
        this.update((state) => ({
          ...state,
          queue: { ...state.queue, shuffleMode: command.mode },
        }));
        break;
    }
  }

  release(): void {
    this.revokeCurrentUrl();
    this.adapter.release();
    this.listeners.clear();
  }

  /**
   * Loads a track and places it in the queue.
   *
   * The queue placement is deliberate: the Android client shipped without it, so a finished
   * track had nothing to advance to and completion was never observable.
   */
  private async loadTrack(track: MediaItem, playWhenReady: boolean): Promise<void> {
    this.update((state) => ({
      ...state,
      status: "Loading",
      currentTrack: track,
      error: undefined,
      position: { elapsedMs: 0 },
      queue: placeInQueue(state.queue, track),
    }));

    const source = await this.resolveSource(track);
    if (!source) {
      this.update((state) => ({
        ...state,
        status: "Error",
        error: `${track.title} could not be opened. The file may have moved or access may have been revoked.`,
      }));
      return;
    }

    this.revokeCurrentUrl();
    if (source.startsWith("blob:")) this.currentObjectUrl = source;

    await this.adapter.load(track, source, playWhenReady);
  }

  private async play(): Promise<void> {
    const { currentTrack, queue } = this.state;
    if (!currentTrack && queue.items.length > 0) {
      const index = queue.currentIndex >= 0 ? queue.currentIndex : 0;
      await this.loadTrack(queue.items[index], true);
      return;
    }
    await this.adapter.play();
  }

  private async skipNext(): Promise<void> {
    const { queue } = this.state;
    if (queue.items.length === 0) return;

    const next = queue.currentIndex + 1;
    if (next >= queue.items.length) {
      if (queue.repeatMode === "All") {
        await this.loadTrack(queue.items[0], true);
        return;
      }
      // Nothing to advance to: terminal, and left terminal so observers can see it.
      this.adapter.pause();
      this.update((state) => ({ ...state, status: "Completed" }));
      return;
    }
    await this.loadTrack(queue.items[next], true);
  }

  private async skipPrevious(): Promise<void> {
    const { queue, position } = this.state;
    // Past a few seconds, "previous" restarts the track, as every music player does.
    if (position.elapsedMs > 3000) {
      this.adapter.seek(0);
      return;
    }
    const previous = queue.currentIndex - 1;
    if (previous < 0) {
      this.adapter.seek(0);
      return;
    }
    await this.loadTrack(queue.items[previous], true);
  }

  private readonly adapterListener: AdapterListener = {
    onReady: (durationMs) =>
      this.update((state) => ({
        ...state,
        status: state.status === "Loading" ? "Ready" : state.status,
        position: { ...state.position, durationMs },
      })),

    onPlaying: () =>
      this.update((state) => ({
        ...state,
        status: "Playing",
        capabilities: { ...state.capabilities, autoplayBlocked: false },
      })),

    onPaused: () =>
      this.update((state) =>
        // Completed and Error are terminal. The Android client reset a completion to Paused
        // a millisecond after setting it, so nothing ever observed a finished track.
        isTerminal(state) ? state : { ...state, status: "Paused" }
      ),

    onBuffering: (isBuffering) =>
      this.update((state) =>
        isTerminal(state)
          ? state
          : { ...state, status: isBuffering ? "Buffering" : state.status }
      ),

    onPosition: (elapsedMs, durationMs, bufferedMs) =>
      this.update((state) => ({
        ...state,
        position: { elapsedMs, durationMs, bufferedMs },
      })),

    onCompleted: () => {
      void this.handleCompletion();
    },

    onError: (message) =>
      this.update((state) => ({ ...state, status: "Error", error: message })),

    onAutoplayBlocked: () =>
      this.update((state) => ({
        ...state,
        status: "Paused",
        capabilities: { ...state.capabilities, autoplayBlocked: true },
      })),
  };

  private async handleCompletion(): Promise<void> {
    const { queue } = this.state;
    if (queue.repeatMode === "One" && this.state.currentTrack) {
      this.adapter.seek(0);
      await this.adapter.play();
      return;
    }
    await this.skipNext();
  }

  private revokeCurrentUrl(): void {
    if (this.currentObjectUrl) {
      URL.revokeObjectURL(this.currentObjectUrl);
      this.currentObjectUrl = undefined;
    }
  }

  private update(reducer: (state: PlayerState) => PlayerState): void {
    const next = reducer(this.state);
    if (next === this.state) return;
    this.state = next;
    this.listeners.forEach((listener) => listener(next));
  }
}

function isTerminal(state: PlayerState): boolean {
  return state.status === "Completed" || state.status === "Error";
}

/** A loaded track replaces the queue; addToQueue is what appends. */
function placeInQueue(queue: QueueState, track: MediaItem): QueueState {
  const existing = queue.items.findIndex((item) => item.id === track.id);
  if (existing >= 0) return { ...queue, currentIndex: existing };
  return { ...queue, items: [track], currentIndex: 0 };
}

function removeFromQueue(state: PlayerState, trackId: string): PlayerState {
  const index = state.queue.items.findIndex((item) => item.id === trackId);
  if (index < 0) return state;

  const items = state.queue.items.filter((_, i) => i !== index);
  let currentIndex = state.queue.currentIndex;
  if (index < currentIndex) currentIndex -= 1;
  else if (index === currentIndex) currentIndex = Math.min(currentIndex, items.length - 1);

  return { ...state, queue: { ...state.queue, items, currentIndex } };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
