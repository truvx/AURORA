# AURORA Master Architecture Summary

Status: final orientation document, 2026-09-03. The canonical implementation contract is `FINAL_ARCHITECTURE_BASELINE.md`. This repository remains documentation-only; this task did not create application source, build files, dependencies, API clients, playback, authentication, or database code.

## Product

AURORA is a personal-use music player for Android and Web. Initial providers are exactly YouTube and local/user-owned music. AURORA provides legitimate YouTube discovery and visible foreground playback where the official APIs permit it, high-quality local-file playback based on measured capabilities, one canonical player/queue, optional Gemini/OpenAI-assisted discovery through a secured gateway, deterministic recommendations, and an original Liquid Glass-inspired accessible interface.

Spotify and Apple Music are outside v1. YouTube extraction, separate audio streams, DRM bypass, unauthorized downloading/offline caching, credential scraping, hidden background playback, fake authentication, fake playback, fabricated quality, and fabricated endpoints are prohibited.

## Cross-platform architecture

Android is Kotlin + Jetpack Compose + AndroidX Media3. Web is TypeScript + React 19.2.7 + Next.js 16.3.4 App Router. The clients are separate implementations with separate composition roots.

```text
Presentation -> Application/state holders -> Domain -> Data -> Infrastructure/Platform
```

Shared material is limited to versioned provider-neutral contracts, schemas, design-token names/values where useful, and deterministic cross-platform fixtures. Kotlin Multiplatform executable sharing is not in v1 because it would add Kotlin/JS or Wasm build complexity while still requiring TypeScript/browser adapters. No Android playback, URI, storage, haptic, or lifecycle abstraction crosses into Web.

## Toolchain baseline

Android uses `compileSdk 37`, `targetSdk 36`, and `minSdk 26`, with AGP `9.4.0`, Gradle Wrapper `9.6.0`, JDK `17.0.20.1`, Kotlin `2.4.10`, Compose `1.12.0`, and Media3 `1.11.0`. Compose 1.12.0 requires compile SDK 37. Target SDK deliberately remains 36 for the initial Android 16 behavior/release baseline and is independently reviewed from compile SDK. Web uses Node `24.20.0` LTS for project/CI, React `19.2.7`, Next.js `16.3.4`, and TypeScript `5.16.1`; the local Node `26.3.1` is retained. The SDK/toolchain evidence and compatibility references are in `TOOLCHAIN_MATRIX.md` and `ADR-016-VERSION-MATRIX.md`.

## Providers and YouTube

`MusicProvider` is capability-based. The initial implementations are `YouTubeProvider` and `LocalLibraryProvider`.

YouTube discovery/metadata use public YouTube Data API v3 with bounded requests and provider attribution. Playback uses the official visible YouTube IFrame Player API: a visible Web player and a visible Android WebView player. The app queue owns provider IDs and advances through official load/cue methods. Autoplay is opt-in and may be blocked.

YouTube does not provide an AURORA-controlled audio-only stream or quality guarantee. AURORA does not download, offline-cache, extract, split, hide, background-play, or screen-off-play YouTube media. The YouTube capability matrix is the source of truth for UI action availability.

## Canonical player

Each client process has one `PlayerCoordinator` owning immutable `PlayerState` and `QueueState`. Mini-player, Now Playing, queue, notification, lock-screen/media controls, and external controls consume projections of that state and send typed `PlayerCommand`s back to the coordinator. The state machine is `Idle`, `Loading`, `Ready`, `Playing`, `Paused`, `Buffering`, `Seeking`, `Completed`, and `Error`, with explicit `PlaybackPosition`, `PlaybackError`, `RepeatMode`, `ShuffleMode`, and `AudioFocusState` contracts.

Android local playback uses Media3. Web local playback uses browser media APIs; Web YouTube playback uses the visible IFrame adapter. YouTube has no promised background/screen-off projection; local playback may expose platform controls when the adapter supports them.

## Quality and normalization

`Preferred Audio Quality` is `Auto`, `Low`, `Medium`, `High`, `Lossless`, or `Hi-Res`. The resolution is:

```text
RequestedQuality + ProviderCapabilities + TrackCapabilities -> ActualQuality
```

Actual codec, bitrate, sample rate, channels, bit depth, lossless state, and Hi-Res state are shown only when sourced from actual metadata. Local files are inspected; YouTube is provider-determined/unknown for AURORA's quality control.

`Volume Normalization` is `Off`, `Quiet`, `Normal`, or `Loud`. Local audio uses ReplayGain or offline integrated-loudness analysis, bounded gain, true-peak protection at `-1.0 dBTP`, and a last-resort limiter. Default targets are Quiet `-18 LUFS-I`, Normal `-14 LUFS-I`, and Loud `-11 LUFS-I`. Source bytes are never modified. YouTube normalization is provider-managed/unavailable, not simulated by a false claim.

## Persistence

Android uses Room over SQLite. Web uses IndexedDB. Both are behind repositories and persist local library metadata, tracks, artists, albums, playlists, ordered playlist tracks, favorites, history, completion/skips, resume positions, recommendations, AI sessions/tool-call audit records subject to privacy settings, provider account metadata, settings, local-file references, and queue snapshots. Tokens stay in secure storage or server secret management. PostgreSQL is reserved for a future sync service and is not required for v1.

## AI and recommendations

`AIProvider` is implemented by `GeminiProvider` and `OpenAIProvider` behind an AURORA AI gateway. Gemini uses project API access or explicit Google Cloud OAuth; OpenAI uses an API project credential. Consumer Gemini/ChatGPT app sessions are never read or treated as API credentials. Provider keys remain server-side.

Models produce validated structured intent/tool calls from the allowlist in `AURORA_AI_AGENT_SPEC.md`. The domain executor resolves provider IDs, checks capabilities and authorization, applies confirmations, and then creates a queue or playback command. Models cannot invent URLs or mutate arbitrary state. Deterministic candidate generation/ranking remains available offline; AI is optional semantic discovery/candidate generation.

## Design, motion, haptics, accessibility

The original Liquid Glass-inspired system uses stable canvas, optional artwork atmosphere, contrast scrim, semantic glass, soft highlight, and content layers. Blur/transparency are adaptive enhancements with opaque/reduced-transparency fallbacks. Light/dark themes, artwork-driven color, Inter typography, Material Symbols Outlined, semantic colors, readable contrast, scalable text, keyboard navigation, screen-reader labels, and non-color status indicators are mandatory.

Motion uses named duration/easing/spring tokens, continuity for connected surfaces, interruption/cancellation, transform/opacity-first rendering, and reduced-motion variants. `HapticEngine` is centralized, capability-aware, sparse, and rate-limited; no per-scroll or per-frame vibration. Visual/semantic feedback always works without haptics.

## Security, testing, performance

No hardcoded or logged secrets, provider tokens, cookies, credentials, private URLs, or authorization headers. Validate all provider/file/AI/user data at typed boundaries. Use supported OAuth state/PKCE and secure token lifecycle where applicable. Listening history is private, minimized, configurable, and deletable.

Tests live with their owning behavior: domain policies/state/ranking/tool validation, repositories/migrations, provider capabilities, Media3/browser lifecycle, secure storage, haptics, UI semantics/accessibility, and cross-surface canonical playback. Use deterministic fakes and real-boundary integration tests. Protect a 16.7 ms frame budget, isolate position ticks, bound artwork/concurrency/cache memory, page large data, and measure startup/AI latency before claiming targets.

## Implementation readiness

The seven architecture gates are resolved in ADR-010 through ADR-016. Toolchain preparation is recorded in `TOOLCHAIN_MATRIX.md`. The ordered implementation plan is `IMPLEMENTATION_PLAN.md`. Future implementation must follow the workflow in `AGENTS.md`, and any material change to this baseline requires a new ADR, tests, and a consistency audit.

## Detailed documents

Read `FINAL_ARCHITECTURE_BASELINE.md` first, then the relevant document and skill. Important references include `ARCHITECTURE.md`, `MODULE_ARCHITECTURE.md`, `PLAYER_STATE_MACHINE.md`, `YOUTUBE_CAPABILITY_MATRIX.md`, `AUDIO_QUALITY_POLICY.md`, `LOUDNESS_POLICY_FINAL.md`, `DATABASE_MODEL.md`, `AI_AUTHENTICATION_MATRIX.md`, `AURORA_AI_AGENT_SPEC.md`, `RECOMMENDATION_ENGINE.md`, `AURORA_MASTER_DESIGN_SYSTEM.md`, `AURORA_MOTION_SPEC.md`, `AURORA_HAPTIC_SPEC.md`, `ACCESSIBILITY_SPEC.md`, `SECURITY.md`, `TEST_ARCHITECTURE.md`, `WEB_ARCHITECTURE.md`, `ARCHITECTURE_REVIEW.md`, `ADR-016-VERSION-MATRIX.md`, `TOOLCHAIN_MATRIX.md`, and `IMPLEMENTATION_PLAN.md`.
