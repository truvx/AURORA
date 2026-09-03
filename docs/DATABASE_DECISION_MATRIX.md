# Database Decision Matrix

Status: final v1 contract, 2026-09-03.

| Concern | Android | Web | Optional backend | Final choice |
| --- | --- | --- | --- | --- |
| Primary store | Room/SQLite | IndexedDB | PostgreSQL | Native local store per platform; PostgreSQL only for later sync |
| Track and library metadata | Room entities and DAOs | Indexed object stores/repositories | Relational tables if sync is added | Local-first metadata |
| Playlists and ordered entries | Transactional Room tables | Transactional repository operation across object stores | Relational tables | Ordered IDs with stable provider/local identity |
| Favorites and settings | Room | IndexedDB | Syncable records later | Local immediately; optional sync later |
| History, completion, skips | Append-friendly Room events plus aggregates | Event object store plus aggregates | Privacy-aware sync/event ingestion | Record explicit play/skip/completion facts |
| Recommendations | Persist bounded candidate/explanation snapshot | Persist bounded snapshot | Optional derived service records | Deterministic baseline never depends on AI |
| AI sessions/tool calls | Bounded conversation and audit records | Bounded conversation and audit records | Gateway audit/usage records with redaction | Persist only what the privacy setting permits |
| Provider accounts | Non-secret account metadata; tokens secure storage | Non-secret account metadata; tokens secure storage | Encrypted secret manager/session store | Never store raw tokens in the ordinary DB |
| Local files/imports | URI/access grants plus technical metadata | File handles/metadata where browser permits | None for source bytes | Reference user-owned bytes; do not copy remote YouTube media |
| Migrations | Versioned Room migrations | Versioned schema upgrades | Numbered SQL migrations | Test forward upgrades and preserve user data |
| Search | Indexed normalized fields; FTS only after measurement | Indexed normalized fields; worker if needed | Optional full-text search later | Bounded prefix/token search in v1 |
| Offline | Full local library/settings/history/queue | Full local library/settings/history/queue within browser quota | Not required | Offline is local-only and explicit |
