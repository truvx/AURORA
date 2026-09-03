# ADR-009: Shared concepts with platform-native playback/UI adapters

## Status

Superseded by ADR-010; retained as the earlier design-phase record.

## Context

Android and web need aligned information architecture and domain behavior, but playback, storage, navigation, accessibility APIs, and rendering conventions are platform-specific.

## Decision

Share provider-neutral domain concepts, serialized contracts, semantic design tokens, and deterministic fixtures where practical. Keep Android Compose/Media3/URI/haptics and web React/browser media/IndexedDB behavior behind separate adapters and composition roots.

## Alternatives considered

- Force identical UI and platform APIs — rejected because native interaction/accessibility and playback constraints differ.
- Duplicate all business logic — rejected because queue/provider/quality/security behavior would drift.
- Choose one platform as primary — rejected because it makes the other a second-class client.

## Consequences

Cross-platform contract discipline and adapter tests are required. Some presentation code is intentionally platform-specific while semantics remain aligned.

## Supersession

ADR-010 makes the final v1 choice: separate Android and Web implementations with shared contracts, schemas, design-token definitions, and deterministic fixtures. Do not use this historical record to introduce executable KMP sharing without a new ADR.
