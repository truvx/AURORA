import { AdapterListener, MediaItem, PlaybackAdapter } from "./types";

/**
 * YouTube playback through the official IFrame Player API.
 *
 * Policy boundary, from docs/YOUTUBE_CAPABILITY_MATRIX.md - these are requirements, not
 * preferences:
 *
 * - The official player must be visible and recognisable. AURORA never extracts audio,
 *   invents stream URLs, hides the player, or plays with the surface off-screen.
 * - Attribution, branding, controls, and ads are the player's own; nothing overlays or
 *   obscures them.
 * - Quality is provider-determined. AURORA never reports bitrate, codec, or lossless state
 *   for a YouTube track.
 * - There is no background or screen-off playback. When the player is not visible, YouTube
 *   playback stops being available; that is stated, not worked around.
 *
 * The element id is passed in so the surface stays owned by the React tree while only this
 * adapter talks to the player.
 */

const IFRAME_API_SRC = "https://www.youtube.com/iframe_api";

/** Player states from the IFrame API. */
const STATE_ENDED = 0;
const STATE_PLAYING = 1;
const STATE_PAUSED = 2;
const STATE_BUFFERING = 3;

interface YouTubePlayer {
  playVideo(): void;
  pauseVideo(): void;
  seekTo(seconds: number, allowSeekAhead: boolean): void;
  setVolume(volume: number): void;
  loadVideoById(videoId: string): void;
  cueVideoById(videoId: string): void;
  getCurrentTime(): number;
  getDuration(): number;
  getPlayerState(): number;
  destroy(): void;
}

export class YouTubeIframeAdapter implements PlaybackAdapter {
  private player?: YouTubePlayer;
  private listener?: AdapterListener;
  private positionTimer?: ReturnType<typeof setInterval>;
  private ready = false;
  private pendingVideoId?: string;
  private pendingAutoplay = false;

  constructor(private readonly elementId: string) {}

  setListener(listener: AdapterListener): void {
    this.listener = listener;
  }

  /** Loads the IFrame API and creates the player inside the host element. */
  async initialise(): Promise<void> {
    if (this.player) return;
    const YT = await loadIframeApi();

    this.player = new YT.Player(this.elementId, {
      // Height and width come from CSS; the surface must remain visible either way.
      playerVars: {
        // Autoplay stays off by default: browser policy can block it, and the capability
        // matrix requires explicit user intent first.
        autoplay: 0,
        // Official controls stay on. Hiding them would obscure required player behaviour.
        controls: 1,
        playsinline: 1,
      },
      events: {
        onReady: () => {
          this.ready = true;
          if (this.pendingVideoId) {
            const videoId = this.pendingVideoId;
            this.pendingVideoId = undefined;
            this.loadVideo(videoId, this.pendingAutoplay);
          }
        },
        onStateChange: (event: { data: number }) => this.onStateChange(event.data),
        onError: () =>
          // A provider restriction is not an app failure, and the message says so.
          this.listener?.onError(
            "YouTube cannot play this video here. It may be restricted, private, or not embeddable."
          ),
      },
    }) as unknown as YouTubePlayer;
  }

  async load(track: MediaItem, src: string, playWhenReady: boolean): Promise<void> {
    const videoId = extractVideoId(track.id) ?? extractVideoId(src);
    if (!videoId) {
      this.listener?.onError("This YouTube track has no usable video id.");
      return;
    }

    await this.initialise();
    if (!this.ready) {
      // Queued until onReady; loading before the player exists silently does nothing.
      this.pendingVideoId = videoId;
      this.pendingAutoplay = playWhenReady;
      return;
    }
    this.loadVideo(videoId, playWhenReady);
  }

  private loadVideo(videoId: string, playWhenReady: boolean): void {
    // cue vs load is the difference between preparing and starting: cueing respects the
    // "autoplay only after explicit intent" rule.
    if (playWhenReady) this.player?.loadVideoById(videoId);
    else this.player?.cueVideoById(videoId);
  }

  async play(): Promise<void> {
    this.player?.playVideo();
  }

  pause(): void {
    this.player?.pauseVideo();
  }

  seek(positionMs: number): void {
    this.player?.seekTo(positionMs / 1000, true);
  }

  setVolume(volume: number): void {
    // The IFrame API takes 0-100 where the rest of the app uses 0-1.
    this.player?.setVolume(Math.round(volume * 100));
  }

  release(): void {
    this.stopPositionTimer();
    this.player?.destroy();
    this.player = undefined;
    this.ready = false;
  }

  private onStateChange(state: number): void {
    switch (state) {
      case STATE_PLAYING:
        this.listener?.onPlaying();
        this.listener?.onBuffering(false);
        this.startPositionTimer();
        break;
      case STATE_PAUSED:
        this.listener?.onPaused();
        this.stopPositionTimer();
        break;
      case STATE_BUFFERING:
        this.listener?.onBuffering(true);
        break;
      case STATE_ENDED:
        this.stopPositionTimer();
        this.listener?.onCompleted();
        break;
    }
  }

  /**
   * Reports position only while the player is actually playing.
   *
   * An unguarded timer reports 0/0 whenever nothing is loaded, and on Android exactly that
   * overwrote the position of a locally playing track twice a second.
   */
  private startPositionTimer(): void {
    this.stopPositionTimer();
    this.positionTimer = setInterval(() => {
      const player = this.player;
      if (!player) return;
      const state = player.getPlayerState();
      if (state !== STATE_PLAYING && state !== STATE_BUFFERING) return;

      const duration = player.getDuration();
      this.listener?.onPosition(
        Math.round(player.getCurrentTime() * 1000),
        // Duration is 0 until metadata arrives; unknown must stay unknown.
        duration > 0 ? Math.round(duration * 1000) : undefined,
        undefined
      );
    }, 500);
  }

  private stopPositionTimer(): void {
    if (this.positionTimer !== undefined) {
      clearInterval(this.positionTimer);
      this.positionTimer = undefined;
    }
  }
}

/** Accepts "youtube:VIDEOID", a watch URL, or a bare id. */
export function extractVideoId(value: string): string | undefined {
  if (!value) return undefined;
  if (value.startsWith("youtube:")) return value.slice("youtube:".length) || undefined;

  try {
    const url = new URL(value);
    if (url.hostname === "youtu.be") return url.pathname.slice(1) || undefined;
    if (url.hostname.endsWith("youtube.com")) {
      return url.searchParams.get("v") ?? undefined;
    }
  } catch {
    // Not a URL; fall through to the bare-id check.
  }
  return /^[A-Za-z0-9_-]{11}$/.test(value) ? value : undefined;
}

interface YouTubeApi {
  Player: new (elementId: string, options: unknown) => unknown;
}

let apiPromise: Promise<YouTubeApi> | undefined;

/** Loads the official IFrame API once per page. */
function loadIframeApi(): Promise<YouTubeApi> {
  if (apiPromise) return apiPromise;

  apiPromise = new Promise<YouTubeApi>((resolve, reject) => {
    const existing = (window as unknown as { YT?: YouTubeApi }).YT;
    if (existing?.Player) {
      resolve(existing);
      return;
    }

    const previous = (window as unknown as { onYouTubeIframeAPIReady?: () => void })
      .onYouTubeIframeAPIReady;
    (window as unknown as { onYouTubeIframeAPIReady: () => void }).onYouTubeIframeAPIReady =
      () => {
        previous?.();
        const api = (window as unknown as { YT?: YouTubeApi }).YT;
        if (api?.Player) resolve(api);
        else reject(new Error("The YouTube player failed to load."));
      };

    const script = document.createElement("script");
    script.src = IFRAME_API_SRC;
    script.async = true;
    script.onerror = () =>
      reject(new Error("The YouTube player could not be loaded. Check your connection."));
    document.head.appendChild(script);
  });

  return apiPromise;
}
