# AURORA agent instructions

AURORA is released under the **GNU General Public License v3.0** (`LICENSE`). Anything
added to it must be compatible with that, and anything derived from another GPL project
keeps its attribution - see "Reuse before rewriting" below and `docs/THIRD_PARTY.md`.

AURORA is a personal-use music player for Android and Web. It combines high-quality playback of user-owned local files with legitimate YouTube discovery and visible foreground playback where permitted, a shared queue/player model, an interface that follows Apple's Liquid Glass, restrained motion and meaningful haptics, accessible cross-platform controls, and optional Gemini/OpenAI music assistance.

## Current repository phase

Both clients are built and running. `android/` is a Kotlin/Compose app with Media3 playback, a Room library, the liquid-glass interface, YouTube discovery and the AI screen; it produces a signed beta APK. `web/` is a Next.js App Router client with the same domain model over IndexedDB, plus the AI gateway both clients call. Phases 0-17 of the plan are implemented; 18-21 (accessibility hardening, performance, testing, CI/release) are partly done and are where most open work sits.

So: write real code. What is still forbidden is what was always forbidden because it is wrong, not because of the phase - fake playback, fake authentication, fake quality figures, invented APIs, YouTube extraction or downloading, hidden or background YouTube players, and DRM circumvention. Placeholder screens and stub implementations that pretend to work also stay out.

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

Never make large speculative rewrites.

Commit once verification passes, as `CLAUDE.md` requires: build, lint, typecheck and the relevant tests green, with evidence. Do not commit work that has not been verified, and do not push or open a pull request unless asked.

## Reuse before rewriting

If working code already exists that does the job, port it. Do not rewrite from scratch
what can be taken, adapted and credited. A rewrite from a screenshot is a guess at
someone's decisions; their source is the decisions themselves.

This is the first thing to check, not the last resort: before building any mechanism,
look for an existing implementation - in this repository, in a dependency already present,
in the platform, or in a compatibly licensed project.

The one gate is the licence, and it is absolute. Code may be taken only when its licence
permits it and AURORA complies with that licence. AURORA is GPL-3.0, so GPL-2.0-or-later,
GPL-3.0, LGPL, MIT, BSD and Apache-2.0 sources are all available to it. Proprietary code,
code with no licence at all, and code under a licence AURORA cannot satisfy are not
available at any speed advantage. "It was faster" is not a defence to a licence violation.

Ported code keeps its attribution: name the project, the file and the licence in a comment
at the top of anything derived from it, and record the dependency in `docs/THIRD_PARTY.md`.

## Architecture boundaries

Keep dependency direction:

```text
Presentation -> Application/state holders -> Domain -> Data -> Infrastructure/Platform
```

Presentation renders immutable state and forwards typed intents. Domain owns provider-neutral models, use cases, policies, and state machines. Data owns repositories, mappers, persistence, provider adapters, and bounded caches. Infrastructure/Platform owns Media3, browser media/Media Session, storage, secure storage, network, auth, haptics, and lifecycle adapters.

Android and Web are separate implementations with separate composition roots:

- Android: Kotlin, Jetpack Compose, AndroidX Media3, Room/SQLite, Android secure storage, Android lifecycle/media-session/haptic adapters.
- Web: TypeScript, React 19.3.0, Next.js 16.3.4 App Router, browser media/Media Session, IndexedDB, Web secure session handling, and browser lifecycle adapters.
- Shared: versioned provider-neutral contracts/schemas, domain concepts, design-token definitions where useful, and deterministic fixtures. Kotlin Multiplatform executable sharing is not part of v1.

Android toolchain baseline: `compileSdk 37`, `targetSdk 36`, `minSdk 26`, AGP `9.4.0`, Gradle Wrapper `9.6.0`, JDK 17, Kotlin `2.4.10`, Compose `1.12.0`, and Media3 `1.11.0`. Compose 1.12.0 requires compile SDK 37. Keep target SDK 36 until a separate Android 17 behavior and release review approves raising it; compile SDK and target SDK are independent. Web project/CI uses Node 24 LTS, React 19.3.0, Next.js 16.3.4, and TypeScript 5.16.1; retain the local Node 26 installation unless a documented requirement changes. See `docs/ADR-016-VERSION-MATRIX.md` and `docs/TOOLCHAIN_MATRIX.md`.

Do not force Android playback, URI, storage, haptic, or lifecycle abstractions into Web. Provider DTOs, SDK types, and platform types must not leak into shared domain/UI contracts.

## Music providers and YouTube

Initial providers are exactly:

1. YouTube
2. Local/user-owned music

Do not add Spotify or Apple Music in v1. MusicBrainz, the Cover Art Archive and LRCLIB are not providers and do not change that list: nothing is played, browsed or recommended from them. They answer one question each - what record is this, what does its sleeve look like, and what are its words - because a YouTube thumbnail is a still from a video and a video title is not a song. Public APIs, no keys, and cached; a lookup that fails leaves the video's own title and thumbnail in place and shows no lyric rather than a guessed one. `MusicProvider` is capability-based; the UI and AI executor query capability snapshots before rendering or executing actions.

YouTube discovery/metadata use bounded public YouTube Data API v3 requests. Playback uses a visible official YouTube IFrame Player API player on Web and a visible Android WebView surface. Preserve attribution, branding, required origin/client identity, and current policy behavior. The player's control bar may be off (the documented `controls=0` parameter) where AURORA's own transport drives the player through the IFrame API; the title, channel avatar and YouTube logo are the player's to show. Autoplay is opt-in and may be blocked.

Never scrape YouTube, extract audiovisual streams, separate audio, invent stream URLs, bypass DRM or geographic restrictions, download/cache YouTube media bytes, provide offline playback, create a hidden/background/screen-off player, block ads, modify the player, or scrape credentials/cookies/tokens. YouTube quality is provider-determined/unknown to AURORA; do not claim bitrate, codec, lossless, or Hi-Res. Do not infer lyrics from captions or descriptions; lyrics come from a lyrics database, matched on the resolved identity and on duration, or they are not shown. The complete contract is `docs/YOUTUBE_CAPABILITY_MATRIX.md`.

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

Never commit, log, or expose secrets, tokens, cookies, credentials, private URLs, or authorization headers, and never hardcode them in source. Keys a build needs are read from `local.properties` and injected, so they reach the APK without reaching the repository. Bundling a credential in the APK at all is a decision with a cost, allowed only where that cost is bounded and stated: the YouTube Data API key is bundled, because it reads public data and spends one project's quota; the AI gateway token is not, because it authorizes spending. `docs/SECURITY.md` records the reasoning and the restrictions that go with it. Never read another app's private storage, database, cookies, tokens, or credentials. Validate all external/user/provider/file/AI data at boundaries. Use supported authentication with state, PKCE where applicable, exact redirects, scopes, issuer/audience/expiry checks, secure storage, refresh/revocation, logout, timeouts, rate limits, and typed failures. Listening history is private by default, minimized, configurable, exportable, and deletable.

## Design, motion, haptics, accessibility

AURORA's interface follows Apple's Liquid Glass as closely as it can. A visual language is not something a project can be forbidden from pursuing, and this one is the target: the material, the optics, the shapes and the motion. What stays out is Apple's property rather than Apple's aesthetic - do not ship SF Pro or SF Symbols, which are licensed for Apple platforms only, do not copy Apple source or image assets, and do not use their branding. Space Mono and Material Symbols stand in for the typefaces. It uses stable canvas, optional artwork atmosphere, contrast scrim, semantic glass, soft highlight, and readable content layers. Blur/transparency/dynamic theming are adaptive enhancements with opaque and reduced-transparency fallbacks.

Use Source Sans Pro (Adobe, SIL OFL 1.1) with the system sans fallback on Android - the web client still uses Space Mono until it is switched - and Material Symbols Outlined with Lucide fallback only where explicitly needed. One family for the whole interface: hierarchy comes from size, weight and colour rather than from a second typeface. Two weights ship: Bold for headings, small-caps section labels and album titles; Regular for everything else, including a song's name and whoever made it. Nothing asks for a weight the family does not ship - a synthesised one is a smeared Regular, so a role that wants a 500 takes 400. Tracking stays near zero, opening only on small capitals. Numeric roles switch on tabular figures (`tnum`), which is what a running time needs. Do not use emoji as UI icons. Keep readable type, reserved layout dimensions, semantic colors, sufficient contrast after artwork, and no color-only status.

Use named duration/easing/spring tokens, transform/opacity-first animation, interruption/reversal, lifecycle cancellation, bounded gesture velocity, and reduced-motion variants. `HapticEngine` is centralized, capability-aware, sparse, rate-limited, and never vibrates per scroll or scrub frame. Visual and semantic feedback must work without haptics.

Support Android text scaling, Web zoom, screen readers, semantic labels, keyboard/focus navigation, minimum 48 dp Android and 44 CSS px Web targets, sufficient contrast, reduced motion, reduced transparency, opaque glass fallback, non-color status, and accessible queue/scrub alternatives. See `docs/ACCESSIBILITY_SPEC.md`.

## Testing and performance

Test behavior at the owning layer: domain states/policies/queue/ranking/tool validation, repositories/migrations, provider capabilities, Media3/browser lifecycle, secure storage, AI gateway contracts, haptics, UI semantics/accessibility, and cross-surface canonical playback. Use deterministic fakes and real-boundary integration tests. Cover offline, unknown, unsupported, permission/auth, quota, policy, retry, stale callback, migration, deletion, and cancellation paths. Screenshots supplement behavior/semantics; line coverage is not proof.

Protect a 16.7 ms frame budget, keep I/O off the UI thread, isolate position ticks, lazy-load and dimension artwork, bound cache/concurrency/memory, page large queries, and measure startup, buffering, and AI latency. Do not sacrifice truthfulness, security, or accessibility for visual or performance shortcuts.

## Implementation sequence

The plan's order was for building the thing from nothing, and phases 0-17 are done. New work is no longer drawn from it in sequence; it comes from the open task list and from what the app actually needs. `docs/IMPLEMENTATION_PLAN.md` still governs anything not yet built, and its phases 18-21 - accessibility hardening, performance, testing, CI/release - remain live:

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
- Android tooling: `android-cli` for the SDK, emulators, adb and Gradle from the terminal; `android-profiler` for frame timing and startup; `edge-to-edge` for insets and system bars
- Web/React: `vercel-react-best-practices`, `vercel-react-view-transitions`, `web-design-guidelines`
- Working practice: `focused-tdd`, `bug-triage`, `ci-fix`, `dependency-upgrader`, `docs-sync`, `testing-setup`, `release-notes`

Skills live in `.claude/skills/`; that directory is the list, not this one. If a skill named here is missing, say so rather than guessing at what it would have said.

Use official primary documentation for current provider/framework/version behavior. If a current API or capability cannot be verified, document it as unknown and do not implement around an assumption.
