---
name: aurora-music-provider
description: Design or implement AURORA music-provider adapters, capability discovery, YouTube-supported playback, and local-library provider behavior.
---

# AURORA music providers

Use for provider interfaces, YouTube discovery/playback, local-file library integration, metadata mapping, artwork, lyrics, recommendations, or provider-specific failures. Read `docs/MUSIC_PROVIDER_ARCHITECTURE.md` before changing provider contracts.

## Provider model

Implement `MusicProvider` with explicit `ProviderCapabilities`: search, metadata, artwork, playback, queue, recommendations, lyrics, offline playback, download, quality selection, and background playback. The UI must query capabilities and show unavailable actions as unavailable or omit them; it must never infer support from provider name.

The first release includes only:

- `YouTubeProvider`: supported search/discovery and permitted playback integration. Preserve the official playback surface and applicable API/policy requirements.
- `LocalLibraryProvider`: user-owned/imported files, offline playback, metadata extraction, artwork, queue, and local analysis.

Do not add Spotify or Apple Music in v1.

## YouTube safety boundary

- Do not extract a separate audio stream from a YouTube video.
- Do not download, cache, or persist YouTube media bytes for offline playback.
- Do not bypass DRM, ads, login, rate limits, embed restrictions, or official playback controls.
- Do not scrape cookies, credentials, private app storage, or undocumented tokens.
- Do not accept an LLM-invented URL as playable media. Resolve a typed provider item through the adapter and policy checks.
- Keep unsupported quality, lyrics, download, and offline actions explicit. Never label a permitted playback path as lossless or downloadable without source evidence.

## Local files

Treat local files as a separate provider. Preserve URI permissions and ownership semantics. Map actual codec, container, bitrate, sample rate, channel count, bit depth, duration, and lossless confidence when available. An unavailable field is `unknown`, not a fabricated value.

## Failure handling

Use typed errors: unsupported capability, auth required, consent required, rate limited, network unavailable, item unavailable, policy blocked, metadata unavailable, and transient provider failure. Include retryability and user-safe copy. Keep retries bounded and cancellation-aware.

## Example

```kotlin
if (!provider.capabilities.supports(ProviderCapability.QUALITY_SELECTION)) {
    state = state.copy(qualityControl = QualityControl.Unavailable)
}
```
