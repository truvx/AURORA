import { StoredTrack } from "./db";

/**
 * Groups library tracks into albums and artists.
 *
 * Derived from tags at read time rather than stored: the grouping is cheap, and a second
 * copy of it in IndexedDB would be one more thing to keep in step with a rescan.
 *
 * Tracks whose tags are missing collect under an explicit "Unknown" group rather than being
 * hidden or attributed to a guess.
 */

export const UNKNOWN_ALBUM = "Unknown album";
export const UNKNOWN_ARTIST = "Unknown artist";

export interface TrackGroup {
  /** Stable key for routing and React keys. */
  key: string;
  name: string;
  trackIds: string[];
  /** Present only when every track in the group agrees, so it is never a guess. */
  artist?: string;
}

export function groupByAlbum(tracks: StoredTrack[]): TrackGroup[] {
  const groups = new Map<string, StoredTrack[]>();

  for (const track of tracks) {
    const name = track.album?.trim() || UNKNOWN_ALBUM;
    const existing = groups.get(name);
    if (existing) existing.push(track);
    else groups.set(name, [track]);
  }

  return [...groups.entries()]
    .map(([name, members]) => ({
      key: name,
      name,
      trackIds: members.map((track) => track.id),
      // Only claim an album artist when every track agrees; a compilation stays unattributed.
      artist: singleArtist(members),
    }))
    .sort(byName);
}

export function groupByArtist(tracks: StoredTrack[]): TrackGroup[] {
  const groups = new Map<string, StoredTrack[]>();

  for (const track of tracks) {
    const name = track.artist?.trim() || UNKNOWN_ARTIST;
    const existing = groups.get(name);
    if (existing) existing.push(track);
    else groups.set(name, [track]);
  }

  return [...groups.entries()]
    .map(([name, members]) => ({
      key: name,
      name,
      trackIds: members.map((track) => track.id),
    }))
    .sort(byName);
}

function singleArtist(tracks: StoredTrack[]): string | undefined {
  const names = new Set(
    tracks.map((track) => track.artist?.trim()).filter((name): name is string => !!name)
  );
  return names.size === 1 ? [...names][0] : undefined;
}

/**
 * Alphabetical, but the Unknown groups sort last: they are a catch-all, not a name, and
 * putting them first would bury real albums under whatever lacks tags.
 */
function byName(a: TrackGroup, b: TrackGroup): number {
  const aUnknown = a.name === UNKNOWN_ALBUM || a.name === UNKNOWN_ARTIST;
  const bUnknown = b.name === UNKNOWN_ALBUM || b.name === UNKNOWN_ARTIST;
  if (aUnknown !== bUnknown) return aUnknown ? 1 : -1;
  return a.name.localeCompare(b.name);
}
