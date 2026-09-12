"use client";

import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { GlassCard } from "@/components/glass/Glass";
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
  const { repository, directory, setDirectory } = useLibrary();
  const dispatch = usePlayerCommands();

  const [tracks, setTracks] = useState<StoredTrack[]>([]);
  const [scan, setScan] = useState<ScanState>({ kind: "idle" });

  // The server has no window, so this has to differ between server and client without
  // tripping hydration. useSyncExternalStore is the supported way to express that: the
  // server snapshot is undefined, and the real capability resolves on the client.
  const supported = useSyncExternalStore(
    subscribeToNothing,
    isFileSystemAccessSupported,
    serverCapabilityUnknown
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

      {supported === false && (
        <GlassCard>
          <h2>This browser cannot open a music folder</h2>
          <p>
            Choosing a folder needs the File System Access API, which Chrome and Edge support
            today. Firefox and Safari do not. YouTube search and the AI assistant still work
            here.
          </p>
        </GlassCard>
      )}

      {supported === true && !directory && tracks.length === 0 && (
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

      {tracks.length > 0 && (
        <ul className={styles.trackList}>
          {tracks.map((track) => (
            <li key={track.id}>
              <button
                type="button"
                className={styles.trackRow}
                onClick={() =>
                  dispatch({ type: "load", track: toMediaItem(track), playWhenReady: true })
                }
              >
                <span className={styles.trackTitle}>{track.title}</span>
                <span className={styles.trackArtist}>{track.artist ?? "Unknown artist"}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {supported === true && directory && tracks.length === 0 && scan.kind === "idle" && (
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
