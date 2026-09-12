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
}

/**
 * Browser capabilities resolved at runtime. The spec requires limitations to be explicit
 * and recoverable rather than presented as app failures.
 */
export interface WebCapabilities {
  /** navigator.mediaSession exists. */
  readonly mediaSession: boolean;
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
  queue: { items: [], currentIndex: -1, repeatMode: "Off", shuffleMode: "Off" },
  volume: 1,
  capabilities: { mediaSession: false, directoryPicker: false, autoplayBlocked: false },
};

export type PlayerCommand =
  | { type: "load"; track: MediaItem; playWhenReady?: boolean }
  | { type: "play" }
  | { type: "pause" }
  | { type: "seek"; positionMs: number }
  | { type: "skipNext" }
  | { type: "skipPrevious" }
  | { type: "addToQueue"; track: MediaItem }
  | { type: "removeFromQueue"; trackId: string }
  | { type: "clearQueue" }
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
  onPosition(elapsedMs: number, durationMs?: number, bufferedMs?: number): void;
  onCompleted(): void;
  onError(message: string): void;
  /** Playback was refused for lack of a user gesture; recoverable, not a failure. */
  onAutoplayBlocked(): void;
}

/** Resolves a playable source for a track; local tracks resolve to object URLs. */
export type SourceResolver = (track: MediaItem) => Promise<string | undefined>;
