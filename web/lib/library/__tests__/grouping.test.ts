import { describe, expect, it } from "vitest";
import { StoredTrack } from "../db";
import { UNKNOWN_ALBUM, UNKNOWN_ARTIST, groupByAlbum, groupByArtist } from "../grouping";

function track(id: string, album?: string, artist?: string): StoredTrack {
  return {
    id,
    title: `Track ${id}`,
    album,
    artist,
    path: [],
    fileName: `${id}.flac`,
    sizeBytes: 1,
    lastModified: 0,
    addedAt: 0,
  };
}

describe("grouping", () => {
  it("groups tracks by album", () => {
    const groups = groupByAlbum([
      track("1", "Kid A", "Radiohead"),
      track("2", "Kid A", "Radiohead"),
      track("3", "Vespertine", "Björk"),
    ]);

    expect(groups.map((g) => [g.name, g.trackIds.length])).toEqual([
      ["Kid A", 2],
      ["Vespertine", 1],
    ]);
  });

  it("names an album artist only when every track agrees", () => {
    const consistent = groupByAlbum([
      track("1", "Kid A", "Radiohead"),
      track("2", "Kid A", "Radiohead"),
    ]);
    expect(consistent[0].artist).toBe("Radiohead");

    // A compilation has no single artist, and inventing one would be wrong.
    const compilation = groupByAlbum([
      track("1", "Mixtape", "Artist A"),
      track("2", "Mixtape", "Artist B"),
    ]);
    expect(compilation[0].artist).toBeUndefined();
  });

  it("collects untagged tracks under an explicit unknown group", () => {
    const groups = groupByAlbum([track("1", undefined, "Someone"), track("2", "Real", "X")]);

    expect(groups.map((g) => g.name)).toEqual(["Real", UNKNOWN_ALBUM]);
  });

  it("sorts unknown last so it does not bury real albums", () => {
    const groups = groupByAlbum([
      track("1"),
      track("2", "Aardvark"),
      track("3", "Zebra"),
    ]);

    expect(groups.map((g) => g.name)).toEqual(["Aardvark", "Zebra", UNKNOWN_ALBUM]);
  });

  it("treats a whitespace-only album as unknown", () => {
    const groups = groupByAlbum([track("1", "   ", "X")]);
    expect(groups[0].name).toBe(UNKNOWN_ALBUM);
  });

  it("groups tracks by artist", () => {
    const groups = groupByArtist([
      track("1", "A", "Aphex Twin"),
      track("2", "B", "Aphex Twin"),
      track("3", "C", "Boards of Canada"),
    ]);

    expect(groups.map((g) => [g.name, g.trackIds.length])).toEqual([
      ["Aphex Twin", 2],
      ["Boards of Canada", 1],
    ]);
  });

  it("collects untagged artists under an explicit unknown group", () => {
    const groups = groupByArtist([track("1", "A"), track("2", "B", "Real Artist")]);

    expect(groups.map((g) => g.name)).toEqual(["Real Artist", UNKNOWN_ARTIST]);
  });

  it("returns nothing for an empty library", () => {
    expect(groupByAlbum([])).toEqual([]);
    expect(groupByArtist([])).toEqual([]);
  });
});
