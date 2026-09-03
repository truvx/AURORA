# AURORA Final Architecture Baseline

Status: canonical contract for future implementation, accepted 2026-09-03.

This document contains final decisions for the documentation-only foundation. It authorizes future implementation work in the order in `IMPLEMENTATION_PLAN.md`; it does not itself create application code or authorize prohibited provider behavior.

## 1. Product boundary

AURORA is a personal-use music player for Android and Web. Initial music providers are:

1. YouTube for legitimate discovery and supported visible foreground playback.
2. Local/user-owned music files for native playback and actual file metadata.

Spotify and Apple Music are not v1 providers. YouTube extraction, separate audio streams, DRM bypass, scraping, unauthorized download/offline caching, hidden/background playback workarounds, credential scraping, fake playback, fake authentication, fake quality, invented APIs, and fabricated URLs are prohibited.

## 2. Cross-platform architecture

Android is Kotlin + Jetpack Compose. Web is TypeScript + React 19.2.7 + Next.js 16.3.4 App Router. They are separate implementations with separate composition roots.

The dependency direction is:

```text
Presentation -> Application/state holders -> Domain -> Data -> Infrastructure/Platform
```

Presentation renders immutable state and forwards typed intents. Domain owns provider-neutral models, policies, use cases, and state machines. Data owns repositories, persistence, provider adapters, mappers, and bounded caches. Infrastructure/Platform owns Media3, browser media and Media Session, Android/Web storage, secure storage, network, auth adapters, haptics, and lifecycle.

Kotlin Multiplatform executable sharing is rejected for v1. Shared artifacts are limited to versioned language-neutral contracts/schemas, domain concepts, semantic design-token definitions where useful, and deterministic fixtures. No Android playback, URI, storage, haptic, lifecycle, or SDK type crosses into Web. Contract tests protect parity.

## 3. Provider contract

`MusicProvider` is capability-based. Provider-specific DTOs remain in adapters. A capability snapshot controls every action in the UI and executor. Initial providers are `YouTubeProvider` and `LocalLibraryProvider`.

### YouTube

Public YouTube Data API v3 provides bounded discovery and metadata requests. The official visible YouTube IFrame Player API provides Web playback and Android WebView embedded playback. The app queue stores provider IDs and uses official cue/load methods. Autoplay is opt-in and may be blocked.

The YouTube player remains visible and user-recognizable. AURORA does not control a separate audio stream, guarantee quality, normalize audio, download bytes, offline-cache media, play when hidden/screen-off, expose notification/lock-screen/background playback, block ads, alter branding, or infer licensed lyrics from captions/descriptions. Public metadata follows current API storage/refresh rules and provider attribution is preserved. The complete matrix is `YOUTUBE_CAPABILITY_MATRIX.md`.

### Local files

Local files are referenced through platform permissions/handles/URIs. Actual codec/container, bitrate, sample rate, channels, bit depth, duration, lossless state, and quality are derived from measured media metadata. Unknown remains unknown. Normalization analysis and playback DSP never alter original bytes.

## 4. Canonical playback

Each client process has exactly one `PlayerCoordinator`, one canonical `PlayerState`, and one canonical `QueueState`. Mini-player, Now Playing, queue, notification, lock-screen/media controls, hardware/external controls, and Web Media Session all consume projections of that state and send typed `PlayerCommand`s to the coordinator. No screen owns a second player.

Player states are `Idle`, `Loading`, `Ready`, `Playing`, `Paused`, `Buffering`, `Seeking`, `Completed`, and `Error`. The contract includes `PlaybackPosition`, `PlaybackError`, `RepeatMode`, `ShuffleMode`, and `AudioFocusState` (`Unknown`, `Gained`, `LostTransiently`, `LostPermanently`, `Ducking`).

Android local playback uses Media3. Web local playback uses browser media APIs. YouTube playback uses the visible IFrame adapter. Queue traversal, repeat, shuffle, interruption, becoming-noisy, focus, stale callback, cancellation, buffering, unknown duration, persistence, and recovery behavior are defined in `PLAYER_STATE_MACHINE.md`.

Natural engine completion is authoritative. For history/recommendation aggregation, continuous playback reaching 90% of known duration counts as completed; seeking alone does not. A skip before that threshold is a skip. Unknown duration has no percentage shortcut.

## 5. Audio quality

The global `Preferred Audio Quality` values are `Auto`, `Low`, `Medium`, `High`, `Lossless`, and `Hi-Res`. They are preferences, not guarantees:

```text
RequestedQuality + ProviderCapabilities + TrackCapabilities -> ActualQuality
```

Only actual source facts can produce codec, bitrate, sample rate, bit depth, channels, lossless, or Hi-Res labels. Local files expose measured facts. YouTube resolves to provider-determined/unknown quality because the selected player does not expose a supported AURORA-controlled quality contract. See `AUDIO_QUALITY_POLICY.md`.

## 6. Loudness normalization

The global `Volume Normalization` values are `Off`, `Quiet`, `Normal`, and `Loud`. `Normal` is the default. Local playback uses ReplayGain or offline integrated-loudness analysis, album gain in album context when reliable, track gain otherwise, bounded gain, true-peak protection, and a last-resort limiter. Defaults are Quiet `-18 LUFS-I`, Normal `-14 LUFS-I`, Loud `-11 LUFS-I`; gain cap `+6 dB`; peak ceiling `-1.0 dBTP`; attenuation caps are `-18 dB` except Loud `-12 dB`. The processing is reversible and non-destructive.

YouTube is provider-managed/unavailable for AURORA normalization. The product separates global preference, track metadata, provider capabilities, and actual applied gain. See `LOUDNESS_POLICY_FINAL.md`.

## 7. Persistence

Android uses Room over SQLite. Web uses IndexedDB behind repositories. PostgreSQL is deferred to a future sync service and is not required for v1.

The logical model covers tracks/media items, artists, albums, artwork, technical metadata, local files, library entries, playlists and ordered playlist tracks, favorites, listening events, completion, skips, resume positions, recommendations, AI sessions/messages/tool calls subject to privacy, provider account metadata, settings, and queue snapshots. Stable provider/source identity is required. Tokens remain in secure storage or server secret management, never ordinary database rows. Migrations are numbered, forward-applied, tested, and non-destructive. Remote YouTube/AI data is bounded by provider policy and cache freshness; local files/history/settings remain available offline.

## 8. AI architecture

`AIProvider` is provider-neutral and has `GeminiProvider` and `OpenAIProvider` implementations behind an AURORA AI gateway. The gateway owns provider secrets, quotas, redaction, provider selection, request limits, response validation, and audit-safe telemetry. Android/Web clients never ship provider keys.

Gemini uses project API access or explicit Google Cloud OAuth. Consumer Gemini app sessions are not read or reused. OpenAI uses an API project credential. A ChatGPT app/session/subscription is not treated as API authorization or API credit; no generic external "Sign in with ChatGPT" flow is selected for v1. AURORA identity is separate from provider identity and billing.

The model emits validated structured intent/tool calls only. The allowlist is `searchMusic`, `searchArtist`, `searchAlbum`, `searchRelatedMusic`, `getTrackMetadata`, `findSimilarMusic`, `recommendMusic`, `createQueue`, `playTrack`, `playAlbum`, `playPlaylist`, `addToQueue`, and `createPlaylist`. The executor resolves provider IDs, checks capabilities, bounds, privacy, authorization, and confirmation. Models cannot invent URLs or mutate arbitrary state. Typed provider/offline/auth/quota/safety/timeout failures fall back to deterministic search/recommendations and do not disrupt existing playback.

## 9. Recommendations

Candidate generation and ranking are separate. Deterministic generation/ranking is mandatory and available without AI. Signals include plays, completion, skips, likes, favorites, repeats, artists, albums, genres, language, mood, tempo, energy, recency, session/context, and playlists, each with privacy and freshness rules. AI may assist natural-language understanding, semantic candidate generation, and explanations, but not source URL creation or unvalidated ranking authority. Availability, exclusions, diversity, provenance, deterministic tie-breaking, and reason codes are applied after candidate generation.

## 10. Design system

AURORA uses an original Liquid Glass-inspired system, not Apple's source, assets, proprietary icons, or exact recreation. The stack is stable canvas, optional artwork atmosphere, contrast scrim, semantic glass, soft highlight, and readable content/control layers. Blur, translucency, dynamic artwork theming, shadows, and gradients are adaptive enhancements. Opaque and reduced-transparency fallbacks preserve meaning and contrast.

Light and dark themes, Inter typography, Material Symbols Outlined, neutral-first semantic tokens, restrained radii, reserved layout dimensions, and platform conventions are final. Text is never left unreadable over artwork. The full design reference is `AURORA_MASTER_DESIGN_SYSTEM.md`, with final typography and icon rules in `TYPOGRAPHY_FINAL.md` and `ICONOGRAPHY_FINAL.md`.

## 11. Motion and haptics

Motion tokens are 80 ms instant, 140 ms quick, 220 ms standard, 360 ms emphasized, and 600-1200 ms ambient where justified. Use transform/opacity-first animation, responsive/standard/soft/settle springs, gesture thresholds with bounded velocity, interruption/reversal, lifecycle cancellation, and no layout-thrashing animated blur. Priorities are album-to-player, mini-player expansion, search, sheets, queue/reorder, playlist changes, scrubber, download state, and AI recommendation appearance.

`HapticEngine` is the single haptic abstraction. It maps tap, selection, toggle, slider, scrub snap, drag start/drop, queue reorder, favorite, download completion, success, warning, and error to device capabilities. It is sparse, rate-limited, cancellable, and never vibrates per scroll or scrub frame. Visual/semantic feedback remains complete without haptics. Reduced motion removes nonessential travel/bounce/parallax/animated blur while preserving state and focus clarity.

## 12. Accessibility

Support Android text scaling and Web zoom, screen readers, semantic labels, keyboard/focus navigation, 48 dp Android and 44 CSS px Web touch targets, sufficient contrast after artwork composition, non-color-only state, reduced motion, reduced transparency, opaque glass fallback, and haptic fallback. Queue reorder and scrub have keyboard/accessible alternatives. Accessibility semantics and behavior are tested, not inferred from screenshots.

## 13. Security and privacy

No hardcoded, bundled, logged, or ordinary-DB secrets, tokens, cookies, credentials, private URLs, or authorization headers. Do not read another app's private storage, cookies, database, tokens, or credentials. Use supported OAuth/API flows with state, PKCE where applicable, exact redirects, scoped permissions, issuer/audience/expiry checks, secure storage, refresh/revocation, logout, and typed failures. Validate all user, provider, file, URL, and AI data at boundaries. Listening history is private by default, minimized, configurable, exportable/deletable, and never sent to AI without opt-in.

## 14. Testing and performance

Test the owning layer: domain policies/state/queue/ranking/tool validation, repositories/migrations, provider capability contracts, Media3/browser lifecycle, secure storage, AI gateway contracts, haptics, UI semantics/accessibility, and cross-surface canonical playback. Use deterministic fakes and real-boundary integration tests. Cover empty/offline/unknown/unsupported/expired/retry/error paths and stale callback ordering.

Protect a 16.7 ms frame budget, keep I/O off the UI thread, isolate high-frequency position state, lazy-load and dimension artwork, bound cache/concurrency/memory, page large queries, and measure startup and AI latency. No performance or quality claim is accepted without device/browser evidence.

## 15. Toolchain contract

The prepared baseline is Android Studio `2026.1.3` installed locally, AGP `9.4.0`, Gradle Wrapper `9.6.0`, JDK `17.0.20.1`, Kotlin `2.4.10`, Compose `1.12.0`, Material 3 `1.4.0`, Media3 `1.11.0`, `compileSdk 37`, `targetSdk 36`, `minSdk 26`, Node `24.20.0` LTS for project/CI, React `19.2.7`, Next.js `16.3.4`, TypeScript `5.16.1`, npm 11, GitHub Actions Ubuntu `24.04`, Temurin 17, and Android Build Tools `36.0.0`. Compose `1.12.0` requires compile SDK 37. Target SDK remains 36 for the initial Android 16 behavior/release baseline and is not required to equal compile SDK. The local machine retains Node `26.3.1`; CI/project setup uses the LTS line. No global Gradle or Kotlin CLI is required. See `TOOLCHAIN_MATRIX.md` and `ADR-016-VERSION-MATRIX.md`.

## 16. Change control

Any material change to this contract requires a new ADR, updated detailed documents and schemas, affected tests, a security/accessibility/provider review, and a consistency audit. Future implementation starts at Phase 0 and follows `AGENTS.md`.
