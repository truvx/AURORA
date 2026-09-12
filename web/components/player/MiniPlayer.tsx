"use client";

import { useCallback } from "react";
import { GlassSurface } from "@/components/glass/Glass";
import { usePlayerCommands, usePlayerState } from "@/lib/player/PlayerProvider";
import { PlayerState } from "@/lib/player/types";
import styles from "./MiniPlayer.module.css";

/**
 * Persistent transport controls.
 *
 * Each piece subscribes to the narrowest slice it needs, so the four-times-a-second position
 * updates re-render only the scrubber and its timer - not the artwork, title, or buttons.
 */
export function MiniPlayer() {
  const dispatch = usePlayerCommands();
  const track = usePlayerState(selectTrack);
  const status = usePlayerState(selectStatus);
  const autoplayBlocked = usePlayerState(selectAutoplayBlocked);
  const error = usePlayerState(selectError);

  if (!track) return null;

  const isPlaying = status === "Playing" || status === "Buffering";

  return (
    <GlassSurface level="elevated" className={styles.miniPlayer}>
      <div
        className={styles.row}
        role="region"
        aria-label={`Now playing: ${track.title}${track.artist ? ` by ${track.artist}` : ""}`}
      >
        <div className={styles.meta}>
          <p className={styles.title}>{track.title}</p>
          <p className={styles.secondary}>
            {/* Failures carry words and a symbol, never colour alone. */}
            {error ? `⚠ ${error}` : (track.artist ?? "Unknown artist")}
          </p>
        </div>

        <Scrubber />

        <div className={styles.controls}>
          <button
            type="button"
            onClick={() => dispatch({ type: "skipPrevious" })}
            aria-label="Previous track"
            className={styles.control}
          >
            <span className="material-symbols-outlined" aria-hidden="true">
              skip_previous
            </span>
          </button>
          <button
            type="button"
            onClick={() => dispatch({ type: isPlaying ? "pause" : "play" })}
            aria-label={isPlaying ? "Pause" : "Play"}
            className={styles.control}
          >
            <span className="material-symbols-outlined" aria-hidden="true">
              {isPlaying ? "pause" : "play_arrow"}
            </span>
          </button>
          <button
            type="button"
            onClick={() => dispatch({ type: "skipNext" })}
            aria-label="Next track"
            className={styles.control}
          >
            <span className="material-symbols-outlined" aria-hidden="true">
              skip_next
            </span>
          </button>
        </div>
      </div>

      {autoplayBlocked && (
        // A browser policy, not a fault. Say what happened and what fixes it.
        <p className={styles.notice} role="status">
          Your browser blocked playback until you interact with the page. Press play to start.
        </p>
      )}
    </GlassSurface>
  );
}

/** Isolated so position ticks do not re-render the rest of the player. */
function Scrubber() {
  const dispatch = usePlayerCommands();
  const position = usePlayerState(selectPosition);

  const onSeek = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      dispatch({ type: "seek", positionMs: Number(event.target.value) });
    },
    [dispatch]
  );

  const knownDuration = position.durationMs !== undefined && position.durationMs > 0;

  return (
    <div className={styles.scrubber}>
      <span className={styles.time}>{formatTime(position.elapsedMs)}</span>
      <input
        type="range"
        min={0}
        max={knownDuration ? position.durationMs : 1}
        value={knownDuration ? Math.min(position.elapsedMs, position.durationMs!) : 0}
        onChange={onSeek}
        disabled={!knownDuration}
        className={styles.range}
        aria-label={
          knownDuration
            ? `Playback position, ${spokenTime(position.elapsedMs)} of ${spokenTime(
                position.durationMs!
              )}`
            : "Playback position. Duration unknown."
        }
      />
      {/* Unknown duration shows as unknown rather than as 0:00. */}
      <span className={styles.time}>
        {knownDuration ? formatTime(position.durationMs!) : "--:--"}
      </span>
    </div>
  );
}

const selectTrack = (state: PlayerState) => state.currentTrack;
const selectStatus = (state: PlayerState) => state.status;
const selectPosition = (state: PlayerState) => state.position;
const selectError = (state: PlayerState) => state.error;
const selectAutoplayBlocked = (state: PlayerState) => state.capabilities.autoplayBlocked;

function formatTime(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/** "2 minutes 5 seconds" reads correctly aloud; "2:05" does not. */
function spokenTime(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  const parts: string[] = [];
  if (minutes > 0) parts.push(`${minutes} minute${minutes === 1 ? "" : "s"}`);
  parts.push(`${seconds} second${seconds === 1 ? "" : "s"}`);
  return parts.join(" ");
}
