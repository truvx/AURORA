# ADR-010: Cross-Platform Strategy

- Status: Accepted
- Date: 2026-09-03
- Supersedes: ADR-009-CROSS-PLATFORM.md

## Decision

AURORA will use separate platform implementations:

- Android: Kotlin, Jetpack Compose, AndroidX Media3, Android platform storage and secure-storage adapters.
- Web: TypeScript, React, Next.js App Router, browser media APIs, IndexedDB, Web Crypto, and browser session APIs.
- Shared: versioned provider-neutral contracts, schemas, design-token definitions where useful, and cross-platform fixtures. Shared executable Android or Kotlin/JS runtime code is not part of v1.

The dependency direction remains `Presentation -> Domain -> Data -> Infrastructure/Platform`. Each platform has its own composition root. Platform playback, local-file access, persistence drivers, authentication, and browser/Android lifecycle code do not cross the boundary.

## Alternatives considered

| Option | Decision | Reason |
| --- | --- | --- |
| Kotlin Multiplatform | Rejected for v1 | It would add Kotlin/JS or Wasm build and debugging complexity while still requiring TypeScript/browser adapters, and would pull Android-shaped assumptions toward the web. |
| Separate Android and Web implementations | Chosen | It matches the native media and storage surfaces, keeps each platform idiomatic, and is easier to test and maintain in the current documentation-only repository. |
| A single web or server-rendered application | Rejected | It cannot provide Android-native local media, Media3, media sessions, or platform haptics. |

## Shared contract rules

1. Define provider-neutral concepts such as `Track`, `Artist`, `Album`, `Playlist`, `QueueState`, `PlayerState`, `RequestedQuality`, `ActualQuality`, and `AIProvider` in language-neutral contract documentation and schemas.
2. Version serialized contracts. Add fields compatibly; do not silently change the meaning of existing fields.
3. Generate or hand-maintain platform types only from the agreed contract; provider DTOs and platform types must not leak into shared UI contracts.
4. Keep behavioral parity through deterministic fixtures and contract tests, not by forcing a shared runtime.
5. Share semantic design-token names and values where they improve parity; platform rendering may adapt blur, material, typography metrics, and touch behavior.

## Consequences

This decision accepts some duplication in small pure-domain policies. The canonical behavior is specified once and tested with shared fixtures. In return, Android can use Media3 and Android lifecycle primitives, while Web can use browser media and IndexedDB without an artificial abstraction. A future change to executable sharing requires a new ADR backed by measured build and test evidence.

## Assumptions and confidence

Assumptions: v1 values native platform behavior and two independently shipped clients more than maximum code reuse; shared contracts and fixtures can be maintained without a generated runtime package. Confidence: high, because the decision follows the existing repository boundaries and avoids coupling platform-only playback/storage concerns.

## Source basis

- [Android app architecture](https://developer.android.com/topic/architecture)
- [Jetpack Compose setup](https://developer.android.com/develop/ui/compose/setup)
- [Next.js installation and system requirements](https://nextjs.org/docs/app/getting-started/installation)
- [MDN Media Session API](https://developer.mozilla.org/en-US/docs/Web/API/Media_Session_API)
