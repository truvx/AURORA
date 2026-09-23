import {
  QUEUE_SNAPSHOT_KEY,
  STORE_QUEUE,
  StoredQueueItem,
  StoredQueueSnapshot,
  openDatabase,
  runTransaction,
} from "../library/db";
import { MediaItem, PlayerState, ProviderKind, QueueState } from "./types";

/**
 * The queue, kept across reloads.
 *
 * Android has had this since Phase 6; the web client lost the queue and what was playing
 * on every refresh. Restoration follows the same rule as Android's QueueRestorer: tracks
 * go back silently, nothing starts playing, and anything that no longer resolves is
 * dropped rather than faked - a deleted file leaves a shorter queue, not an entry that
 * fails when tapped.
 */
export class QueueSnapshotRepository {
  async save(queue: QueueState): Promise<void> {
    const db = await openDatabase();
    try {
      const snapshot: StoredQueueSnapshot = {
        key: QUEUE_SNAPSHOT_KEY,
        items: queue.items.map(toStored),
        currentIndex: queue.currentIndex,
        repeatMode: queue.repeatMode,
        shuffleMode: queue.shuffleMode,
        updatedAt: Date.now(),
      };
      await runTransaction(db, STORE_QUEUE, "readwrite", ([store]) => store.put(snapshot));
    } finally {
      db.close();
    }
  }

  async load(): Promise<StoredQueueSnapshot | undefined> {
    const db = await openDatabase();
    try {
      const result = await runTransaction<StoredQueueSnapshot>(
        db,
        STORE_QUEUE,
        "readonly",
        ([store]) => store.get(QUEUE_SNAPSHOT_KEY) as IDBRequest<StoredQueueSnapshot>
      );
      return result ?? undefined;
    } finally {
      db.close();
    }
  }

  async clear(): Promise<void> {
    const db = await openDatabase();
    try {
      await runTransaction(db, STORE_QUEUE, "readwrite", ([store]) =>
        store.delete(QUEUE_SNAPSHOT_KEY)
      );
    } finally {
      db.close();
    }
  }
}

function toStored(item: MediaItem): StoredQueueItem {
  return {
    id: item.id,
    provider: item.provider,
    title: item.title,
    artist: item.artist,
    album: item.album,
    artworkUrl: item.artworkUrl,
  };
}

/**
 * Turns a stored entry back into a track.
 *
 * Everything is checked, because this came off disk and may have been written by a build
 * that knew a different shape. A row missing an id, a title or a recognisable provider is
 * not repaired - it is dropped.
 */
export function fromStored(stored: StoredQueueItem): MediaItem | undefined {
  if (typeof stored?.id !== "string" || !stored.id) return undefined;
  if (typeof stored.title !== "string" || !stored.title) return undefined;
  const provider = stored.provider as ProviderKind;
  if (provider !== "LOCAL" && provider !== "YOUTUBE") return undefined;

  return {
    id: stored.id,
    provider,
    title: stored.title,
    artist: typeof stored.artist === "string" ? stored.artist : undefined,
    album: typeof stored.album === "string" ? stored.album : undefined,
    artworkUrl: typeof stored.artworkUrl === "string" ? stored.artworkUrl : undefined,
    isAvailable: true,
  };
}

/**
 * The restorable part of a snapshot: valid tracks, and where the queue was pointing.
 *
 * `exists` says whether a track is still there. A local file that has left the library
 * cannot be played, so it does not go back in the queue.
 */
export function restorableQueue(
  snapshot: StoredQueueSnapshot | undefined,
  exists: (item: MediaItem) => boolean
): { items: MediaItem[]; currentIndex: number } {
  if (!snapshot || !Array.isArray(snapshot.items)) return { items: [], currentIndex: -1 };

  const kept: MediaItem[] = [];
  let currentIndex = -1;

  snapshot.items.forEach((stored, index) => {
    const item = fromStored(stored);
    if (!item || !exists(item)) return;
    if (index === snapshot.currentIndex) currentIndex = kept.length;
    kept.push(item);
  });

  // The track that was playing may be the one that went missing. Pointing at the start is
  // better than pointing at nothing, since the queue is not empty.
  if (kept.length > 0 && currentIndex === -1) currentIndex = 0;
  return { items: kept, currentIndex };
}

/**
 * What identifies this queue, for deciding whether it needs writing again.
 *
 * Position ticks arrive four times a second and must not each cause a database write. The
 * queue only changes when its tracks, its pointer or its modes change.
 */
export function queueSignature(state: PlayerState): string {
  const { queue } = state;
  return [
    queue.items.map((item) => item.id).join(","),
    queue.currentIndex,
    queue.repeatMode,
    queue.shuffleMode,
  ].join("|");
}
