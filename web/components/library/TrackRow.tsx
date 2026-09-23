"use client";

import { Artwork } from "@/components/artwork/Artwork";
import { useTrackArtwork } from "@/lib/artwork/useArtwork";
import { StoredTrack } from "@/lib/library/db";
import styles from "@/app/library/library.module.css";

/**
 * One track in the list, with its cover.
 *
 * Its own component because each row loads its own artwork, and a hook cannot be called
 * from inside a map in the parent.
 */
export function TrackRow({
  track,
  onPlay,
  onQueue,
}: {
  track: StoredTrack;
  onPlay: () => void;
  onQueue: () => void;
}) {
  const artwork = useTrackArtwork(track);

  return (
    <div className={styles.trackRowGroup}>
      <button type="button" className={styles.trackRow} onClick={onPlay}>
        <Artwork url={artwork.url} title={track.title} size="row" />
        <span className={styles.trackText}>
          <span className={styles.trackTitle}>{track.title}</span>
          <span className={styles.trackArtist}>{track.artist ?? "Unknown artist"}</span>
        </span>
      </button>
      {/*
        Queueing was a command with no way to reach it: loading a track replaces the queue,
        so before this the web queue could never hold more than one track.
      */}
      <button
        type="button"
        className={styles.trackAction}
        onClick={onQueue}
        aria-label={`Add ${track.title} to the queue`}
      >
        <span className="material-symbols-outlined" aria-hidden="true">
          queue_music
        </span>
      </button>
    </div>
  );
}
