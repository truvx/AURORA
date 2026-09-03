# AURORA agent instructions

AURORA is a personal-use music player for Android and Web. It combines high-quality playback of user-owned local files with legitimate YouTube discovery and visible foreground playback where permitted, a shared queue/player model, an original Liquid Glass-inspired interface, restrained motion and meaningful haptics, accessible cross-platform controls, and optional Gemini/OpenAI music assistance.

## Current repository phase

The repository currently contains architecture, design, policy, implementation-plan, and toolchain documentation only. Do not create Android source, Jetpack Compose UI, React components, playback implementation, YouTube implementation, AI implementation, authentication implementation, database implementation, production API clients, placeholder screens, application build files, application dependencies, fake playback, fake authentication, fake quality, invented APIs, YouTube extraction, unauthorized downloading, or DRM work unless a later prompt explicitly authorizes the appropriate implementation phase.

The canonical contract is `docs/FINAL_ARCHITECTURE_BASELINE.md`. The ordered work is `docs/IMPLEMENTATION_PLAN.md`. Detailed policy documents and the final toolchain are linked from those files.

## Required workflow

BEFORE a major implementation:

1. inspect current code
2. read relevant docs
3. read relevant skills
4. make a plan
5. implement incrementally
6. test
7. build
8. review
9. update docs when architecture changes

Never make large speculative rewrites. Do not create a git commit unless explicitly requested.

## Architecture boundaries

Keep dependency direction:

```text
Presentation -> Application/state holders -> Domain -> Data -> Infrastructure/Platform
```

Presentation renders immutable state and forwards typed intents. Domain owns provider-neutral models, use cases, policies, and state machines. Data owns repositories, mappers, persistence, provider adapters, and bounded caches. Infrastructure/Platform owns Media3, browser media/Media Session, storage, secure storage, network, auth, haptics, and lifecycle adapters.

Android and Web are separate implementations with separate composition roots:

- Android: Kotlin, Jetpack Compose, AndroidX Media3, Room/SQLite, Android secure storage, Android lifecycle/media-session/haptic adapters.
- Web: TypeScript, React 19.2.7, Next.js 16.3.4 App Router, browser media/Media Session, IndexedDB, Web secure session handling, and browser lifecycle adapters.
- Shared: versioned provider-neutral contracts/schemas, domain concepts, design-token definitions where useful, and deterministic fixtures. Kotlin Multiplatform executable sharing is not part of v1.

Android toolchain baseline: `compileSdk 37`, `targetSdk 36`, `minSdk 26`, AGP `9.4.0`, Gradle Wrapper `9.6.0`, JDK 17, Kotlin `2.4.10`, Compose `1.12.0`, and Media3 `1.11.0`. Compose 1.12.0 requires compile SDK 37. Keep target SDK 36 until a separate Android 17 behavior and release review approves raising it; compile SDK and target SDK are independent. Web project/CI uses Node 24 LTS, React 19.2.7, Next.js 16.3.4, and TypeScript 5.16.1; retain the local Node 26 installation unless a documented requirement changes. See `docs/ADR-016-VERSION-MATRIX.md` and `docs/TOOLCHAIN_MATRIX.md`.

Do not force Android playback, URI, storage, haptic, or lifecycle abstractions into Web. Provider DTOs, SDK types, and platform types must not leak into shared domain/UI contracts.

## Music providers and YouTube

Initial providers are exactly:

1. YouTube
2. Local/user-owned music

Do not add Spotify or Apple Music in v1. `MusicProvider` is capability-based; the UI and AI executor query capability snapshots before rendering or executing actions.

YouTube discovery/metadata use bounded public YouTube Data API v3 requests. Playback uses a visible official YouTube IFrame Player API player on Web and a visible Android WebView surface. Preserve attribution, branding, user controls, required origin/client identity, and current policy behavior. Autoplay is opt-in and may be blocked.

Never scrape YouTube, extract audiovisual streams, separate audio, invent stream URLs, bypass DRM or geographic restrictions, download/cache YouTube media bytes, provide offline playback, create a hidden/background/screen-off player, block ads, modify the player, or scrape credentials/cookies/tokens. YouTube quality is provider-determined/unknown to AURORA; do not claim bitrate, codec, lossless, or Hi-Res. Do not infer lyrics from captions or descriptions. The complete contract is `docs/YOUTUBE_CAPABILITY_MATRIX.md`.

## Canonical player

There is one `PlayerCoordinator` per client process. It owns canonical immutable `PlayerState` and `QueueState`. Mini-player, Now Playing, queue, notification, lock-screen/media controls, hardware/external controls, and Web Media Session all consume projections of that state and send typed `PlayerCommand`s back to the coordinator. Never create a player per screen.

States are `Idle`, `Loading`, `Ready`, `Playing`, `Paused`, `Buffering`, `Seeking`, `Completed`, and `Error`. Contracts include `PlaybackPosition`, `PlaybackError`, `RepeatMode`, `ShuffleMode`, and `AudioFocusState`. Android local playback uses Media3. Web local playback uses browser media. YouTube uses its visible IFrame adapter and has no promised background/screen-off projection. See `docs/PLAYER_STATE_MACHINE.md`.

## Quality and normalization

The universal `Preferred Audio Quality` values are `Auto`, `Low`, `Medium`, `High`, `Lossless`, and `Hi-Res`. Resolve:

```text
RequestedQuality + ProviderCapabilities + TrackCapabilities = ActualQuality
```

Preferences are not guarantees. For local files derive codec/container, bitrate, sample rate, channels, bit depth, duration, lossless status, and quality from actual metadata. Unknown remains unknown. For YouTube expose only provider-determined/unknown state.

The universal `Volume Normalization` values are `Off`, `Quiet`, `Normal`, and `Loud`. Local playback uses ReplayGain or offline LUFS analysis, album gain in reliable album context, track gain otherwise, bounded gain, true-peak protection at `-1.0 dBTP`, and a last-resort limiter. Defaults are Quiet `-18 LUFS-I`, Normal `-14 LUFS-I`, and Loud `-11 LUFS-I`; gain cap `+6 dB`; attenuation caps `-18 dB` except Loud `-12 dB`. Processing is non-destructive. YouTube normalization is provider-managed/unavailable, not simulated. See `docs/LOUDNESS_POLICY_FINAL.md`.

## Database and persistence

Android uses Room over SQLite. Web uses IndexedDB behind repositories. PostgreSQL is deferred to a future sync service. Persist tracks, artists, albums, playlists, ordered playlist tracks, favorites, listening history, completion/skips, resume positions, recommendations, AI sessions/tool calls subject to privacy, provider account metadata, settings, local-file references, and queue snapshots. Use stable source/provider identity, indexes, numbered migrations, transactions, and explicit offline/error behavior. Never store provider tokens in the ordinary database; use secure storage or server secret management.

## AI and security

`AIProvider` is provider-neutral with `GeminiProvider` and `OpenAIProvider` behind an AURORA AI gateway. The gateway owns credentials, quotas, redaction, provider calls, response validation, and safe telemetry. Android/Web clients never ship provider keys.

Gemini uses project API access or explicit Google Cloud OAuth; the consumer Gemini app session is not read. OpenAI uses an API project credential; a ChatGPT app/session/subscription is not treated as API authorization or API credit, and no generic external Sign in with ChatGPT flow is assumed. Identity and API/model billing are separate.

AI may emit only validated structured intents/tool calls from the allowlist in `docs/AURORA_AI_AGENT_SPEC.md`: search, metadata, similar/recommendation, queue, playback proposal, and playlist proposal tools. The executor resolves IDs through providers, checks capabilities, privacy, authorization, bounds, and confirmation. Models may never invent media URLs or mutate arbitrary application state. Deterministic discovery/recommendation remains available offline.

Never hardcode, bundle, commit, log, or expose secrets, tokens, cookies, credentials, private URLs, or authorization headers. Never read another app's private storage, database, cookies, tokens, or credentials. Validate all external/user/provider/file/AI data at boundaries. Use supported authentication with state, PKCE where applicable, exact redirects, scopes, issuer/audience/expiry checks, secure storage, refresh/revocation, logout, timeouts, rate limits, and typed failures. Listening history is private by default, minimized, configurable, exportable, and deletable.

## Design, motion, haptics, accessibility

Use the original AURORA Liquid Glass-inspired system only. It uses stable canvas, optional artwork atmosphere, contrast scrim, semantic glass, soft highlight, and readable content layers. Blur/transparency/dynamic theming are adaptive enhancements with opaque and reduced-transparency fallbacks. Do not copy Apple source, assets, proprietary fonts, or icons.

Use Inter with system fallback and Material Symbols Outlined with Lucide fallback only where explicitly needed. Do not use emoji as UI icons. Keep readable type, reserved layout dimensions, semantic colors, sufficient contrast after artwork, and no color-only status.

Use named duration/easing/spring tokens, transform/opacity-first animation, interruption/reversal, lifecycle cancellation, bounded gesture velocity, and reduced-motion variants. `HapticEngine` is centralized, capability-aware, sparse, rate-limited, and never vibrates per scroll or scrub frame. Visual and semantic feedback must work without haptics.

Support Android text scaling, Web zoom, screen readers, semantic labels, keyboard/focus navigation, minimum 48 dp Android and 44 CSS px Web targets, sufficient contrast, reduced motion, reduced transparency, opaque glass fallback, non-color status, and accessible queue/scrub alternatives. See `docs/ACCESSIBILITY_SPEC.md`.

## Testing and performance

Test behavior at the owning layer: domain states/policies/queue/ranking/tool validation, repositories/migrations, provider capabilities, Media3/browser lifecycle, secure storage, AI gateway contracts, haptics, UI semantics/accessibility, and cross-surface canonical playback. Use deterministic fakes and real-boundary integration tests. Cover offline, unknown, unsupported, permission/auth, quota, policy, retry, stale callback, migration, deletion, and cancellation paths. Screenshots supplement behavior/semantics; line coverage is not proof.

Protect a 16.7 ms frame budget, keep I/O off the UI thread, isolate position ticks, lazy-load and dimension artwork, bound cache/concurrency/memory, page large queries, and measure startup, buffering, and AI latency. Do not sacrifice truthfulness, security, or accessibility for visual or performance shortcuts.

## Implementation sequence

Follow `docs/IMPLEMENTATION_PLAN.md` exactly unless a new ADR changes the order:

Phase 0 Toolchain; Phase 1 Repository/project skeleton; Phase 2 Design system foundation; Phase 3 Application shell/navigation; Phase 4 Local music library; Phase 5 Player engine; Phase 6 Queue and Now Playing; Phase 7 YouTube integration; Phase 8 Audio quality system; Phase 9 Loudness normalization; Phase 10 Library/favorites/playlists; Phase 11 AI foundation; Phase 12 Gemini integration; Phase 13 OpenAI integration; Phase 14 AI music agent; Phase 15 Recommendation engine; Phase 16 Haptics and advanced motion; Phase 17 Web application; Phase 18 Accessibility hardening; Phase 19 Performance optimization; Phase 20 Testing; Phase 21 CI/release.

## Skills and documents

Read `aurora-project-rules` first. Use the matching AURORA skill and document before changing that area:

- Architecture/contracts: `aurora-architecture`, `docs/ARCHITECTURE.md`, `docs/FINAL_ARCHITECTURE_BASELINE.md`, `docs/ADR-016-VERSION-MATRIX.md`
- Providers: `aurora-music-provider`, `docs/MUSIC_PROVIDER_ARCHITECTURE.md`, `docs/YOUTUBE_CAPABILITY_MATRIX.md`
- Player: `aurora-player`, `docs/PLAYER_STATE_MACHINE.md`
- Audio: `aurora-audio`, `docs/AUDIO_QUALITY_POLICY.md`, `docs/LOUDNESS_POLICY_FINAL.md`
- AI: `aurora-ai-music-agent`, `docs/AI_AUTHENTICATION_MATRIX.md`, `docs/AURORA_AI_AGENT_SPEC.md`
- Recommendations: `aurora-recommendations`, `docs/RECOMMENDATION_ENGINE.md`
- UI: `aurora-liquid-glass-ui`, `ui-ux-pro-max`, `docs/AURORA_MASTER_DESIGN_SYSTEM.md`
- Motion/haptics: `aurora-motion`, `aurora-haptics`, `docs/AURORA_MOTION_SPEC.md`, `docs/AURORA_HAPTIC_SPEC.md`
- Security/testing/web: `aurora-security`, `aurora-testing`, `aurora-web`, `docs/SECURITY.md`, `docs/TEST_ARCHITECTURE.md`, `docs/WEB_ARCHITECTURE.md`
- Reviews: `review-architecture-scope`, `review-backend-change`, `review-frontend-change`, `review-security-privacy`, `review-test-coverage`
- Android/Kotlin/Compose: use the installed Android and Chris Banes skills for AGP, Compose, state/effects, animation, performance, Flow, accessibility/focus, and testing.

Use official primary documentation for current provider/framework/version behavior. If a current API or capability cannot be verified, document it as unknown and do not implement around an assumption.
