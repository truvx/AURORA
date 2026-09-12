import { MediaItem } from "../player/types";
import { STORE_HANDLES, StoredTrack, openDatabase, runTransaction } from "./db";

/**
 * Local library access through the File System Access API.
 *
 * The user picks a music folder once; the handle is stored in IndexedDB so the library
 * survives reloads without re-prompting. Audio bytes are never copied - files are re-opened
 * from the real folder when a track is played.
 *
 * Only Chromium browsers implement this today. Support is reported as a capability so the
 * UI can explain the limitation instead of appearing broken (docs/ACCESSIBILITY_SPEC.md
 * requires unmet conditions to be explained).
 */

const HANDLE_KEY = "musicDirectory";

const AUDIO_EXTENSIONS = new Set([
  "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "alac", "aiff",
]);

export function isFileSystemAccessSupported(): boolean {
  return typeof window !== "undefined" && "showDirectoryPicker" in window;
}

/** Prompts for a music folder. Returns undefined when the user cancels. */
export async function pickMusicDirectory(): Promise<FileSystemDirectoryHandle | undefined> {
  if (!isFileSystemAccessSupported()) {
    throw new Error(
      "This browser does not support choosing a music folder. Chrome or Edge is required."
    );
  }
  try {
    const handle = await (
      window as unknown as {
        showDirectoryPicker: (options?: {
          mode?: "read" | "readwrite";
          id?: string;
        }) => Promise<FileSystemDirectoryHandle>;
      }
    ).showDirectoryPicker({ mode: "read", id: "aurora-music" });
    await saveDirectoryHandle(handle);
    return handle;
  } catch (error) {
    // Cancelling the picker is a normal outcome, not an error to report.
    if (error instanceof DOMException && error.name === "AbortError") return undefined;
    throw error;
  }
}

async function saveDirectoryHandle(handle: FileSystemDirectoryHandle): Promise<void> {
  const db = await openDatabase();
  await runTransaction(db, STORE_HANDLES, "readwrite", ([store]) => {
    store.put(handle, HANDLE_KEY);
  });
  db.close();
}

/** Returns the saved folder, or undefined if none was chosen or permission was revoked. */
export async function restoreMusicDirectory(): Promise<
  FileSystemDirectoryHandle | undefined
> {
  if (!isFileSystemAccessSupported()) return undefined;
  const db = await openDatabase();
  const handle = await runTransaction<FileSystemDirectoryHandle>(
    db,
    STORE_HANDLES,
    "readonly",
    ([store]) => store.get(HANDLE_KEY) as IDBRequest<FileSystemDirectoryHandle>
  );
  db.close();
  if (!handle) return undefined;

  // Permission can lapse between sessions. Query without prompting; a prompt outside a user
  // gesture would be rejected anyway.
  const permission = await queryPermission(handle);
  return permission === "granted" ? handle : undefined;
}

/** Re-requests folder permission. Must be called from a user gesture. */
export async function requestDirectoryPermission(
  handle: FileSystemDirectoryHandle
): Promise<boolean> {
  const withPermission = handle as FileSystemDirectoryHandle & {
    requestPermission?: (descriptor: { mode: "read" }) => Promise<PermissionState>;
  };
  if (!withPermission.requestPermission) return true;
  return (await withPermission.requestPermission({ mode: "read" })) === "granted";
}

async function queryPermission(handle: FileSystemDirectoryHandle): Promise<PermissionState> {
  const withPermission = handle as FileSystemDirectoryHandle & {
    queryPermission?: (descriptor: { mode: "read" }) => Promise<PermissionState>;
  };
  if (!withPermission.queryPermission) return "granted";
  return withPermission.queryPermission({ mode: "read" });
}

/**
 * Walks the chosen folder for audio files.
 *
 * Metadata comes from the file itself. Tags are not parsed yet, so title falls back to the
 * filename and artist stays undefined rather than being guessed - unknown stays unknown,
 * matching the rule the Android client had to be corrected to follow.
 */
export async function scanDirectory(
  root: FileSystemDirectoryHandle,
  onProgress?: (found: number) => void
): Promise<StoredTrack[]> {
  const tracks: StoredTrack[] = [];
  await walk(root, [], tracks, onProgress);
  return tracks;
}

async function walk(
  directory: FileSystemDirectoryHandle,
  path: string[],
  out: StoredTrack[],
  onProgress?: (found: number) => void
): Promise<void> {
  const iterable = directory as unknown as {
    values: () => AsyncIterable<FileSystemHandle>;
  };

  for await (const entry of iterable.values()) {
    if (entry.kind === "directory") {
      await walk(entry as FileSystemDirectoryHandle, [...path, entry.name], out, onProgress);
      continue;
    }

    const extension = entry.name.split(".").pop()?.toLowerCase();
    if (!extension || !AUDIO_EXTENSIONS.has(extension)) continue;

    try {
      const file = await (entry as FileSystemFileHandle).getFile();
      out.push({
        id: [...path, entry.name].join("/"),
        title: stripExtension(entry.name),
        path,
        fileName: entry.name,
        sizeBytes: file.size,
        lastModified: file.lastModified,
        mimeType: file.type || undefined,
        addedAt: Date.now(),
      });
      onProgress?.(out.length);
    } catch {
      // One unreadable file must not abandon the whole scan.
    }
  }
}

/** Re-opens a track's file and returns an object URL for playback. */
export async function resolveTrackUrl(
  root: FileSystemDirectoryHandle,
  track: StoredTrack
): Promise<string | undefined> {
  try {
    let directory = root;
    for (const segment of track.path) {
      directory = await directory.getDirectoryHandle(segment);
    }
    const fileHandle = await directory.getFileHandle(track.fileName);
    const file = await fileHandle.getFile();
    return URL.createObjectURL(file);
  } catch {
    // Moved, renamed, or deleted since the scan. The caller reports it as unavailable
    // rather than pretending the track is playable.
    return undefined;
  }
}

export function toMediaItem(track: StoredTrack): MediaItem {
  return {
    id: track.id,
    provider: "LOCAL",
    title: track.title,
    artist: track.artist,
    album: track.album,
    isAvailable: true,
    technicalMetadata: {
      codec: track.mimeType,
      durationMs: track.durationMs,
    },
  };
}

function stripExtension(fileName: string): string {
  const dot = fileName.lastIndexOf(".");
  return dot > 0 ? fileName.slice(0, dot) : fileName;
}
