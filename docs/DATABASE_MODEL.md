# AURORA database model

Status: final v1 logical model, accepted by ADR-013. This remains a documentation-only model; no schema, migration, driver, or database implementation is created in the foundation phase.

## Scope

Android will implement this model with Room over SQLite. Web will implement it with IndexedDB behind repositories. A future sync service may use PostgreSQL, but it is not required for v1.

## Entity overview

```text
Provider 1──* MediaItem *──1 Album
              │       └──* Artist (through MediaItemArtist)
              └──* Artwork

MediaItem 1──* TrackTechnicalMetadata
MediaItem 1──* ListeningEvent
Playlist 1──* PlaylistEntry *──1 MediaItem
UserLibrary 1──* LibraryEntry *──1 MediaItem
MediaItem 1──1 LocalFile (optional)
MediaItem 1──1 ResumePosition (per source/device profile)
AIAccount 1──* AIConversation 1──* AIMessage
```

## Entities

| Entity | Key fields | Notes |
|---|---|---|
| `Provider` | stable ID, kind, capability snapshot/version | No secret values; capability snapshots can expire |
| `MediaItem` | provider/source ID, kind, title, provenance, availability | Provider-neutral metadata with unknown optional fields |
| `Album` | provider/source ID, title, album artist, year, artwork ref | Albums may be partial or provider-specific |
| `Artist` | provider/source ID, name, artwork ref | Track/album relationships use join records |
| `Artwork` | source ref, dimensions, palette version, cache metadata | Store references/derived palette, not unbounded duplicate blobs |
| `TrackTechnicalMetadata` | codec, container, bitrate, sample rate, bit depth, channels, duration, lossless confidence | Source and extraction version required |
| `LocalFile` | stable file/URI identity, permission status, size, modified time, import state | Preserve user ownership; avoid unsafe paths |
| `LibraryEntry` | media ID, favorite flag, added time, local/download state | One user only today, model scope remains explicit |
| `Playlist` | ID, name, created/updated, source kind | User-created vs imported/source-backed |
| `PlaylistEntry` | playlist ID, stable entry ID, media ID, position, added time | Position ordering must be deterministic |
| `ListeningEvent` | event ID, media ID, timestamp, session ID, event kind, progress | Privacy setting controls retention/analysis |
| `ResumePosition` | source/provider/media ID, position, duration snapshot, updated time | Never key only by title/artist |
| `DownloadOrImportRecord` | media/file ID, status, source kind, path/URI ref, error category | YouTube media bytes are never represented as downloadable in this model |
| `Setting` | typed key/version/value | Sensitive settings are separated from secure token store |
| `AIAccount` | provider, connection state, scopes/metadata, timestamps | Tokens are external to ordinary database |
| `AIConversation` | conversation ID, provider, created/updated, retention policy | Content retention is user-controlled |
| `AIMessage` | conversation ID, role, validated content/tool refs, timestamps | Do not store secrets/raw provider payloads by default |

## Relationships and constraints

- Provider/source identity is part of every external media key.
- Deleting a local file marks the library item unavailable without deleting user playlist history automatically.
- Playlist order uses stable entries and transactional reorder operations.
- Listening events are append-oriented, bounded/retained by policy, and never required for core playback.
- Artwork caches are evictable; metadata records must not depend on an in-memory image cache.
- Secure tokens are not queryable through ordinary repositories.

## Migrations

Every schema change needs a version, forward migration, rollback/backup consideration, and test fixture. Unknown fields must survive migrations when feasible. Provider capability snapshots and technical metadata carry extraction/schema versions so old facts are not silently treated as current.

## Indexing principles

Index stable media/provider IDs, normalized search keys, playlist/order pairs, timestamps for recent history, local-file scan identity, and resume lookup keys. Avoid indexes on unbounded raw prompts or large artwork/audio blobs.

## Offline behavior

The database must support browsing imported/local music, favorites, playlists, recent local playback, settings, resume positions, and deterministic recommendations offline. Network-derived provider data can be stale/unavailable and must be labeled accordingly.
