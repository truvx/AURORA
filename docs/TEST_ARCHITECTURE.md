# AURORA test architecture

## Test pyramid and ownership

### Pure domain/unit tests

Use fast deterministic tests for value objects, provider capability resolution, requested/actual quality, normalization math/configuration, queue operations, shuffle/repeat, player state transitions, recommendation eligibility/ranking/diversity, AI intent parsing, tool schema validation, authorization policy, settings defaults/migrations, and error classification.

These tests should not use Android, browser, Media3, a database, network, real AI calls, or wall-clock sleeps. Use seeded fixtures and explicit clocks/random sources where needed.

### Repository/data tests

Test mapping between provider DTOs and domain models, serialization/versioning, database queries, migrations, playlist ordering, favorite/history/resume persistence, library scan idempotency, URI/file identity, artwork palette cache invalidation, and eviction. Use an in-memory or test database when query/migration behavior is the subject; do not mock the database for SQL/mapping risks.

### Provider contract tests

Run the same contract suite against YouTube and local adapters using fakes/recorded safe fixtures where permitted. Verify declared capabilities, unsupported operations, provenance, pagination, typed errors, retryability, cancellation, metadata unknowns, policy blocks, and no raw arbitrary media URL escape. Live provider calls require explicit test configuration and must not be a default test dependency.

### Player/platform integration tests

Test Media3/media-session mapping, notification/lock-screen controls, audio focus, interruptions, becoming-noisy/headphone, Bluetooth route changes, background/foreground, browser Media Session/autoplay/visibility, engine callbacks, seek behavior, buffering, and canonical state projection. Use a fake engine for most state tests and a real platform adapter only for integration behavior.

### AI tests

Use fixture responses for Gemini/OpenAI adapter decoding, malformed/unknown tool calls, argument bounds, ambiguous entity references, dangerous/arbitrary URL attempts, privacy-filtered context, confirmation policy, provider capability mismatch, rate-limit/offline fallback, cancellation, and queue handoff. Never send real secrets or private history in tests.

### UI/accessibility tests

Test component semantics and behavior rather than implementation structure: accessible names/roles/values, focus order/restore, keyboard controls, touch targets, screen-reader state changes, loading/empty/error/offline/recovery, dynamic type/zoom, light/dark/dynamic artwork themes, contrast-protection fallback, reduced motion/transparency, unavailable capability copy, and non-drag queue reorder. Use visual/screenshot tests only for stable, material visual contracts.

### Performance tests

Benchmark startup, 500-item lists, large artwork decode/cache, palette extraction, library scans, metadata extraction, queue reorder, scrubber updates, database pagination, search, AI result rendering, and playback command response. Run representative low-end Android and mobile web scenarios. Store device/browser, data-set, baseline, and metric with results.

## Mocking rules

Mock at unstable external boundaries: network transport, provider SDKs, AI services, clock, random seed, file picker, secure storage, haptic engine, platform player, and browser APIs. Do not mock the policy/reducer/ranker under test. Prefer fakes that obey typed interfaces and contract suites over broad mocking frameworks.

## Integration data

Fixtures must use synthetic or openly licensed metadata/artwork/audio where needed. Keep tokens, personal listening history, local file paths, private URLs, and provider credentials out of fixtures, snapshots, logs, and recordings.

## Completion gate

An implementation is not complete on line coverage alone. It needs owner tests for changed behavior, contract tests for changed boundaries, accessibility tests for changed controls, relevant build/lint/type checks, and performance evidence for hot-path changes. Use `aurora-testing`, focused TDD, Compose state/effects/testing skills, and platform test guidance as applicable.
