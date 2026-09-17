import { STORE_TRACKS, StoredTrack, openDatabase, runTransaction } from "./db";

/**
 * Persistence for the scanned library.
 *
 * Reconciliation mirrors the Android scanner's rule: a partial or failed scan must never be
 * treated as authoritative, because that would delete a library the user still has.
 */
export class LibraryRepository {
  private db?: IDBDatabase;

  private async connection(): Promise<IDBDatabase> {
    if (!this.db) this.db = await openDatabase();
    return this.db;
  }

  async getAll(): Promise<StoredTrack[]> {
    const db = await this.connection();
    const tracks = await runTransaction<StoredTrack[]>(
      db,
      STORE_TRACKS,
      "readonly",
      ([store]) => store.getAll() as IDBRequest<StoredTrack[]>
    );
    return (tracks ?? []).sort((a, b) => a.title.localeCompare(b.title));
  }

  async get(id: string): Promise<StoredTrack | undefined> {
    const db = await this.connection();
    return runTransaction<StoredTrack>(
      db,
      STORE_TRACKS,
      "readonly",
      ([store]) => store.get(id) as IDBRequest<StoredTrack>
    );
  }

  /**
   * Replaces the library with the result of a completed scan.
   *
   * Tracks missing from [scanned] are removed, so the caller must only pass the result of a
   * scan that finished. An empty result clears the library, which is correct for an emptied
   * folder and wrong for a failed scan - hence the guard in the caller.
   */
  async replaceAll(scanned: StoredTrack[]): Promise<void> {
    const db = await this.connection();
    const existing = await this.getAll();
    const scannedIds = new Set(scanned.map((track) => track.id));

    await runTransaction(db, STORE_TRACKS, "readwrite", ([store]) => {
      for (const track of existing) {
        if (!scannedIds.has(track.id)) store.delete(track.id);
      }
      for (const track of scanned) {
        // Preserve the original addedAt so library ordering is stable across rescans.
        const previous = existing.find((item) => item.id === track.id);
        store.put(previous ? { ...track, addedAt: previous.addedAt } : track);
      }
    });
  }

  async clear(): Promise<void> {
    const db = await this.connection();
    await runTransaction(db, STORE_TRACKS, "readwrite", ([store]) => {
      store.clear();
    });
  }

  close(): void {
    this.db?.close();
    this.db = undefined;
  }
}
