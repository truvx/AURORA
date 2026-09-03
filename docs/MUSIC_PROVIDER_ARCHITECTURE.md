# AURORA music-provider architecture

Status: final v1 provider boundary. The capability matrix, not provider name checks, controls product actions.

## Scope

v1 has `YouTubeProvider` for public discovery/metadata and visible foreground embedded playback, and `LocalLibraryProvider` for user-owned/imported files and offline playback. Spotify and Apple Music are intentionally excluded from v1. New providers must implement the stable provider contract rather than add provider branches across the app.

## Capability contract

Capabilities are explicit: search, metadata, artwork, playback, queue, recommendations, lyrics, download, offline playback, quality selection, and background playback. UI queries capabilities and does not show unsupported controls. Capability absence is normal, not an exceptional crash.

## YouTube boundary

Use supported APIs/official playback surfaces and applicable policy. Do not extract separate audio, download/cache media bytes, bypass DRM/ads/restrictions, scrape credentials/cookies/private tokens, or accept invented URLs. Map policy-blocked, consent, auth, rate-limit, unavailable, and transient outcomes to typed errors.

## Local library

Preserve user URI permissions and local ownership. Extract actual technical metadata where available: container, codec, bitrate, sample rate, channels, bit depth, duration, and lossless confidence. Keep missing fields unknown. Treat files as a separate source/provider with offline playback and local analysis.

## Repository behavior

Adapters map external DTOs to provider-neutral models and expose cancellation, bounded retries, pagination, provenance, and retryability. Contract tests cover capability matrices and error mapping. No provider-specific behavior belongs in composables or shared player logic.
