import "fake-indexeddb/auto";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { DB_NAME } from "../db";
import { OrganizationRepository } from "../organization";

/**
 * Mirrors the guarantees the Android client's playlist DAO is held to
 * (docs/DATABASE_MODEL.md): ordering stays contiguous, duplicates are allowed, entry ids
 * survive reordering, and a bad move changes nothing.
 */
describe("OrganizationRepository", () => {
  let repository: OrganizationRepository;

  // The repository caches its connection, and deleteDatabase blocks while one is open, so
  // the connection must be closed before the next test wipes the database.
  afterEach(() => {
    repository?.close();
  });

  beforeEach(async () => {
    // Each test gets a fresh database; IndexedDB is global and would otherwise leak state.
    await new Promise<void>((resolve, reject) => {
      const request = indexedDB.deleteDatabase(DB_NAME);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
      request.onblocked = () => reject(new Error("deleteDatabase blocked by an open connection"));
    });
    repository = new OrganizationRepository();
  });

  // --- favorites ----------------------------------------------------------------------

  it("stores and clears favorites", async () => {
    await repository.setFavorite("track-a", true);
    expect(await repository.getFavoriteIds()).toEqual(new Set(["track-a"]));

    await repository.setFavorite("track-a", false);
    expect(await repository.getFavoriteIds()).toEqual(new Set());
  });

  it("treats repeated favoriting as idempotent", async () => {
    await repository.setFavorite("track-a", true);
    await repository.setFavorite("track-a", true);

    expect(await repository.getFavoriteIds()).toEqual(new Set(["track-a"]));
  });

  // --- playlists ----------------------------------------------------------------------

  it("creates a playlist with a trimmed name", async () => {
    const playlist = await repository.createPlaylist("  Late Night  ");
    expect(playlist.name).toBe("Late Night");

    const playlists = await repository.getPlaylists();
    expect(playlists.map((p) => p.name)).toEqual(["Late Night"]);
  });

  it("refuses a blank playlist name", async () => {
    await expect(repository.createPlaylist("   ")).rejects.toThrow(/needs a name/i);
  });

  it("renames a playlist", async () => {
    const playlist = await repository.createPlaylist("Old");
    await repository.renamePlaylist(playlist.id, "New");

    const playlists = await repository.getPlaylists();
    expect(playlists[0].name).toBe("New");
  });

  it("deletes a playlist and its entries", async () => {
    const playlist = await repository.createPlaylist("Temp");
    await repository.addToPlaylist(playlist.id, "track-a");
    await repository.deletePlaylist(playlist.id);

    expect(await repository.getPlaylists()).toEqual([]);
    // IndexedDB has no cascade, so orphaned entries would survive a naive delete.
    expect(await repository.getEntries(playlist.id)).toEqual([]);
  });

  // --- entries and ordering -----------------------------------------------------------

  it("appends entries in order", async () => {
    const playlist = await repository.createPlaylist("Mix");
    await repository.addToPlaylist(playlist.id, "a");
    await repository.addToPlaylist(playlist.id, "b");
    await repository.addToPlaylist(playlist.id, "c");

    const entries = await repository.getEntries(playlist.id);
    expect(entries.map((e) => e.mediaId)).toEqual(["a", "b", "c"]);
    expect(entries.map((e) => e.position)).toEqual([0, 1, 2]);
  });

  it("allows the same track twice", async () => {
    const playlist = await repository.createPlaylist("Mix");
    await repository.addToPlaylist(playlist.id, "a");
    await repository.addToPlaylist(playlist.id, "a");

    const entries = await repository.getEntries(playlist.id);
    expect(entries.map((e) => e.mediaId)).toEqual(["a", "a"]);
    // Distinct entry ids are what make the duplicates independently removable.
    expect(entries[0].entryId).not.toBe(entries[1].entryId);
  });

  it("closes the gap when an entry is removed", async () => {
    const playlist = await repository.createPlaylist("Mix");
    await repository.addToPlaylist(playlist.id, "a");
    await repository.addToPlaylist(playlist.id, "b");
    await repository.addToPlaylist(playlist.id, "c");

    const entries = await repository.getEntries(playlist.id);
    await repository.removeFromPlaylist(playlist.id, entries[1].entryId);

    const remaining = await repository.getEntries(playlist.id);
    expect(remaining.map((e) => e.mediaId)).toEqual(["a", "c"]);
    expect(remaining.map((e) => e.position)).toEqual([0, 1]);
  });

  it("moves an entry down and renumbers", async () => {
    const playlist = await repository.createPlaylist("Mix");
    for (const id of ["a", "b", "c", "d"]) await repository.addToPlaylist(playlist.id, id);

    await repository.moveEntry(playlist.id, 0, 2);

    const entries = await repository.getEntries(playlist.id);
    expect(entries.map((e) => e.mediaId)).toEqual(["b", "c", "a", "d"]);
    expect(entries.map((e) => e.position)).toEqual([0, 1, 2, 3]);
  });

  it("moves an entry up and renumbers", async () => {
    const playlist = await repository.createPlaylist("Mix");
    for (const id of ["a", "b", "c", "d"]) await repository.addToPlaylist(playlist.id, id);

    await repository.moveEntry(playlist.id, 3, 1);

    const entries = await repository.getEntries(playlist.id);
    expect(entries.map((e) => e.mediaId)).toEqual(["a", "d", "b", "c"]);
    expect(entries.map((e) => e.position)).toEqual([0, 1, 2, 3]);
  });

  it("keeps an entry id stable across a move", async () => {
    const playlist = await repository.createPlaylist("Mix");
    await repository.addToPlaylist(playlist.id, "a");
    await repository.addToPlaylist(playlist.id, "b");

    const before = await repository.getEntries(playlist.id);
    const movedId = before[0].entryId;
    await repository.moveEntry(playlist.id, 0, 1);

    const after = await repository.getEntries(playlist.id);
    expect(after[1].entryId).toBe(movedId);
  });

  it("leaves ordering untouched for an out-of-range move", async () => {
    const playlist = await repository.createPlaylist("Mix");
    await repository.addToPlaylist(playlist.id, "a");
    await repository.addToPlaylist(playlist.id, "b");

    await repository.moveEntry(playlist.id, 0, 7);

    const entries = await repository.getEntries(playlist.id);
    expect(entries.map((e) => e.mediaId)).toEqual(["a", "b"]);
    expect(entries.map((e) => e.position)).toEqual([0, 1]);
  });

  it("treats a move to the same position as a no-op", async () => {
    const playlist = await repository.createPlaylist("Mix");
    await repository.addToPlaylist(playlist.id, "a");
    await repository.addToPlaylist(playlist.id, "b");

    await repository.moveEntry(playlist.id, 1, 1);

    const entries = await repository.getEntries(playlist.id);
    expect(entries.map((e) => e.mediaId)).toEqual(["a", "b"]);
  });

  it("keeps playlists separate", async () => {
    const first = await repository.createPlaylist("First");
    const second = await repository.createPlaylist("Second");
    await repository.addToPlaylist(first.id, "a");
    await repository.addToPlaylist(second.id, "b");

    expect((await repository.getEntries(first.id)).map((e) => e.mediaId)).toEqual(["a"]);
    expect((await repository.getEntries(second.id)).map((e) => e.mediaId)).toEqual(["b"]);
  });
});
