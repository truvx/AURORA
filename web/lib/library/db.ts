/**
 * IndexedDB schema for the web client.
 *
 * Stores bounded metadata only. Audio bytes are never written here: the directory handle
 * lets us re-open the real files on demand, so a large library costs kilobytes of metadata
 * rather than gigabytes of duplicated audio (docs/WEB_ARCHITECTURE.md).
 */

export const DB_NAME = "aurora";
export const DB_VERSION = 1;

export const STORE_TRACKS = "tracks";
export const STORE_HANDLES = "handles";
export const STORE_SETTINGS = "settings";

export interface StoredTrack {
  /** Stable within a library: the file's path relative to the chosen directory. */
  id: string;
  title: string;
  artist?: string;
  album?: string;
  /** Path segments from the chosen root, used to re-open the file. */
  path: string[];
  fileName: string;
  sizeBytes: number;
  lastModified: number;
  mimeType?: string;
  durationMs?: number;
  addedAt: number;
}

export function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === "undefined") {
      reject(new Error("This browser has no IndexedDB, so the library cannot be saved."));
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_TRACKS)) {
        const tracks = db.createObjectStore(STORE_TRACKS, { keyPath: "id" });
        tracks.createIndex("album", "album", { unique: false });
        tracks.createIndex("artist", "artist", { unique: false });
        tracks.createIndex("addedAt", "addedAt", { unique: false });
      }
      // Directory handles are structured-cloneable, so the chosen folder survives reloads
      // and the user is not asked to pick it again every session.
      if (!db.objectStoreNames.contains(STORE_HANDLES)) {
        db.createObjectStore(STORE_HANDLES);
      }
      if (!db.objectStoreNames.contains(STORE_SETTINGS)) {
        db.createObjectStore(STORE_SETTINGS);
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () =>
      reject(request.error ?? new Error("The library database could not be opened"));
  });
}

export function runTransaction<T>(
  db: IDBDatabase,
  storeNames: string | string[],
  mode: IDBTransactionMode,
  work: (stores: IDBObjectStore[]) => IDBRequest<T> | void
): Promise<T | undefined> {
  return new Promise((resolve, reject) => {
    const names = Array.isArray(storeNames) ? storeNames : [storeNames];
    const transaction = db.transaction(names, mode);
    const stores = names.map((name) => transaction.objectStore(name));

    let request: IDBRequest<T> | void;
    try {
      request = work(stores);
    } catch (error) {
      transaction.abort();
      reject(error);
      return;
    }

    transaction.oncomplete = () => resolve(request ? request.result : undefined);
    transaction.onerror = () =>
      reject(transaction.error ?? new Error("The library database write failed"));
    // Quota exhaustion surfaces as an abort; saying so is better than a silent no-op.
    transaction.onabort = () =>
      reject(
        transaction.error ??
          new Error("The library database ran out of space or the write was cancelled")
      );
  });
}
