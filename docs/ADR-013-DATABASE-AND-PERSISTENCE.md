# ADR-013: Database and Persistence

- Status: Accepted
- Date: 2026-09-03

## Decision

AURORA uses a local-first persistence boundary with platform-native embedded storage:

- Android: Room over SQLite for relational entities, migrations, indexes, and transactional queue/library updates.
- Web: IndexedDB for the local relational-like object store and indexes, accessed through a repository boundary. No SQLite/WASM engine is required for v1.
- Optional future sync/backend: PostgreSQL behind a versioned repository/sync API. It is not required for the initial single-device/local-first experience and no backend schema is implemented in this phase.

This is the smallest robust architecture for user-owned local files, listening history, queues, settings, and offline operation. Provider metadata and AI results are bounded caches, not an assumption that all remote data is permanently available.

## Alternatives considered

| Option | Decision | Reason |
| --- | --- | --- |
| Android Room | Chosen | Official Android relational persistence, compile-time query validation, migrations, and testable DAO boundary. |
| Raw SQLite on Android | Rejected for v1 | More manual mapping, migration, and query safety without a benefit for this product scope. |
| Web IndexedDB | Chosen | Browser-native offline storage with indexes and no server requirement for the local-first baseline. |
| SQLite/WASM on Web | Rejected for v1 | Adds WASM delivery, worker, persistence, and quota complexity before search/query scale requires it. |
| PostgreSQL from first launch | Deferred | Useful for multi-device sync and account data, but unnecessary for the first local-first release and expands security/deployment scope. |

Assumptions: v1 is local-first and single-device by default; browser quota and Android file access are handled as explicit capabilities. Confidence: high for Room/IndexedDB as the smallest native stores; medium for any future sync model, which is intentionally outside this baseline.

## Persistence contract

The conceptual model in `DATABASE_MODEL.md` is final for v1. It includes tracks/media items, artists, albums, artwork references, technical metadata, local files, library entries, playlists and ordered entries, favorites, listening events, resume/completion/skips, recommendations, AI conversations/messages/tool-call audit records, provider accounts, settings, and queue snapshots.

- Stable identity is `(providerId, providerItemId)` for remote content and a durable local-file identity for imported files.
- Track technical metadata is nullable and truthful: codec/container, bitrate, sample rate, channels, bit depth, duration, lossless status, and source quality remain unknown when not measured.
- Original audio bytes are referenced, never rewritten by normalization or metadata analysis.
- Provider tokens and refresh credentials are not ordinary database rows; secure storage owns them and the database stores only non-secret account metadata and references.
- All writes use repository transactions. Queue persistence is a snapshot of ordered IDs plus repeat/shuffle state and a restoration timestamp; playback engine state itself is not persisted as an active instance.
- Migrations are numbered, forward-applied, tested against a clean database and the previous production schema, and never silently drop user data.
- Required indexes cover provider identity, normalized search fields, playlist ordering, listening-event time, favorite state, local-file URI/key, and recommendation candidate lookup. Search uses platform-supported indexed prefix/token strategies; full-text behavior is a measured follow-up, not a promise.

## Offline and retention behavior

Local files, saved playlists, favorites, settings, queue snapshot, and local history remain available offline. Remote YouTube metadata/search and AI require network access and policy-compliant cache freshness. Failed writes use explicit retry state; no silent data loss. Listening history is personal data with export/delete/retention controls owned by the privacy settings boundary.

## Sources

- [Room persistence library](https://developer.android.com/training/data-storage/room)
- [IndexedDB API](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API)
- [PostgreSQL documentation](https://www.postgresql.org/docs/current/index.html)
