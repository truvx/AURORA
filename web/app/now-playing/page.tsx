"use client";

import { GlassCard } from "@/components/glass/Glass";
import { usePlayerCommands, usePlayerState } from "@/lib/player/PlayerProvider";
import { PlayerState } from "@/lib/player/types";
import styles from "./now-playing.module.css";

const selectTrack = (state: PlayerState) => state.currentTrack;
const selectQueue = (state: PlayerState) => state.queue;
const selectStatus = (state: PlayerState) => state.status;
const selectError = (state: PlayerState) => state.error;

export default function NowPlayingPage() {
  const track = usePlayerState(selectTrack);
  const queue = usePlayerState(selectQueue);
  const status = usePlayerState(selectStatus);
  const error = usePlayerState(selectError);
  const dispatch = usePlayerCommands();

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

  return (
    <div className={styles.page}>
      {/*
        The track is the subject of this screen, so it carries the heading and the largest
        type. "Now playing" is the label that gives it context - setting that as the biggest
        thing on the page puts the emphasis on the state rather than on the music.
      */}
      <p className={styles.eyebrow}>Now playing</p>

      <GlassCard>
        <h1 className={styles.title}>{track.title}</h1>
        <p className={styles.artist}>{track.artist ?? "Unknown artist"}</p>
        {track.album && <p className={styles.secondary}>{track.album}</p>}
        {/* Status is words, not a colour or an icon alone. */}
        <p className={styles.status} role="status">
          {error ? `⚠ ${error}` : statusLabel(status)}
        </p>
      </GlassCard>

      <section aria-labelledby="queue-heading">
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
    </div>
  );
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
