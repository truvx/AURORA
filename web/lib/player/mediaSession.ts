import { PlayerCoordinator } from "./coordinator";
import { PlayerState } from "./types";

/**
 * Publishes playback to the OS via the Media Session API, so hardware keys, lock screens,
 * and browser media hubs control AURORA.
 *
 * Every handler routes back through the coordinator rather than touching the audio element,
 * keeping one canonical source of playback state.
 *
 * Media Session is unsupported in some browsers; the binder reports that as a capability
 * rather than failing, since the in-app controls still work.
 */
export function bindMediaSession(coordinator: PlayerCoordinator): () => void {
  if (typeof navigator === "undefined" || !("mediaSession" in navigator)) {
    coordinator.setCapabilities({ mediaSession: false });
    return () => {};
  }

  const session = navigator.mediaSession;
  coordinator.setCapabilities({ mediaSession: true });

  const handlers: Array<[MediaSessionAction, MediaSessionActionHandler]> = [
    ["play", () => void coordinator.dispatch({ type: "play" })],
    ["pause", () => void coordinator.dispatch({ type: "pause" })],
    ["nexttrack", () => void coordinator.dispatch({ type: "skipNext" })],
    ["previoustrack", () => void coordinator.dispatch({ type: "skipPrevious" })],
    [
      "seekto",
      (details) => {
        if (details.seekTime !== undefined && details.seekTime !== null) {
          void coordinator.dispatch({
            type: "seek",
            positionMs: details.seekTime * 1000,
          });
        }
      },
    ],
  ];

  for (const [action, handler] of handlers) {
    try {
      session.setActionHandler(action, handler);
    } catch {
      // Browsers reject actions they do not implement. Skipping one is not a failure:
      // the remaining controls stay usable.
    }
  }

  const unsubscribe = coordinator.subscribe((state) => publish(session, state));
  publish(session, coordinator.getState());

  return () => {
    unsubscribe();
    for (const [action] of handlers) {
      try {
        session.setActionHandler(action, null);
      } catch {
        // Nothing to clean up if the browser never accepted the handler.
      }
    }
  };
}

function publish(session: MediaSession, state: PlayerState): void {
  const track = state.currentTrack;
  if (!track) {
    session.playbackState = "none";
    session.metadata = null;
    return;
  }

  session.playbackState = state.status === "Playing" ? "playing" : "paused";
  session.metadata = new MediaMetadata({
    title: track.title,
    artist: track.artist ?? "Unknown artist",
    album: track.album ?? "",
    artwork: track.artworkUrl ? [{ src: track.artworkUrl }] : [],
  });

  // Position state needs a real duration; reporting a guessed one would put a wrong
  // timeline on the user's lock screen.
  const durationMs = state.position.durationMs;
  if (durationMs && durationMs > 0 && typeof session.setPositionState === "function") {
    try {
      session.setPositionState({
        duration: durationMs / 1000,
        position: Math.min(state.position.elapsedMs, durationMs) / 1000,
        playbackRate: 1,
      });
    } catch {
      // Thrown when position exceeds duration during a seek; the next tick corrects it.
    }
  }
}
