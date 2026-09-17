"use client";

import { useCallback, useEffect, useState } from "react";
import { GlassCard } from "@/components/glass/Glass";
import { StoredPlaylist, StoredPlaylistEntry, StoredTrack } from "@/lib/library/db";
import { toMediaItem } from "@/lib/library/fileSystem";
import { OrganizationRepository } from "@/lib/library/organization";
import { usePlayerCommands } from "@/lib/player/PlayerProvider";
import styles from "./PlaylistsSection.module.css";

interface Props {
  organization: OrganizationRepository;
  /** Library tracks, used to resolve entries to something playable. */
  tracks: StoredTrack[];
}

/**
 * Playlist browsing and editing.
 *
 * Reordering uses explicit Move up / Move down buttons rather than drag: they are reachable
 * by keyboard and screen reader, and each press maps to one transactional move in the
 * repository. docs/ACCESSIBILITY_SPEC.md requires a named alternative to dragging.
 */
export function PlaylistsSection({ organization, tracks }: Props) {
  const dispatch = usePlayerCommands();

  const [playlists, setPlaylists] = useState<StoredPlaylist[]>([]);
  const [openId, setOpenId] = useState<string | undefined>();
  const [entries, setEntries] = useState<StoredPlaylistEntry[]>([]);
  const [newName, setNewName] = useState("");
  const [error, setError] = useState<string | undefined>();

  const refreshPlaylists = useCallback(async () => {
    try {
      setPlaylists(await organization.getPlaylists());
    } catch {
      setError("Your playlists could not be read.");
    }
  }, [organization]);

  const refreshEntries = useCallback(
    async (playlistId: string) => {
      try {
        setEntries(await organization.getEntries(playlistId));
      } catch {
        setError("That playlist's tracks could not be read.");
      }
    },
    [organization]
  );

  // setState lands in a callback rather than after an await, which is both what the React
  // rules ask for and what stops a slow read from writing to an unmounted component.
  useEffect(() => {
    let cancelled = false;
    organization
      .getPlaylists()
      .then((result) => {
        if (!cancelled) setPlaylists(result);
      })
      .catch(() => {
        if (!cancelled) setError("Your playlists could not be read.");
      });
    return () => {
      cancelled = true;
    };
  }, [organization]);

  useEffect(() => {
    if (!openId) return;
    let cancelled = false;
    organization
      .getEntries(openId)
      .then((result) => {
        if (!cancelled) setEntries(result);
      })
      .catch(() => {
        if (!cancelled) setError("That playlist's tracks could not be read.");
      });
    return () => {
      cancelled = true;
    };
  }, [organization, openId]);

  const create = useCallback(async () => {
    try {
      await organization.createPlaylist(newName);
      setNewName("");
      setError(undefined);
      await refreshPlaylists();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The playlist could not be created.");
    }
  }, [newName, organization, refreshPlaylists]);

  const remove = useCallback(
    async (playlistId: string) => {
      if (openId === playlistId) setOpenId(undefined);
      await organization.deletePlaylist(playlistId);
      await refreshPlaylists();
    },
    [openId, organization, refreshPlaylists]
  );

  const move = useCallback(
    async (from: number, to: number) => {
      if (!openId) return;
      await organization.moveEntry(openId, from, to);
      await refreshEntries(openId);
    },
    [openId, organization, refreshEntries]
  );

  const removeEntry = useCallback(
    async (entryId: string) => {
      if (!openId) return;
      await organization.removeFromPlaylist(openId, entryId);
      await refreshEntries(openId);
      await refreshPlaylists();
    },
    [openId, organization, refreshEntries, refreshPlaylists]
  );

  const openPlaylist = playlists.find((playlist) => playlist.id === openId);

  if (openPlaylist) {
    return (
      <PlaylistDetail
        playlist={openPlaylist}
        entries={entries}
        tracks={tracks}
        error={error}
        onBack={() => setOpenId(undefined)}
        onMove={move}
        onRemoveEntry={removeEntry}
        onPlay={(track) =>
          dispatch({ type: "load", track: toMediaItem(track), playWhenReady: true })
        }
      />
    );
  }

  return (
    <div className={styles.section}>
      <div className={styles.createRow}>
        <label className={styles.visuallyHidden} htmlFor="new-playlist">
          New playlist name
        </label>
        <input
          id="new-playlist"
          type="text"
          value={newName}
          onChange={(event) => setNewName(event.target.value)}
          placeholder="New playlist name"
          className={styles.input}
        />
        <button
          type="button"
          onClick={() => void create()}
          disabled={newName.trim().length === 0}
          className={styles.primaryButton}
        >
          Create
        </button>
      </div>

      {error && (
        <p role="alert" className={styles.status}>
          ⚠ {error}
        </p>
      )}

      {playlists.length === 0 ? (
        <GlassCard>
          <h2>No playlists yet</h2>
          <p>Name one above to start collecting tracks.</p>
        </GlassCard>
      ) : (
        <ul className={styles.list}>
          {playlists.map((playlist) => (
            <li key={playlist.id} className={styles.row}>
              <button
                type="button"
                className={styles.rowButton}
                onClick={() => setOpenId(playlist.id)}
                // Explicit: the name computed from content alone came back empty in the
                // accessibility tree, leaving an unnamed button.
                aria-label={`Open playlist ${playlist.name}`}
              >
                <span className={styles.rowTitle}>{playlist.name}</span>
              </button>
              <button
                type="button"
                className={styles.iconButton}
                onClick={() => void remove(playlist.id)}
                aria-label={`Delete playlist ${playlist.name}`}
              >
                <span className="material-symbols-outlined" aria-hidden="true">
                  delete
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function PlaylistDetail({
  playlist,
  entries,
  tracks,
  error,
  onBack,
  onMove,
  onRemoveEntry,
  onPlay,
}: {
  playlist: StoredPlaylist;
  entries: StoredPlaylistEntry[];
  tracks: StoredTrack[];
  error?: string;
  onBack: () => void;
  onMove: (from: number, to: number) => void;
  onRemoveEntry: (entryId: string) => void;
  onPlay: (track: StoredTrack) => void;
}) {
  const byId = new Map(tracks.map((track) => [track.id, track]));

  return (
    <div className={styles.section}>
      <div className={styles.detailHeader}>
        <button
          type="button"
          onClick={onBack}
          className={styles.iconButton}
          aria-label="Back to all playlists"
        >
          <span className="material-symbols-outlined" aria-hidden="true">
            arrow_back
          </span>
        </button>
        <div>
          <h2 className={styles.rowTitle}>{playlist.name}</h2>
          <p className={styles.status}>
            {entries.length} track{entries.length === 1 ? "" : "s"}
          </p>
        </div>
      </div>

      {error && (
        <p role="alert" className={styles.status}>
          ⚠ {error}
        </p>
      )}

      {entries.length === 0 ? (
        <p className={styles.status}>
          This playlist is empty. Add tracks from the Tracks tab.
        </p>
      ) : (
        <ol className={styles.list}>
          {entries.map((entry, index) => {
            const track = byId.get(entry.mediaId);
            return (
              <li key={entry.entryId} className={styles.row}>
                <button
                  type="button"
                  className={styles.rowButton}
                  disabled={!track}
                  onClick={() => track && onPlay(track)}
                  aria-label={
                    track
                      ? `Play ${track.title}, track ${index + 1} of ${entries.length}`
                      : "This track is not currently available"
                  }
                >
                  <span className={styles.rowTitle}>
                    {/* A missing file keeps its place rather than vanishing from a list
                        the user built. */}
                    {track?.title ?? "Unavailable track"}
                  </span>
                  <span className={styles.status}>
                    {track ? (track.artist ?? "Unknown artist") : "Not currently available"}
                  </span>
                </button>
                <button
                  type="button"
                  className={styles.iconButton}
                  disabled={index === 0}
                  onClick={() => onMove(index, index - 1)}
                  aria-label={`Move ${track?.title ?? "track"} up`}
                >
                  <span className="material-symbols-outlined" aria-hidden="true">
                    arrow_upward
                  </span>
                </button>
                <button
                  type="button"
                  className={styles.iconButton}
                  disabled={index === entries.length - 1}
                  onClick={() => onMove(index, index + 1)}
                  aria-label={`Move ${track?.title ?? "track"} down`}
                >
                  <span className="material-symbols-outlined" aria-hidden="true">
                    arrow_downward
                  </span>
                </button>
                <button
                  type="button"
                  className={styles.iconButton}
                  onClick={() => onRemoveEntry(entry.entryId)}
                  aria-label={`Remove ${track?.title ?? "track"} from ${playlist.name}`}
                >
                  <span className="material-symbols-outlined" aria-hidden="true">
                    close
                  </span>
                </button>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}
