import {
  FavoriteEntry,
  STORE_FAVORITES,
  STORE_PLAYLISTS,
  STORE_PLAYLIST_ENTRIES,
  StoredPlaylist,
  StoredPlaylistEntry,
  openDatabase,
  runTransaction,
} from "./db";

/**
 * Favorites and playlists for the web client.
 *
 * Deliberately mirrors the Android contract (docs/DATABASE_MODEL.md): positions stay
 * contiguous, the same track may appear twice in a playlist, entry ids are stable across
 * reordering, and an unavailable track keeps its place rather than being silently dropped
 * from a list the user built.
 */
export class OrganizationRepository {
  private db?: IDBDatabase;

  private async connection(): Promise<IDBDatabase> {
    if (!this.db) this.db = await openDatabase();
    return this.db;
  }

  // --- favorites ----------------------------------------------------------------------

  async getFavoriteIds(): Promise<Set<string>> {
    const db = await this.connection();
    const entries = await runTransaction<FavoriteEntry[]>(
      db,
      STORE_FAVORITES,
      "readonly",
      ([store]) => store.getAll() as IDBRequest<FavoriteEntry[]>
    );
    return new Set((entries ?? []).map((entry) => entry.mediaId));
  }

  async setFavorite(mediaId: string, isFavorite: boolean): Promise<void> {
    const db = await this.connection();
    await runTransaction(db, STORE_FAVORITES, "readwrite", ([store]) => {
      if (isFavorite) store.put({ mediaId, addedAt: Date.now() });
      else store.delete(mediaId);
    });
  }

  // --- playlists ----------------------------------------------------------------------

  async getPlaylists(): Promise<StoredPlaylist[]> {
    const db = await this.connection();
    const playlists = await runTransaction<StoredPlaylist[]>(
      db,
      STORE_PLAYLISTS,
      "readonly",
      ([store]) => store.getAll() as IDBRequest<StoredPlaylist[]>
    );
    return (playlists ?? []).sort((a, b) => b.updatedAt - a.updatedAt);
  }

  async createPlaylist(name: string): Promise<StoredPlaylist> {
    const trimmed = name.trim();
    if (!trimmed) throw new Error("A playlist needs a name.");

    const now = Date.now();
    const playlist: StoredPlaylist = {
      id: crypto.randomUUID(),
      name: trimmed,
      createdAt: now,
      updatedAt: now,
    };
    const db = await this.connection();
    await runTransaction(db, STORE_PLAYLISTS, "readwrite", ([store]) => {
      store.put(playlist);
    });
    return playlist;
  }

  async renamePlaylist(playlistId: string, name: string): Promise<void> {
    const trimmed = name.trim();
    if (!trimmed) throw new Error("A playlist needs a name.");

    const db = await this.connection();
    const existing = await runTransaction<StoredPlaylist>(
      db,
      STORE_PLAYLISTS,
      "readonly",
      ([store]) => store.get(playlistId) as IDBRequest<StoredPlaylist>
    );
    if (!existing) return;

    await runTransaction(db, STORE_PLAYLISTS, "readwrite", ([store]) => {
      store.put({ ...existing, name: trimmed, updatedAt: Date.now() });
    });
  }

  async deletePlaylist(playlistId: string): Promise<void> {
    const db = await this.connection();
    const entries = await this.getEntries(playlistId);
    await runTransaction(
      db,
      [STORE_PLAYLISTS, STORE_PLAYLIST_ENTRIES],
      "readwrite",
      ([playlists, playlistEntries]) => {
        playlists.delete(playlistId);
        // IndexedDB has no cascade, so entries are removed explicitly.
        entries.forEach((entry) => playlistEntries.delete(entry.entryId));
      }
    );
  }

  async getEntries(playlistId: string): Promise<StoredPlaylistEntry[]> {
    const db = await this.connection();
    const entries = await runTransaction<StoredPlaylistEntry[]>(
      db,
      STORE_PLAYLIST_ENTRIES,
      "readonly",
      ([store]) =>
        store.index("playlistId").getAll(playlistId) as IDBRequest<StoredPlaylistEntry[]>
    );
    return (entries ?? []).sort((a, b) => a.position - b.position);
  }

  /** Appends to the end. The same track may be added more than once, as on Android. */
  async addToPlaylist(playlistId: string, mediaId: string): Promise<void> {
    const entries = await this.getEntries(playlistId);
    const db = await this.connection();
    const now = Date.now();

    await runTransaction(
      db,
      [STORE_PLAYLIST_ENTRIES, STORE_PLAYLISTS],
      "readwrite",
      ([playlistEntries, playlists]) => {
        playlistEntries.put({
          entryId: crypto.randomUUID(),
          playlistId,
          mediaId,
          position: entries.length,
          addedAt: now,
        });
        touch(playlists, playlistId, now);
      }
    );
  }

  /** Removes an entry and closes the gap so positions stay contiguous. */
  async removeFromPlaylist(playlistId: string, entryId: string): Promise<void> {
    const entries = await this.getEntries(playlistId);
    const target = entries.find((entry) => entry.entryId === entryId);
    if (!target) return;

    const remaining = entries
      .filter((entry) => entry.entryId !== entryId)
      .map((entry, index) => ({ ...entry, position: index }));

    const db = await this.connection();
    const now = Date.now();
    await runTransaction(
      db,
      [STORE_PLAYLIST_ENTRIES, STORE_PLAYLISTS],
      "readwrite",
      ([playlistEntries, playlists]) => {
        playlistEntries.delete(entryId);
        remaining.forEach((entry) => playlistEntries.put(entry));
        touch(playlists, playlistId, now);
      }
    );
  }

  /**
   * Moves an entry between positions. Out-of-range or no-op moves leave the playlist
   * untouched rather than corrupting the ordering.
   */
  async moveEntry(playlistId: string, from: number, to: number): Promise<void> {
    if (from === to) return;
    const entries = await this.getEntries(playlistId);
    const last = entries.length - 1;
    if (from < 0 || from > last || to < 0 || to > last) return;

    const reordered = [...entries];
    const [moved] = reordered.splice(from, 1);
    reordered.splice(to, 0, moved);
    const renumbered = reordered.map((entry, index) => ({ ...entry, position: index }));

    const db = await this.connection();
    const now = Date.now();
    await runTransaction(
      db,
      [STORE_PLAYLIST_ENTRIES, STORE_PLAYLISTS],
      "readwrite",
      ([playlistEntries, playlists]) => {
        renumbered.forEach((entry) => playlistEntries.put(entry));
        touch(playlists, playlistId, now);
      }
    );
  }

  close(): void {
    this.db?.close();
    this.db = undefined;
  }
}

function touch(playlists: IDBObjectStore, playlistId: string, now: number): void {
  const request = playlists.get(playlistId);
  request.onsuccess = () => {
    const playlist = request.result as StoredPlaylist | undefined;
    if (playlist) playlists.put({ ...playlist, updatedAt: now });
  };
}
