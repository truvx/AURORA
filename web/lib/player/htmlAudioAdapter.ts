import { AdapterListener, MediaItem, PlaybackAdapter } from "./types";

/**
 * Local playback through a single HTMLAudioElement.
 *
 * One element for the whole app: creating one per track leaks decoders and loses the
 * user-gesture grant that autoplay policy attaches to an element. The element is never held
 * in React state - only this adapter touches it.
 */
export class HtmlAudioAdapter implements PlaybackAdapter {
  private readonly audio: HTMLAudioElement;
  private listener?: AdapterListener;
  private positionTimer?: ReturnType<typeof setInterval>;

  constructor(audio?: HTMLAudioElement) {
    this.audio = audio ?? new Audio();
    this.audio.preload = "metadata";
    this.attachEvents();
  }

  setListener(listener: AdapterListener): void {
    this.listener = listener;
  }

  async load(_track: MediaItem, src: string, playWhenReady: boolean): Promise<void> {
    this.audio.src = src;
    this.audio.load();
    if (playWhenReady) await this.play();
  }

  async play(): Promise<void> {
    try {
      await this.audio.play();
    } catch (error) {
      // NotAllowedError means the browser wants a user gesture first. That is a normal
      // browser policy, not a playback failure, and the UI must be able to say so.
      if (error instanceof DOMException && error.name === "NotAllowedError") {
        this.listener?.onAutoplayBlocked();
        return;
      }
      this.listener?.onError(
        error instanceof Error ? error.message : "Playback could not be started"
      );
    }
  }

  pause(): void {
    this.audio.pause();
  }

  seek(positionMs: number): void {
    if (Number.isFinite(this.audio.duration)) {
      this.audio.currentTime = Math.min(positionMs / 1000, this.audio.duration);
    } else {
      this.audio.currentTime = positionMs / 1000;
    }
  }

  setVolume(volume: number): void {
    this.audio.volume = volume;
  }

  release(): void {
    this.stopPositionTimer();
    this.audio.pause();
    this.audio.removeAttribute("src");
    this.audio.load();
  }

  /** Exposed so the Media Session binder can mirror element state. */
  get element(): HTMLAudioElement {
    return this.audio;
  }

  private attachEvents(): void {
    this.audio.addEventListener("loadedmetadata", () =>
      this.listener?.onReady(this.durationMs())
    );
    this.audio.addEventListener("playing", () => {
      this.listener?.onPlaying();
      this.startPositionTimer();
    });
    this.audio.addEventListener("pause", () => {
      this.listener?.onPaused();
      this.stopPositionTimer();
    });
    this.audio.addEventListener("waiting", () => this.listener?.onBuffering(true));
    this.audio.addEventListener("canplay", () => this.listener?.onBuffering(false));
    this.audio.addEventListener("ended", () => {
      this.stopPositionTimer();
      this.listener?.onCompleted();
    });
    this.audio.addEventListener("error", () => {
      this.stopPositionTimer();
      this.listener?.onError(describeMediaError(this.audio.error));
    });
  }

  /**
   * Polled rather than driven by timeupdate: browsers fire timeupdate irregularly (roughly
   * 4Hz, and throttled in background tabs), which makes a scrubber visibly stutter.
   */
  private startPositionTimer(): void {
    this.stopPositionTimer();
    this.positionTimer = setInterval(() => {
      this.listener?.onPosition(
        Math.round(this.audio.currentTime * 1000),
        this.durationMs(),
        this.bufferedMs()
      );
    }, 250);
  }

  private stopPositionTimer(): void {
    if (this.positionTimer !== undefined) {
      clearInterval(this.positionTimer);
      this.positionTimer = undefined;
    }
  }

  /** Streams report Infinity and unloaded media reports NaN; both mean unknown. */
  private durationMs(): number | undefined {
    const { duration } = this.audio;
    return Number.isFinite(duration) ? Math.round(duration * 1000) : undefined;
  }

  private bufferedMs(): number | undefined {
    const { buffered } = this.audio;
    if (buffered.length === 0) return undefined;
    return Math.round(buffered.end(buffered.length - 1) * 1000);
  }
}

function describeMediaError(error: MediaError | null): string {
  if (!error) return "This track could not be played";
  switch (error.code) {
    case MediaError.MEDIA_ERR_ABORTED:
      return "Playback was cancelled";
    case MediaError.MEDIA_ERR_NETWORK:
      return "Playback stopped because of a network problem";
    case MediaError.MEDIA_ERR_DECODE:
      return "This file could not be decoded";
    case MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED:
      return "This browser cannot play this file format";
    default:
      return "This track could not be played";
  }
}
