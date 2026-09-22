"use client";

import { useState } from "react";
import { Artwork } from "@/components/artwork/Artwork";
import { GlassCard } from "@/components/glass/Glass";
import { CoverFlow } from "@/components/player/CoverFlow";
import { useArtworkById } from "@/lib/artwork/useArtwork";
import { usePlayerCommands, usePlayerState } from "@/lib/player/PlayerProvider";
import { PlayerState } from "@/lib/player/types";
import styles from "./now-playing.module.css";

const selectTrack = (state: PlayerState) => state.currentTrack;
const selectQueue = (state: PlayerState) => state.queue;
const selectStatus = (state: PlayerState) => state.status;
const selectError = (state: PlayerState) => state.error;
const selectPosition = (state: PlayerState) => state.position;

export default function NowPlayingPage() {
  const track = usePlayerState(selectTrack);
  const queue = usePlayerState(selectQueue);
  const status = usePlayerState(selectStatus);
  const error = usePlayerState(selectError);
  const position = usePlayerState(selectPosition);
  const dispatch = usePlayerCommands();
  const artwork = useArtworkById(track?.id);
  const [queueOpen, setQueueOpen] = useState(false);

  if (!track) {
    return (
      <div className={styles.page}>
        <h1>Now playing</h1>
        <GlassCard>
          <h2>Nothing is playing</h2>
          <p>Choose a track from your library to start listening.</p>
        </GlassCard>
      </div>
    );
  }

  const playing = status === "Playing" || status === "Buffering";
  const elapsed = position.elapsedMs;
  const duration = position.durationMs;
  const progress = duration && duration > 0 ? Math.min(elapsed / duration, 1) : 0;

  return (
    <div className={styles.stage}>
      {/*
        The track is the subject of this screen, so it carries the heading. "Now playing"
        is the label that gives it context, not the biggest thing on the page.
      */}
      <h1 className={styles.srOnly}>Now playing: {track.title}</h1>

      <CoverFlow
        items={queue.items}
        currentIndex={queue.currentIndex}
        onSelect={(item) => dispatch({ type: "load", track: item, playWhenReady: true })}
      />

      {/* Status stays in words, never a colour or an icon alone. */}
      <p className={styles.status} role="status">
        {error ? `⚠ ${error}` : statusLabel(status)}
      </p>

      {/* The floating transport: one glass bar over the stage, as in the reference. */}
      <div className={styles.transport}>
        <div className={styles.controls}>
          <button
            type="button"
            className={styles.control}
            onClick={() => dispatch({ type: "skipPrevious" })}
            aria-label="Previous track"
          >
            <span className="material-symbols-outlined" aria-hidden="true">skip_previous</span>
          </button>
          <button
            type="button"
            className={`${styles.control} ${styles.primary}`}
            onClick={() => dispatch({ type: playing ? "pause" : "play" })}
            aria-label={playing ? "Pause" : "Play"}
          >
            <span className="material-symbols-outlined" aria-hidden="true">
              {playing ? "pause" : "play_arrow"}
            </span>
          </button>
          <button
            type="button"
            className={styles.control}
            onClick={() => dispatch({ type: "skipNext" })}
            aria-label="Next track"
          >
            <span className="material-symbols-outlined" aria-hidden="true">skip_next</span>
          </button>
        </div>

        <div className={styles.nowPlaying}>
          <Artwork url={artwork.url} title={track.title} size="row" />
          <div className={styles.nowPlayingText}>
            <p className={styles.nowPlayingTitle}>{track.title}</p>
            <p className={styles.nowPlayingArtist}>{track.artist ?? "Unknown artist"}</p>
          </div>
          {/*
            A progress indication, not a scrubber: it reports, it does not accept input, so
            it is a progressbar rather than a slider pretending to be one.
          */}
          <div
            className={styles.progress}
            role="progressbar"
            aria-label="Playback position"
            aria-valuemin={0}
            aria-valuemax={duration ?? 0}
            aria-valuenow={elapsed}
            aria-valuetext={`${formatTime(elapsed)}${duration ? ` of ${formatTime(duration)}` : ""}`}
          >
            <span className={styles.progressFill} style={{ transform: `scaleX(${progress})` }} />
          </div>
        </div>

        <button
          type="button"
          className={styles.control}
          onClick={() => setQueueOpen((open) => !open)}
          aria-expanded={queueOpen}
          aria-controls="queue-panel"
          aria-label={queueOpen ? "Hide queue" : `Show queue, ${queue.items.length} tracks`}
        >
          <span className="material-symbols-outlined" aria-hidden="true">queue_music</span>
        </button>
      </div>

      {queueOpen && (
        <section id="queue-panel" aria-labelledby="queue-heading" className={styles.queuePanel}>
          <h2 id="queue-heading">Queue</h2>
          {queue.items.length === 0 ? (
            <p className={styles.secondary}>The queue is empty.</p>
          ) : (
            <ol className={styles.queue}>
              {queue.items.map((item, index) => {
                const isCurrent = index === queue.currentIndex;
                return (
                  <li key={`${item.id}-${index}`}>
                    <div className={styles.queueRow}>
                      <button
                        type="button"
                        className={styles.queueButton}
                        onClick={() =>
                          dispatch({ type: "load", track: item, playWhenReady: true })
                        }
                        // Position is announced so a screen reader user knows where they are
                        // in the queue, not just which track this is.
                        aria-label={`Play ${item.title}, track ${index + 1} of ${
                          queue.items.length
                        }${isCurrent ? ", currently playing" : ""}`}
                        aria-current={isCurrent ? "true" : undefined}
                      >
                        <span aria-hidden="true" className={styles.queueIndex}>
                          {isCurrent ? "▶" : index + 1}
                        </span>
                        <span className={styles.queueTitle}>{item.title}</span>
                        <span className={styles.secondary}>
                          {item.artist ?? "Unknown artist"}
                        </span>
                      </button>
                      <button
                        type="button"
                        className={styles.removeButton}
                        onClick={() =>
                          dispatch({ type: "removeFromQueue", trackId: item.id })
                        }
                        aria-label={`Remove ${item.title} from the queue`}
                      >
                        <span className="material-symbols-outlined" aria-hidden="true">
                          close
                        </span>
                      </button>
                    </div>
                  </li>
                );
              })}
            </ol>
          )}
        </section>
      )}
    </div>
  );
}

function formatTime(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}

function statusLabel(status: PlayerState["status"]): string {
  switch (status) {
    case "Playing":
      return "Playing";
    case "Paused":
      return "Paused";
    case "Buffering":
      return "Buffering…";
    case "Loading":
      return "Loading…";
    case "Completed":
      return "Finished";
    case "Seeking":
      return "Seeking…";
    case "Ready":
      return "Ready to play";
    default:
      return "Idle";
  }
}
