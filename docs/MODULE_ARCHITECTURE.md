# AURORA module architecture

Status: final conceptual target. Android and Web have separate presentation/application/domain/data implementations; only contracts, fixtures, and token definitions are shared.

## Repository shape

```text
aurora/
├── android/
│   ├── app/                 # Compose presentation, application state, and composition root
│   ├── domain/              # Kotlin provider-neutral policies and state machines
│   ├── data/                # Room, local files, providers, AI gateway, recommendations
│   └── infrastructure/     # Media3, Android storage/auth/session/haptics/lifecycle
├── web/
│   ├── app/                 # React/Next presentation, application state, and composition root
│   ├── domain/              # TypeScript provider-neutral policies and state machines
│   ├── data/                # IndexedDB, providers, AI gateway, recommendations
│   └── infrastructure/     # Browser media/storage/auth/session/lifecycle
├── shared/
│   ├── contracts/           # Versioned serialized boundaries and capability schemas
│   ├── test-fixtures/       # Cross-platform deterministic fixtures, no production secrets
│   └── design-tokens/       # Semantic token names/values where cross-platform parity helps
├── server/
│   └── ai-gateway/          # Provider secrets, Gemini/OpenAI adapters, validation, quotas
└── docs/                    # Product, architecture, design, ADRs, and validation records
```

This is a conceptual target, not an instruction to create these directories or files during the design phase.

## Module responsibilities and dependencies

| Module | Responsibility | May depend on | Must not depend on |
|---|---|---|---|
| `android/domain` and `web/domain` | Platform-local provider-neutral entities, policies, capability interfaces, player/recommendation state machines | standard language/runtime primitives and shared contract fixtures | UI, SDKs, database, network, provider DTOs |
| `android/data` and `web/data` | Repository, provider, library, AI-gateway, recommendation, and persistence implementations | local domain, shared contracts, platform ports | presentation, direct global-player mutation |
| `android/infrastructure` | Media3, Android navigation/composition, lifecycle, media session, secure storage, haptics | approved interfaces and Android APIs | duplicated domain rules, provider extraction |
| `web/infrastructure` | React/Next composition, browser media/session, IndexedDB, web auth | approved interfaces and Web APIs | Android/Media3 imports, server secrets in bundles |
| `shared/contracts` | Versioned DTO/serialization boundaries and schema rules | contract primitives | platform UI, secret values, raw SDK objects |
| `shared/test-fixtures` | Cross-platform deterministic inputs/expected outputs | contract definitions | production secrets and platform objects |
| `shared/design-tokens` | Token names, semantic values, accessibility/motion specs | no runtime application modules | business logic, provider state, raw SDKs |
| `server/ai-gateway` | Provider secrets, Gemini/OpenAI adapters, validation, redaction, quotas | provider APIs and gateway contracts | client secrets, direct arbitrary app mutation |

## Public interfaces

Public cross-module interfaces should be small, typed, documented, and versioned when serialized. Expected boundaries include `MusicProvider`, capability interfaces, `LibraryRepository`, `HistoryRepository`, `PlaylistRepository`, `PlayerEngine`, `SettingsStore`, `AIProvider`, `AICommandAuthorizer`, `SecureTokenStore`, `HapticEngine`, and `ArtworkThemeResolver`.

## Serialization boundaries

Serialize only stable provider-neutral DTOs. Include schema/version, stable IDs, source/provider identity, optionality, and provenance. Never serialize platform handles, live player objects, access tokens, raw cookies, unvalidated URLs, or large audio blobs into shared state. Map unknown fields to explicit unknown values rather than defaults that look factual.

## Platform-specific implementation rules

Android owns Media3, notification/media session, audio focus, content URI permissions, Android secure storage, haptic primitives, and lifecycle integration. Web owns browser media elements, Media Session, autoplay/visibility policy, IndexedDB, Web Crypto/storage choices, and browser accessibility details. Both implement the same intent/state contracts where practical.

## Prohibited dependencies

No composable or React component may import an SDK client. No domain module may import provider names to select behavior. No provider adapter may mutate a global player directly. No AI adapter may call a raw application command. No database module may know about screen state. Platform domain implementations must conform to the shared contract fixtures and may not silently diverge in ranking, quality, or normalization semantics.

## Composition roots

Each client constructs adapters, repositories, use cases, and coordinators at one explicit composition root. Dependency injection is explicit enough to support test replacement. Avoid service locators and hidden global mutable state.

## Failure and observability ports

Domain results carry typed, user-safe error categories and retryability. Data/infrastructure can add diagnostic context at a logging port, but must redact secrets, tokens, raw media URLs, private prompts, and full histories.

## Migration rule

Adding a module requires a written reason, an owning layer, allowed dependencies, test seam, and documentation update. If the module exists only to pass through one method or hide an unresolved contract, simplify the boundary instead.
