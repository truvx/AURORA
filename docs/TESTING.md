# AURORA testing strategy

Status: final v1 testing boundary. See `IMPLEMENTATION_PLAN.md` for phase-specific verification and `TOOLCHAIN_MATRIX.md` for the build matrix.

## Test ownership

Domain tests cover repositories’ contracts, player reducer/state machine, queue/shuffle/repeat, settings, quality/normalization resolution, AI intent/tool validation, and deterministic ranking. Adapter/integration tests cover provider mapping/capabilities/errors, local metadata, persistence, Media3/media session lifecycle, audio focus/interruption, secure storage, haptics fallback, browser storage, and provider-to-player handoff.

UI tests cover semantics, state rendering, navigation, focus, touch targets, loading/empty/error/recovery, large text, contrast, reduced motion/transparency, and responsive layout. Cross-surface tests verify the mini-player/full player/queue/notification reflect one canonical state.

## Method

Prefer deterministic fakes at boundaries and contract tests for adapters. Assert observable behavior and state transitions, including cancellation, retries, unavailable capability, auth failure, duplicate events, and persistence recovery. Use screenshot/runtime/browser tests only for material visual/interaction behavior; do not make unstable animation pixels the primary contract.

## Gate

Every behavior change needs focused tests before broad checks. Run platform-native tests/builds and review regressions. Line coverage is a signal, not the acceptance criterion.
