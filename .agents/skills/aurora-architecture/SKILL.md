---
name: aurora-architecture
description: Define or review AURORA's cross-platform architecture, module boundaries, domain contracts, persistence, provider abstractions, and dependency direction.
---

# AURORA architecture

Use this skill for any feature that crosses presentation, domain, data, infrastructure, Android, web, playback, AI, or persistence boundaries. Read `docs/ARCHITECTURE.md` and the closest domain document before changing a contract.

## Non-negotiable shape

Keep dependencies flowing inward:

```text
Presentation -> Application / ViewModel -> Domain -> Data -> Infrastructure / Platform
```

- Presentation owns rendering, navigation, accessibility semantics, and user-intent forwarding. It does not call provider SDKs, Media3, databases, or AI clients directly.
- Application/ViewModel owns screen-state orchestration, use-case invocation, serialization of commands where needed, and one-shot effect mapping. It does not map provider DTOs or own platform engines.
- Domain owns provider-neutral music models, use cases, player commands/state contracts, settings policies, recommendation ranking, and validation.
- Data owns repositories, mappers, persistence, and provider adapters. Provider-specific DTOs never leak into domain or UI.
- Infrastructure/platform owns Media3, Android media sessions, file pickers, secure storage, browser storage, network clients, haptics, and platform lifecycle adapters.
- A platform adapter may implement a domain interface; a domain class must not import Android, Compose, browser, YouTube, Gemini, or OpenAI types.

## Required boundaries

- Use repository interfaces at the domain boundary and dependency injection at composition roots.
- Keep separate interfaces for `MusicProvider`, `PlayerEngine`, `AiProvider`, `SettingsStore`, `LibraryStore`, and `SecureTokenStore`.
- Use capability objects and sealed results rather than provider-name conditionals in shared UI.
- Model immutable screen state and one-shot effects separately; expose read-only state to UI.
- Keep one owner for each mutable state value. Derive display state instead of duplicating it.
- Keep YouTube, local files, and future providers behind adapters. Never make the entire player a YouTube implementation.
- Keep observability at boundaries: command/result, provider failure category, queue transition, persistence failure, and playback lifecycle. Never log tokens, raw URLs, or private prompts.

## Review gates

Reject changes that introduce a composable-to-SDK call, a UI-to-database call, a provider DTO in a shared model, a second playback owner, an arbitrary URL from AI, or an untyped `Map<String, Any>` crossing a module boundary. Ask whether a dependency can be inverted or a contract narrowed before adding a new module.

## Example contract

```kotlin
interface MusicProvider {
    val id: ProviderId
    val capabilities: ProviderCapabilities
    suspend fun search(query: MusicQuery): ProviderResult<Paged<TrackSummary>>
}
```

The interface expresses what a provider can do; it does not promise that every provider supports every operation. See `docs/MUSIC_PROVIDER_ARCHITECTURE.md`.
