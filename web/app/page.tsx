"use client";

import { useEffect, useState } from "react";
import { Artwork } from "@/components/artwork/Artwork";
import { TrackRow } from "@/components/library/TrackRow";
import { useTrackArtwork } from "@/lib/artwork/useArtwork";
import { StoredTrack } from "@/lib/library/db";
import { toMediaItem } from "@/lib/library/fileSystem";
import { TrackGroup, groupByAlbum } from "@/lib/library/grouping";
import { useLibrary, usePlayerCommands } from "@/lib/player/PlayerProvider";
import styles from "./home.module.css";

const RECENT_LIMIT = 10;
const ALBUM_LIMIT = 10;

/**
 * Listen Now: what was playing, and what to put on next.
 *
 * Everything here comes off this device - the listening history and the library that has
 * already been scanned. There is no editorial shelf, because AURORA has nobody writing
 * one, and a row of things nobody chose would be filler pretending to be a recommendation.
 */
export default function HomePage() {
  const { repository, history } = useLibrary();
  const dispatch = usePlayerCommands();

  const [tracks, setTracks] = useState<StoredTrack[]>([]);
  const [recent, setRecent] = useState<StoredTrack[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    if (!repository || !history) return;
    let cancelled = false;

    void (async () => {
      const [all, recentIds] = await Promise.all([
        repository.getAll().catch(() => []),
        history.getRecentlyPlayed(RECENT_LIMIT).catch(() => [] as string[]),
      ]);
      if (cancelled) return;

      const byId = new Map(all.map((track) => [track.id, track]));
      setTracks(all);
      // A track played and since removed from the library is dropped rather than shown as
      // a row that cannot play.
      setRecent(recentIds.map((id) => byId.get(id)).filter((t): t is StoredTrack => !!t));
      setLoaded(true);
    })();

    return () => {
      cancelled = true;
    };
  }, [repository, history]);

  const albums = groupByAlbum(tracks).slice(0, ALBUM_LIMIT);

  return (
    <div className={styles.page}>
      <h1 className="aurora-display">Listen Now</h1>

      <section aria-labelledby="recent-heading">
        <h2 id="recent-heading" className={styles.sectionTitle}>
          Recently played
        </h2>
        {recent.length > 0 ? (
          <ul className={styles.shelf}>
            {recent.map((track) => (
              <li key={track.id}>
                <TrackCard
                  track={track}
                  subtitle={track.artist ?? "Unknown artist"}
                  onPlay={() =>
                    dispatch({ type: "load", track: toMediaItem(track), playWhenReady: true })
                  }
                />
              </li>
            ))}
          </ul>
        ) : (
          <p className={styles.empty}>
            {loaded && tracks.length === 0
              ? "Choose your music folder in the library to get started."
              : "Play something, and it appears here."}
          </p>
        )}
      </section>

      {albums.length > 0 && (
        <section aria-labelledby="albums-heading">
          <h2 id="albums-heading" className={styles.sectionTitle}>
            Albums
          </h2>
          <ul className={styles.shelf}>
            {albums.map((album) => (
              <li key={album.key}>
                <AlbumCard
                  album={album}
                  tracks={album.trackIds
                    .map((id) => tracks.find((track) => track.id === id))
                    .filter((track): track is StoredTrack => !!track)}
                  onPlay={() => {
                    const inAlbum = album.trackIds
                      .map((id) => tracks.find((track) => track.id === id))
                      .filter((track): track is StoredTrack => !!track);
                    const [first, ...rest] = inAlbum;
                    if (!first) return;
                    dispatch({ type: "load", track: toMediaItem(first), playWhenReady: true });
                    // The rest queue behind it, so an album plays as an album.
                    for (const track of rest) {
                      dispatch({ type: "addToQueue", track: toMediaItem(track) });
                    }
                  }}
                />
              </li>
            ))}
          </ul>
        </section>
      )}

      {tracks.length > 0 && (
        <section aria-labelledby="tracks-heading">
          <h2 id="tracks-heading" className={styles.sectionTitle}>
            From your library
          </h2>
          <ul className={styles.rows}>
            {tracks.slice(0, 8).map((track) => (
              <li key={track.id}>
                <TrackRow
                  track={track}
                  onPlay={() =>
                    dispatch({ type: "load", track: toMediaItem(track), playWhenReady: true })
                  }
                  onQueue={() => dispatch({ type: "addToQueue", track: toMediaItem(track) })}
                />
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function TrackCard({
  track,
  subtitle,
  onPlay,
}: {
  track: StoredTrack;
  subtitle: string;
  onPlay: () => void;
}) {
  const artwork = useTrackArtwork(track);

  return (
    <button type="button" className={styles.card} onClick={onPlay}>
      <Artwork url={artwork.url} title={track.title} size="player" />
      <p className={styles.cardTitle}>{track.title}</p>
      <p className={styles.cardSubtitle}>{subtitle}</p>
    </button>
  );
}

function AlbumCard({
  album,
  tracks,
  onPlay,
}: {
  album: TrackGroup;
  tracks: StoredTrack[];
  onPlay: () => void;
}) {
  // The album's own first track carries the cover for the whole group.
  const artwork = useTrackArtwork(tracks[0]);

  return (
    <button type="button" className={styles.card} onClick={onPlay}>
      <Artwork url={artwork.url} title={album.name} size="player" />
      <p className={styles.cardTitle}>{album.name}</p>
      <p className={styles.cardSubtitle}>
        {tracks.length === 1 ? "1 track" : `${tracks.length} tracks`}
      </p>
    </button>
  );
}
