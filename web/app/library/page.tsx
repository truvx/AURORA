"use client";

import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { GlassCard } from "@/components/glass/Glass";
import { PlaylistsSection } from "@/components/library/PlaylistsSection";
import { TrackGroup, groupByAlbum, groupByArtist } from "@/lib/library/grouping";
import { StoredTrack } from "@/lib/library/db";
import {
  isFileSystemAccessSupported,
  pickMusicDirectory,
  requestDirectoryPermission,
  scanDirectory,
  toMediaItem,
} from "@/lib/library/fileSystem";
import { useLibrary, usePlayerCommands } from "@/lib/player/PlayerProvider";
import styles from "./library.module.css";

/** Capability never changes during a session, so there is nothing to subscribe to. */
const subscribeToNothing = () => () => {};
const serverCapabilityUnknown = (): boolean | undefined => undefined;

type ScanState =
  | { kind: "idle" }
  | { kind: "scanning"; found: number }
  | { kind: "error"; message: string };

export default function LibraryPage() {
  const { repository, organization, directory, setDirectory } = useLibrary();
  const dispatch = usePlayerCommands();

  const [tracks, setTracks] = useState<StoredTrack[]>([]);
  const [scan, setScan] = useState<ScanState>({ kind: "idle" });
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [showFavoritesOnly, setShowFavoritesOnly] = useState(false);
  const [view, setView] = useState<"tracks" | "albums" | "artists" | "playlists">("tracks");
  const [openGroup, setOpenGroup] = useState<TrackGroup | undefined>();
  const [addingTrackId, setAddingTrackId] = useState<string | undefined>();
  const [playlistChoices, setPlaylistChoices] = useState<{ id: string; name: string }[]>([]);

  // The server has no window, so this has to differ between server and client without
  // tripping hydration. useSyncExternalStore is the supported way to express that: the
  // server snapshot is undefined, and the real capability resolves on the client.
  const supported = useSyncExternalStore(
    subscribeToNothing,
    isFileSystemAccessSupported,
    serverCapabilityUnknown
  );

  useEffect(() => {
    organization
      ?.getFavoriteIds()
      .then(setFavorites)
      .catch(() => {
        // Favorites are an enhancement; the library still works without them.
      });
  }, [organization]);

  const beginAddToPlaylist = useCallback(
    async (mediaId: string) => {
      if (!organization) return;
      setPlaylistChoices(await organization.getPlaylists());
      setAddingTrackId(mediaId);
    },
    [organization]
  );

  const addToPlaylist = useCallback(
    async (playlistId: string) => {
      if (!organization || !addingTrackId) return;
      await organization.addToPlaylist(playlistId, addingTrackId);
      setAddingTrackId(undefined);
    },
    [organization, addingTrackId]
  );

  const toggleFavorite = useCallback(
    async (mediaId: string) => {
      if (!organization) return;
      const next = !favorites.has(mediaId);
      await organization.setFavorite(mediaId, next);
      setFavorites(await organization.getFavoriteIds());
    },
    [organization, favorites]
  );

  // Show whatever was saved before touching the disk, so a reload is not a blank page.
  useEffect(() => {
    repository
      ?.getAll()
      .then(setTracks)
      .catch(() =>
        setScan({ kind: "error", message: "Your saved library could not be read." })
      );
  }, [repository]);

  const runScan = useCallback(
    async (handle: FileSystemDirectoryHandle) => {
      if (!repository) return;
      setScan({ kind: "scanning", found: 0 });
      try {
        const found = await scanDirectory(handle, (count) =>
          setScan({ kind: "scanning", found: count })
        );
        await repository.replaceAll(found);
        setTracks(await repository.getAll());
        setScan({ kind: "idle" });
      } catch (error) {
        // A failed scan must not be treated as an empty folder; the saved library stands.
        setScan({
          kind: "error",
          message:
            error instanceof Error
              ? error.message
              : "The folder could not be read. Your saved library was left unchanged.",
        });
      }
    },
    [repository]
  );

  const chooseFolder = useCallback(async () => {
    try {
      const handle = await pickMusicDirectory();
      if (!handle) return;
      setDirectory?.(handle);
      await runScan(handle);
    } catch (error) {
      setScan({
        kind: "error",
        message: error instanceof Error ? error.message : "The folder could not be opened.",
      });
    }
  }, [runScan, setDirectory]);

  const rescan = useCallback(async () => {
    if (!directory) return;
    // Permission can lapse between sessions and may only be re-requested from a gesture.
    const granted = await requestDirectoryPermission(directory);
    if (!granted) {
      setScan({
        kind: "error",
        message: "Access to your music folder was declined, so it could not be rescanned.",
      });
      return;
    }
    await runScan(directory);
  }, [directory, runScan]);

  const visibleTracks = showFavoritesOnly
    ? tracks.filter((track) => favorites.has(track.id))
    : tracks;

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Library</h1>
        {directory && (
          <button type="button" onClick={rescan} className={styles.secondaryButton}>
            Rescan
          </button>
        )}
      </header>

      {view === "tracks" && supported === false && (
        <GlassCard>
          <h2>This browser cannot open a music folder</h2>
          <p>
            Choosing a folder needs the File System Access API, which Chrome and Edge support
            today. Firefox and Safari do not. YouTube search and the AI assistant still work
            here.
          </p>
        </GlassCard>
      )}

      {view === "tracks" && supported === true && !directory && tracks.length === 0 && (
        <GlassCard>
          <h2>Choose your music folder</h2>
          <p>
            AURORA reads files directly from the folder you pick and remembers it for next
            time. Your audio is never uploaded or copied.
          </p>
          <button type="button" onClick={chooseFolder} className={styles.primaryButton}>
            Choose folder
          </button>
        </GlassCard>
      )}

      {/* Always available: playlists must be reachable before any track is scanned. */}
      <div className={styles.filters} role="group" aria-label="Library view">
          <button
            type="button"
            onClick={() => { setView("tracks"); setOpenGroup(undefined); }}
            className={styles.secondaryButton}
            aria-pressed={view === "tracks"}
          >
            Tracks
          </button>
          <button
            type="button"
            onClick={() => { setView("albums"); setOpenGroup(undefined); }}
            className={styles.secondaryButton}
            aria-pressed={view === "albums"}
          >
            Albums
          </button>
          <button
            type="button"
            onClick={() => { setView("artists"); setOpenGroup(undefined); }}
            className={styles.secondaryButton}
            aria-pressed={view === "artists"}
          >
            Artists
          </button>
          <button
            type="button"
            onClick={() => setView("playlists")}
            className={styles.secondaryButton}
            aria-pressed={view === "playlists"}
          >
            Playlists
          </button>
      </div>

      {view === "playlists" && organization && (
        <PlaylistsSection organization={organization} tracks={tracks} />
      )}

      {(view === "albums" || view === "artists") && (
        <GroupedView
          groups={view === "albums" ? groupByAlbum(tracks) : groupByArtist(tracks)}
          openGroup={openGroup}
          tracks={tracks}
          label={view === "albums" ? "album" : "artist"}
          onOpen={setOpenGroup}
          onPlay={(track) =>
            dispatch({ type: "load", track: toMediaItem(track), playWhenReady: true })
          }
        />
      )}

      {scan.kind === "scanning" && (
        <p role="status" className={styles.status}>
          Scanning your folder… {scan.found} track{scan.found === 1 ? "" : "s"} found so far.
        </p>
      )}

      {scan.kind === "error" && (
        <p role="alert" className={styles.status}>
          ⚠ {scan.message}
        </p>
      )}

      {view === "tracks" && tracks.length > 0 && (
        <>
          <div className={styles.filters}>
            <button
              type="button"
              onClick={() => setShowFavoritesOnly((value) => !value)}
              className={styles.secondaryButton}
              aria-pressed={showFavoritesOnly}
            >
              {showFavoritesOnly ? "Showing favorites" : "Show favorites only"}
            </button>
          </div>

          {addingTrackId && (
            <GlassCard>
              <h2 className={styles.pickerTitle}>Add to playlist</h2>
              {playlistChoices.length === 0 ? (
                <p className={styles.status}>Create a playlist first.</p>
              ) : (
                <ul className={styles.trackList}>
                  {playlistChoices.map((playlist) => (
                    <li key={playlist.id}>
                      <button
                        type="button"
                        className={styles.trackRow}
                        onClick={() => void addToPlaylist(playlist.id)}
                      >
                        <span className={styles.trackTitle}>{playlist.name}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              <button
                type="button"
                className={styles.secondaryButton}
                onClick={() => setAddingTrackId(undefined)}
              >
                Cancel
              </button>
            </GlassCard>
          )}

          {visibleTracks.length === 0 ? (
            <p className={styles.status}>No favorites yet.</p>
          ) : (
            <ul className={styles.trackList}>
              {visibleTracks.map((track) => {
                const isFavorite = favorites.has(track.id);
                return (
                  <li key={track.id} className={styles.trackItem}>
                    <button
                      type="button"
                      className={styles.trackRow}
                      onClick={() =>
                        dispatch({
                          type: "load",
                          track: toMediaItem(track),
                          playWhenReady: true,
                        })
                      }
                    >
                      <span className={styles.trackTitle}>{track.title}</span>
                      <span className={styles.trackArtist}>
                        {track.artist ?? "Unknown artist"}
                      </span>
                    </button>
                    <button
                      type="button"
                      className={styles.favoriteButton}
                      onClick={() => void beginAddToPlaylist(track.id)}
                      aria-label={`Add ${track.title} to a playlist`}
                    >
                      <span className="material-symbols-outlined" aria-hidden="true">
                        playlist_add
                      </span>
                    </button>
                    <button
                      type="button"
                      className={styles.favoriteButton}
                      onClick={() => void toggleFavorite(track.id)}
                      aria-pressed={isFavorite}
                      aria-label={
                        isFavorite
                          ? `Remove ${track.title} from favorites`
                          : `Add ${track.title} to favorites`
                      }
                    >
                      {/* Filled vs outlined, so the state is not colour alone. */}
                      <span className="material-symbols-outlined" aria-hidden="true">
                        {isFavorite ? "favorite" : "favorite_border"}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}

      {view === "tracks" && supported === true && directory && tracks.length === 0 && scan.kind === "idle" && (
        <GlassCard>
          <h2>No audio files found</h2>
          <p>That folder has no files AURORA can play. Try rescanning or choosing another.</p>
          <button type="button" onClick={chooseFolder} className={styles.primaryButton}>
            Choose a different folder
          </button>
        </GlassCard>
      )}
    </div>
  );
}

/**
 * Albums and artists share one presentation: a list of groups, then the tracks inside one.
 * Written as a single component because the only difference is the wording.
 */
function GroupedView({
  groups,
  openGroup,
  tracks,
  label,
  onOpen,
  onPlay,
}: {
  groups: TrackGroup[];
  openGroup?: TrackGroup;
  tracks: StoredTrack[];
  label: string;
  onOpen: (group: TrackGroup | undefined) => void;
  onPlay: (track: StoredTrack) => void;
}) {
  if (groups.length === 0) {
    return (
      <GlassCard>
        <h2>No {label}s yet</h2>
        <p>Scan a music folder to see your {label}s here.</p>
      </GlassCard>
    );
  }

  if (openGroup) {
    // Re-read from the current grouping so the list reflects a rescan rather than the
    // snapshot captured when the group was opened.
    const current = groups.find((group) => group.key === openGroup.key) ?? openGroup;
    const byId = new Map(tracks.map((track) => [track.id, track]));

    return (
      <div className={styles.page}>
        <div className={styles.header}>
          <button
            type="button"
            onClick={() => onOpen(undefined)}
            className={styles.secondaryButton}
            aria-label={`Back to all ${label}s`}
          >
            Back
          </button>
          <h2>{current.name}</h2>
        </div>
        <ul className={styles.trackList}>
          {current.trackIds.map((id) => {
            const track = byId.get(id);
            if (!track) return null;
            return (
              <li key={id}>
                <button
                  type="button"
                  className={styles.trackRow}
                  onClick={() => onPlay(track)}
                  aria-label={`Play ${track.title}`}
                >
                  <span className={styles.trackTitle}>{track.title}</span>
                  <span className={styles.trackArtist}>{track.artist ?? "Unknown artist"}</span>
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    );
  }

  return (
    <ul className={styles.trackList}>
      {groups.map((group) => (
        <li key={group.key}>
          <button
            type="button"
            className={styles.trackRow}
            onClick={() => onOpen(group)}
            aria-label={`Open ${group.name}, ${group.trackIds.length} track${
              group.trackIds.length === 1 ? "" : "s"
            }`}
          >
            <span className={styles.trackTitle}>{group.name}</span>
            <span className={styles.trackArtist}>
              {group.artist ? `${group.artist} · ` : ""}
              {group.trackIds.length} track{group.trackIds.length === 1 ? "" : "s"}
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}
