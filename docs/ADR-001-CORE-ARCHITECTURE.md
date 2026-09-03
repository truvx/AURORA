# ADR-001: Core architecture

## Status

Accepted as foundation; clarified by ADR-010 and FINAL_ARCHITECTURE_BASELINE.md.

## Context

AURORA must serve Android and web while keeping local music, legitimate YouTube discovery/playback, a centralized player, recommendations, settings, AI providers, and a premium UI coherent. The system must remain usable without AI or network access, must not couple itself to prohibited media extraction, and must keep platform SDKs out of shared business contracts.

## Decision

Use a layered, dependency-inverted architecture with five conceptual levels:

```text
Presentation
    ↓ intents / immutable state / effects
Application + ViewModel / state holders
    ↓ use-case invocation
Domain
    ↓ repository and engine interfaces
Data
    ↓ adapters
Infrastructure / Platform
```

The shared domain is the source of truth for provider-neutral models, capabilities, policies, player state transitions, queue rules, quality/normalization resolution, recommendation ranking, and AI tool authorization. Android and web each compose platform implementations at their application boundary.

## Boundaries

### Presentation

Compose and React render state, emit user intents, own only ephemeral visual state, expose accessibility semantics, and select navigation destinations. They do not call Media3, browser media APIs, databases, provider SDKs, or AI clients.

### Application / ViewModel

Application coordinators translate screen intents into domain use cases, combine canonical flows into screen state, serialize user commands where required, and publish one-shot effects. They do not contain provider DTO mapping or platform-specific playback code.

### Domain

Domain owns stable entities and value objects (`Track`, `Album`, `Artist`, `Playlist`, `ProviderId`, `ProviderCapabilities`, `PlayerState`, `QueueState`, `AudioQuality`, `NormalizationMode`, recommendation inputs, and typed errors). It owns policies and interfaces, but imports no Android, web, vendor, or UI types.

### Data

Data implements repositories and provider adapters, maps external DTOs into domain models, coordinates cache/persistence, and classifies errors. It may depend on domain contracts and infrastructure interfaces, not on composables or React.

### Infrastructure / Platform

Infrastructure implements Media3, Android media session, secure storage, file/URI access, haptics, browser media/session, IndexedDB, network transport, and supported auth mechanisms. All platform behavior is adapted behind domain-facing contracts.

## Dependency direction

Dependencies point inward. A feature may depend on a narrower interface from an inner layer; an inner layer never imports an outer layer. Cross-feature calls go through application use cases or explicit domain contracts. No random cross-module imports, static global player instances, or provider-specific conditionals in shared UI.

## State flow

```text
user/system event
    → presentation intent
    → application coordinator/use case
    → domain policy or interface
    → repository/engine adapter
    → canonical state update
    → derived screen state
```

State is immutable at boundaries and has one owner. Durable state, current player state, and one-shot effects are separate. UI never assumes that requesting play means playback succeeded; it observes confirmed engine state.

## Event flow

Commands are typed and serialized by their owner. Platform callbacks (media session, audio focus, headset/Bluetooth, visibility, network, import completion) enter through adapters and become domain events. Events are idempotent where practical and include source identity so stale provider/player callbacks cannot overwrite newer state.

## Persistence

Use a local persistence abstraction for library metadata, playlists, favorites, listening history, resume positions, settings, provider account metadata, and download/import records. Persist domain records through repositories; never let UI or provider DTOs write directly. Local media bytes remain user-owned files and are referenced by stable URI/file identity. Secure tokens use a separate secure-storage abstraction and are never stored with ordinary app preferences.

## Networking

Network clients are provider- or service-specific adapters behind typed interfaces. Responses are validated and mapped at the boundary. Retries are bounded, cancellation-aware, and limited to safe/idempotent operations. No undocumented YouTube extraction endpoint, arbitrary AI URL, or hardcoded secret is part of the design.

## Media playback

One process-level playback coordinator owns one platform engine per client. Android may use Media3/ExoPlayer for sources the platform/provider legitimately exposes; web uses browser media APIs. The mini-player, now-playing, queue, notification, lock screen, and external controls consume the same canonical `PlayerState` and `QueueState`.

## AI integration

Gemini and OpenAI implement `AIProvider`. They produce validated intent/tool-call envelopes. The application validates tool name, schema, limits, authorization, provider capability, confirmation rules, and resolved track identity before execution. The model never controls arbitrary application state or invents media URLs. AI is optional; deterministic search/recommendation paths remain available.

## Provider abstraction

Providers implement small capability interfaces and expose a capability set. YouTube and local files are separate adapters. UI asks capabilities rather than testing provider names. Unsupported download, offline, lyrics, quality, or background behavior is represented explicitly.

## Testing boundaries

Domain reducers, policies, ranking, and validators use pure unit tests. Repository and adapter behavior uses contract tests and deterministic fakes. Media session, secure storage, file access, haptics, browser storage, and lifecycle use platform integration tests. UI uses semantics/interaction tests. Cross-surface tests verify canonical player state.

## Security boundaries

Secrets and tokens cross only through supported auth and secure storage. Provider and AI inputs are untrusted. Tool execution is allowlisted and authorized. Logs are privacy-minimized. Local-library history is local by default. TLS validation, DRM, platform permissions, and provider policy are never weakened for convenience.

## Alternatives considered

### Shared UI-only monorepo with duplicated business logic

Rejected: it makes queue, provider capability, quality, and security behavior drift between Android and web.

### Platform-first architecture with Android as the source of truth

Rejected: Android playback APIs would leak into web and make provider/domain behavior difficult to test.

### Microservices from the start

Rejected: A single-user application does not justify distributed operational complexity in the foundation phase. Server boundaries may be introduced later for supported auth/AI proxy needs without changing domain contracts.

### One giant `MusicProvider` interface

Rejected: it forces every provider to fake support for lyrics, downloads, quality, or recommendations. Capability interfaces preserve truthful behavior.

## Consequences

Positive: clear ownership, testable policies, truthful provider capabilities, one player state, platform flexibility, and safer AI/media boundaries.

Costs: more interfaces and mapping code; composition roots must be carefully maintained; some cross-platform code sharing requires deliberate contract design rather than direct class reuse.

## Future migration considerations

New providers, sync, or a backend may implement existing interfaces. A change to canonical models requires versioned serialization/migration and coordinated Android/web contract review. Replacing Media3 or browser adapters should not require presentation or domain changes.
