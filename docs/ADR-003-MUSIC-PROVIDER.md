# ADR-003: Capability-based music providers

## Status

Accepted as foundation; YouTube behavior is finalized by ADR-011 and YOUTUBE_CAPABILITY_MATRIX.md.

## Context

AURORA combines supported YouTube discovery/playback with user-owned local files. Providers have materially different capabilities and policy constraints.

## Decision

Use a provider-neutral `MusicProvider` identity plus small capability interfaces (`SearchProvider`, `MetadataProvider`, `PlaybackProvider`, `ArtworkProvider`, `LibraryProvider`, `DownloadProvider`, `LyricsProvider`, and recommendation capability where supported). Each adapter declares explicit capabilities, typed errors, provenance, retryability, and source identity.

YouTube is limited to supported mechanisms; the architecture forbids extraction, unauthorized downloading, DRM bypass, scraping, and fake quality. Local files are a first-class provider.

## Alternatives considered

- One giant interface — rejected because unsupported methods become fake/no-op promises.
- Provider-specific branches throughout UI — rejected because it leaks policy and prevents future providers.
- Unofficial stream extraction — rejected for compliance, security, and durability reasons.

## Consequences

UI must query capabilities and display limitations clearly. Adapter and mapping code increases, but adding a future provider remains localized.

## Future migration

New providers implement the stable capability contracts. Capability snapshots and serialized IDs are versioned; provider policy changes must not require domain/UI rewrites.
