/**
 * Web playback contracts.
 *
 * These mirror the Android domain vocabulary (docs/PLAYER_STATE_MACHINE.md) so the two
 * clients describe the same concepts, but they are a separate implementation: no Android
 * playback, URI, or storage abstraction is reused here, per docs/WEB_ARCHITECTURE.md.
 */

export type ProviderKind = "LOCAL" | "YOUTUBE";

export type PlaybackStatus =
  | "Idle"
  | "Loading"
  | "Ready"
  | "Playing"
  | "Paused"
  | "Buffering"
  | "Seeking"
  | "Completed"
  | "Error";

export type RepeatMode = "Off" | "One" | "All";
export type ShuffleMode = "Off" | "On";

export interface TechnicalMetadata {
  /** Container or codec as reported by the browser; unknown stays undefined. */
  readonly codec?: string;
  readonly sampleRate?: number;
  readonly channels?: number;
  readonly durationMs?: number;
  /** Derived from container only. The browser exposes no reliable lossless signal. */
  readonly isLossless?: boolean;
}

export interface MediaItem {
  readonly id: string;
  readonly provider: ProviderKind;
  readonly title: string;
  readonly artist?: string;
  readonly album?: string;
  readonly artworkUrl?: string;
  readonly technicalMetadata?: TechnicalMetadata;
  readonly isAvailable: boolean;
}

export interface PlaybackPosition {
  readonly elapsedMs: number;
  /** undefined means genuinely unknown, never zero-as-unknown. */
  readonly durationMs?: number;
  readonly bufferedMs?: number;
}

export interface QueueState {
  readonly items: readonly MediaItem[];
  readonly currentIndex: number;
  readonly repeatMode: RepeatMode;
  readonly shuffleMode: ShuffleMode;
  /**
   * The order to play in while shuffling: queue indices, not tracks.
   *
   * Indices rather than a reordered copy of the items, so the queue the listener sees
   * never changes underneath them - shuffle changes what comes next, not what the list
   * says. Empty whenever shuffleMode is Off. The same shape the Android client uses.
   */
  readonly shuffledOrder: readonly number[];
}

/**
 * Browser capabilities resolved at runtime. The spec requires limitations to be explicit
 * and recoverable rather than presented as app failures.
 */
export interface WebCapabilities {
  /** navigator.mediaSession exists. */
  readonly mediaSession: boolean;
  /*
   * Whether the transport can actually do the thing, so a control is never dead.
   *
   * Derived rather than stored: it is a question about the queue, the repeat mode and how
   * far into the track we are, all of which already live in the state.
   */
  readonly canSkipNext: boolean;
  readonly canSkipPrevious: boolean;
  /** showDirectoryPicker exists (Chromium today). */
  readonly directoryPicker: boolean;
  /** Playback was blocked by autoplay policy and needs a user gesture. */
  readonly autoplayBlocked: boolean;
}

export interface PlayerState {
  readonly status: PlaybackStatus;
  readonly currentTrack?: MediaItem;
  readonly position: PlaybackPosition;
  readonly queue: QueueState;
  readonly volume: number;
  readonly error?: string;
  readonly capabilities: WebCapabilities;
}

export const initialPlayerState: PlayerState = {
  status: "Idle",
  position: { elapsedMs: 0 },
  queue: { items: [], currentIndex: -1, repeatMode: "Off", shuffleMode: "Off", shuffledOrder: [] },
  volume: 1,
  capabilities: {
    mediaSession: false,
    directoryPicker: false,
    autoplayBlocked: false,
    canSkipNext: false,
    canSkipPrevious: false,
  },
};

export type PlayerCommand =
  | { type: "load"; track: MediaItem; playWhenReady?: boolean }
  /**
   * Play a list from one of its tracks.
   *
   * Distinct from `load`, which is one track arriving on its own - from the AI, say - and
   * joins the queue without disturbing it. A list is a *context*: tapping the third song
   * of an album means "play this album from here", so it replaces the queue. Without this
   * every entry point loaded a single track, a fresh play left nothing to skip to, and
   * next and previous were dead controls.
   */
  | { type: "playList"; items: readonly MediaItem[]; startIndex: number }
  | { type: "play" }
  | { type: "pause" }
  | { type: "seek"; positionMs: number }
  | { type: "skipNext" }
  | { type: "skipPrevious" }
  | { type: "addToQueue"; track: MediaItem }
  | { type: "removeFromQueue"; trackId: string }
  | { type: "clearQueue" }
  /**
   * Puts a whole saved queue back in one step.
   *
   * One command rather than a run of appends: appending restored the tracks but never the
   * pointer or the modes, and every append looked like a queue change to whatever is
   * persisting it - so the snapshot was overwritten with the defaults before the restore
   * had finished, and the real one was gone.
   */
  | {
      type: "restoreQueue";
      items: MediaItem[];
      currentIndex: number;
      repeatMode: RepeatMode;
      shuffleMode: ShuffleMode;
    }
  | { type: "setVolume"; volume: number }
  | { type: "setRepeat"; mode: RepeatMode }
  | { type: "setShuffle"; mode: ShuffleMode };

/**
 * What the coordinator needs from a playback surface. Implemented by the HTML audio
 * element for local files, and later by the YouTube IFrame for provider playback.
 */
export interface PlaybackAdapter {
  load(track: MediaItem, src: string, playWhenReady: boolean): Promise<void>;
  play(): Promise<void>;
  pause(): void;
  seek(positionMs: number): void;
  setVolume(volume: number): void;
  release(): void;
  /** Registers the sink the adapter reports engine events to. */
  setListener(listener: AdapterListener): void;
}

export interface AdapterListener {
  onReady(durationMs?: number): void;
  onPlaying(): void;
  onPaused(): void;
  onBuffering(isBuffering: boolean): void;
  /**
   * A seek finished.
   *
   * Needed because a *paused* element emits only `seeking`, `seeked`, `canplay` - no
   * `playing`, no `pause` - so without this there is no event that ends the seek and the
   * status stays "Seeking" for good.
   */
  onSeeked(): void;
  onPosition(elapsedMs: number, durationMs?: number, bufferedMs?: number): void;
  onCompleted(): void;
  onError(message: string): void;
  /** Playback was refused for lack of a user gesture; recoverable, not a failure. */
  onAutoplayBlocked(): void;
}

/** Resolves a playable source for a track; local tracks resolve to object URLs. */
export type SourceResolver = (track: MediaItem) => Promise<string | undefined>;
