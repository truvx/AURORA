---
name: aurora-testing
description: Plan or review AURORA behavioral tests across repositories, providers, playback, settings, AI, recommendations, UI, accessibility, and persistence.
---

# AURORA testing

Use for test strategy, new behavior, regressions, or verification. Read `docs/TESTING.md`, then use focused TDD and platform-specific testing skills.

## Test behavior at owners

- Domain: quality resolution, normalization configuration, player reducer/state machine, queue, shuffle, repeat, ranking, tool-call validation, and settings.
- Data/provider: mapping, capability discovery, retry classification, local metadata, provider policy/error boundaries, and persistence.
- Infrastructure: Media3/media-session lifecycle, audio focus/interruption, secure storage adapters, file permissions, haptics fallback, and browser storage.
- UI: semantics, state rendering, navigation, focus, touch targets, loading/empty/error/recovery, large text, contrast, reduced motion, and responsive layout.
- Integration: canonical state across mini-player/full player/queue/notification; provider-to-player handoff; AI-to-provider-to-queue flow.

Prefer deterministic fakes at boundaries and contract tests for adapters. Test outcomes and state transitions rather than private implementation details. Include negative cases and cancellation.

## Required behavior coverage

Repositories, adapters, player state, queue reorder, shuffle/repeat, settings, quality and normalization, AI parsing/tool authorization, ranking, auth state, haptic fallback, navigation, persistence, local library scans/imports, downloads where permitted, UI semantics, and accessibility must each have a focused owner test before the feature is considered complete.

Do not inflate coverage with tests that only execute lines or snapshot unstable animation pixels.
