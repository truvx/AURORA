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

  /** Rises on every load; a resolve that finishes against an older one is discarded. */
  private loadGeneration = 0;

  /** What to go back to when a seek ends. */
  private statusBeforeSeek: PlayerState["status"] | undefined;

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
    /*
     * A fresh intent clears the last failure.
     *
     * The error was only ever cleared inside loadTrack, so after a track failed to open
     * the banner outlived the condition: Next, Previous and Play all left it on screen,
     * and the transport read as inert when it was working. Anything the listener does to
     * move on is them saying they have read it.
     */
    if (RECOVERING_COMMANDS.has(command.type) && this.state.error !== undefined) {
      this.update((state) => ({ ...state, error: undefined }));
    }

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
        this.update((state) => {
          // Remembered, because a seek is a detour and not a destination: what comes after
          // it is whatever was happening before it.
          if (!isTerminal(state)) this.statusBeforeSeek = state.status;
          return { ...state, status: "Seeking" };
        });
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
      case "restoreQueue":
        // Deliberately not playing, and deliberately not touching currentTrack: this is
        // the queue coming back, not a track starting.
        this.update((state) => ({
          ...state,
          queue: {
            items: command.items,
            currentIndex: command.currentIndex,
            repeatMode: command.repeatMode,
            shuffleMode: command.shuffleMode,
            /*
             * Regenerated rather than restored.
             *
             * A shuffle order is how one sitting happened to run, not a property of the
             * queue, and persisting it would mean reopening the app replays the same
             * "random" order every time. Coming back to a shuffled queue reshuffles.
             */
            shuffledOrder:
              command.shuffleMode === "On"
                ? shuffledOrderFrom(command.items.length, command.currentIndex)
                : [],
          },
        }));
        break;
      case "playList": {
        /*
         * The list becomes the queue, and the tapped track becomes the position in it.
         *
         * Replacing rather than appending is the point: a list is a context, and "play the
         * album from here" means the album is now what is playing. `load` is the other
         * intent - one track arriving alone - and it still joins without disturbing
         * anything.
         */
        if (command.items.length === 0) break;
        /*
         * An index outside the list starts from the beginning, rather than clamping to
         * the end. An out-of-range index means the caller's idea of the list is stale, and
         * starting at the last track would play one song and stop - "play this list" is
         * the recoverable reading.
         */
        const asked = command.startIndex;
        const start = asked >= 0 && asked < command.items.length ? asked : 0;
        const items = [...command.items];
        this.update((state) => ({
          ...state,
          queue: { ...state.queue, items, currentIndex: start, shuffledOrder: [] },
        }));
        await this.loadTrack(items[start], true, start);
        break;
      }
      case "clearQueue":
        this.update((state) => ({
          ...state,
          queue: { ...state.queue, items: [], currentIndex: -1, shuffledOrder: [] },
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
          queue: {
            ...state.queue,
            shuffleMode: command.mode,
            shuffledOrder:
              command.mode === "On"
                ? shuffledOrderFrom(state.queue.items.length, state.queue.currentIndex)
                : [],
          },
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
  private async loadTrack(
    track: MediaItem,
    playWhenReady: boolean,
    /**
     * Where in the queue this track is being played from.
     *
     * Passed in rather than looked up, because a queue may hold the same track twice and
     * its identity cannot say which copy is meant. Skipping into the second copy of a
     * track used to re-derive the position from the id, land on the first copy, and loop
     * between them forever.
     */
    queueIndex?: number
  ): Promise<void> {
    const generation = ++this.loadGeneration;

    this.update((state) => ({
      ...state,
      status: "Loading",
      currentTrack: track,
      error: undefined,
      position: { elapsedMs: 0 },
      queue: placeInQueue(state.queue, track, queueIndex),
    }));

    const source = await this.resolveSource(track);
    /*
     * Another load started while this one was resolving.
     *
     * Without this the loser still reached the adapter, so the speakers played one track
     * while the screen named another - and worse, revokeCurrentUrl below would revoke the
     * blob the winner had just been handed.
     */
    if (generation !== this.loadGeneration) return;

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

    const next = stepThrough(queue, 1);
    if (next === undefined) {
      if (queue.repeatMode === "All") {
        await this.loadTrack(queue.items[0], true, 0);
        return;
      }
      // Nothing to advance to: terminal, and left terminal so observers can see it.
      this.adapter.pause();
      this.update((state) => ({ ...state, status: "Completed" }));
      return;
    }
    await this.loadTrack(queue.items[next], true, next);
  }

  private async skipPrevious(): Promise<void> {
    const { queue, position } = this.state;
    // Past a few seconds, "previous" restarts the track, as every music player does.
    if (position.elapsedMs > RESTART_THRESHOLD_MS) {
      this.adapter.seek(0);
      return;
    }
    const previous = stepThrough(queue, -1);
    if (previous === undefined) {
      this.adapter.seek(0);
      return;
    }
    await this.loadTrack(queue.items[previous], true, previous);
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

    onSeeked: () =>
      this.update((state) =>
        state.status === "Seeking"
          ? { ...state, status: this.statusBeforeSeek ?? "Paused" }
          : state
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
    const reduced = reducer(this.state);
    if (reduced === this.state) return;
    /*
     * Capability is recomputed here rather than at each call site, so there is no path
     * that changes the queue and forgets to say what the transport can now do.
     */
    const next = withTransportCapability(reduced);
    this.state = next;
    this.listeners.forEach((listener) => listener(next));
  }
}

/**
 * A play order that is not the queue order, with the current track at the front.
 *
 * Current-first so switching shuffle on does not jump you out of the track you are
 * listening to: the change is about what comes next, not about now. Fisher-Yates over the
 * rest, which is the shuffle that is actually uniform - sorting by `random()` is not, and
 * the bias shows over a long queue.
 */
function shuffledOrderFrom(length: number, currentIndex: number): number[] {
  if (length <= 0) return [];
  const rest = Array.from({ length }, (_, i) => i).filter((i) => i !== currentIndex);
  for (let i = rest.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [rest[i], rest[j]] = [rest[j], rest[i]];
  }
  return currentIndex >= 0 && currentIndex < length ? [currentIndex, ...rest] : rest;
}

/**
 * The next queue index in the direction asked for, or undefined at the end.
 *
 * Undefined is the signal to do whatever "the end" means in that direction - wrap for
 * repeat-all, stop at the end of the queue, restart the track for previous - and leaving
 * that decision to the callers is what lets one walker serve both.
 */
function stepThrough(queue: QueueState, direction: 1 | -1): number | undefined {
  const { items, currentIndex, shuffleMode, shuffledOrder } = queue;

  if (shuffleMode === "On" && shuffledOrder.length === items.length && items.length > 0) {
    const position = shuffledOrder.indexOf(currentIndex);
    // Not in the order - the queue changed under it - so start from its beginning.
    if (position < 0) return shuffledOrder[0];
    const next = position + direction;
    return next >= 0 && next < shuffledOrder.length ? shuffledOrder[next] : undefined;
  }

  const next = currentIndex + direction;
  return next >= 0 && next < items.length ? next : undefined;
}

/** The commands that mean "I have seen the error, move on". */
const RECOVERING_COMMANDS: ReadonlySet<PlayerCommand["type"]> = new Set([
  "play",
  "pause",
  "seek",
  "skipNext",
  "skipPrevious",
  "load",
]);

/**
 * What the transport can do from here.
 *
 * A control that is visible and does nothing is worse than one that is visibly
 * unavailable: the first teaches people the app is broken, the second tells them where
 * they are. Previous is always available once you are far enough into a track, because
 * there it restarts rather than moves.
 */
/**
 * Past this, "previous" restarts the track instead of leaving it.
 *
 * Named because two places need to agree: the behaviour, and the capability flag that
 * tells the UI whether the button does anything.
 */
const RESTART_THRESHOLD_MS = 3000;

function withTransportCapability(state: PlayerState): PlayerState {
  const { items, currentIndex, repeatMode } = state.queue;
  const hasNeighbours = items.length > 1;
  const wraps = repeatMode !== "Off" && hasNeighbours;

  const canSkipNext = hasNeighbours && (currentIndex < items.length - 1 || wraps);
  const canSkipPrevious =
    (hasNeighbours && (currentIndex > 0 || wraps)) ||
    (state.currentTrack !== undefined && state.position.elapsedMs > RESTART_THRESHOLD_MS);

  const { capabilities } = state;
  if (
    capabilities.canSkipNext === canSkipNext &&
    capabilities.canSkipPrevious === canSkipPrevious
  ) {
    return state;
  }
  return { ...state, capabilities: { ...capabilities, canSkipNext, canSkipPrevious } };
}

function isTerminal(state: PlayerState): boolean {
  return state.status === "Completed" || state.status === "Error";
}

/**
 * Puts a loaded track into the queue without destroying what is already there.
 *
 * [index] is the copy being played, when the caller knows it - skipping forward always
 * does. Without it, a track already present keeps its first position, and a new one is
 * appended. It used to replace the whole queue with a single item, so pressing play on
 * anything discarded the queue the listener had just built.
 */
function placeInQueue(queue: QueueState, track: MediaItem, index?: number): QueueState {
  if (index !== undefined && queue.items[index]?.id === track.id) {
    return { ...queue, currentIndex: index };
  }
  const existing = queue.items.findIndex((item) => item.id === track.id);
  if (existing >= 0) return { ...queue, currentIndex: existing };
  return { ...queue, items: [...queue.items, track], currentIndex: queue.items.length };
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
