# AURORA architecture

Status: final cross-cutting boundary. The canonical platform decision is `ADR-010-CROSS-PLATFORM-STRATEGY.md`.

## Boundary model

```text
Presentation (Compose / React)
        ↓ intents + immutable state
Application / ViewModel / state holders
        ↓ use cases + one-shot effects
Domain (models, use cases, policies, state machines)
        ↓ interfaces
Data (repositories, adapters, mappers, persistence)
        ↓ platform implementations
Infrastructure / Platform (Media3, browser APIs, auth, storage, network, haptics)
```

Presentation never imports provider SDKs, Media3, database drivers, or AI clients. Application/ViewModel coordinates state and effects but does not map provider DTOs or own platform engines. Domain contains no Android, Compose, browser, YouTube, Gemini, or OpenAI types. Data maps external DTOs into domain models. Infrastructure is selected at the composition root through dependency injection.

## Core interfaces

`MusicProvider`, `PlayerEngine`, `AIProvider`, `SettingsStore`, `LibraryStore`, `HistoryStore`, and `SecureTokenStore` are narrow interfaces. Capability sets and typed result/error models prevent provider assumptions from spreading. Repositories are domain-facing; implementations remain in data.

## State and flow

Use unidirectional data flow: UI event → application/ViewModel state holder → domain use case → repository/engine → canonical immutable state/effect. A state value has one owner. Screen state is derived from canonical sources rather than copied into multiple screens. One-shot effects are not durable state.

## Cross-platform strategy

Share versioned provider-neutral contracts, schemas, metadata semantics, design tokens, and deterministic test fixtures where useful. Keep Android Media3/media-session behavior and Web browser media/Media Session/IndexedDB behavior behind separate adapters. Do not force platform-specific playback code into the shared layer; executable KMP sharing is not part of v1.

## Observability

Record structured, privacy-minimized categories for provider requests/results, command transitions, queue changes, playback lifecycle, persistence failures, and AI tool validation. Redact tokens, authorization headers, raw media URLs, private prompts, and full listening histories.

## Architecture review checklist

Check dependency direction, ownership, cancellation, error typing, capability queries, provider isolation, secure storage, test seams, lifecycle behavior, performance, and whether the proposed abstraction removes or adds complexity. The detailed decision and module contract live in `ADR-001-CORE-ARCHITECTURE.md` and `MODULE_ARCHITECTURE.md`.
