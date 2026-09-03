# AURORA Implementation Plan

Status: final ordered plan, 2026-09-03. This plan is documentation only; no phase below has been implemented by the foundation task.

## Delivery rules

- Preserve `Presentation -> Application -> Domain -> Data -> Infrastructure/Platform`.
- Deliver one small vertical increment at a time and keep a passing checkpoint after each phase.
- Define contracts and capability matrices before adapters or UI actions.
- Do not add Spotify/Apple Music, YouTube extraction, unauthorized download/offline behavior, fake playback/auth, fabricated quality, or invented APIs.
- Every phase must update tests and documentation for changed behavior.

## Phase 0 - Toolchain

- Purpose: Establish the reproducible Android, Web, and CI toolchain without creating an app project.
- Prerequisites: Final architecture and `TOOLCHAIN_MATRIX.md`.
- Affected modules: Machine environment only; no repository module.
- Relevant skills: `aurora-project-rules`, `aurora-architecture`, `source-driven-development`, `planning-and-task-breakdown`, Android `android-cli`, Android `agp-9-upgrade`.
- Expected files: `docs/TOOLCHAIN_MATRIX.md`, future CI version pins; no source/build files in this phase.
- Implementation sequence: Install/configure JDK 17 and Android command-line tools; verify the stable Android API 37 compile platform and AGP-required Build Tools 36.0.0; configure shell exports; record `compileSdk 37`, `targetSdk 36`, and `minSdk 26`; verify Java, javac, adb, and sdkmanager.
- Tests: Run `java -version`, `javac -version`, `adb version`, `sdkmanager --version`; confirm SDK package inventory.
- Acceptance criteria: JDK 17, API 37 compile tooling, and Build Tools 36.0.0 are usable; `targetSdk 36` and `minSdk 26` are explicit; no global Gradle/Kotlin CLI is required; local and CI versions are documented.
- Risks: IDE and build JDK mismatch, preview SDK drift, non-reproducible PATH.
- Rollback/failure considerations: Keep Android Studio bundled JBR separate; remove only newly installed toolchain components if installation is corrupted; do not delete existing user SDK content.

## Phase 1 - Repository/project skeleton

- Purpose: Create the smallest buildable Android, Web, contracts, and optional gateway skeleton with explicit module boundaries.
- Prerequisites: Phase 0; accepted ADRs 010-016.
- Affected modules: `android/`, `web/`, `shared/contracts/`, future `server/ai-gateway/`.
- Relevant skills: `aurora-architecture`, `api-and-interface-design`, `source-driven-development`, `git-workflow-and-versioning`, Android `agp-9-upgrade`.
- Expected files: Gradle settings/version catalog/wrapper, Android module manifests, Web package manifest/lockfile, contract schema index, environment templates without secrets, CI scaffolding.
- Implementation sequence: Create roots; add only pinned build plugins; declare dependency directions; add schema version; add compile/help checks; wire composition roots with no feature UI.
- Tests: Gradle `help` and dry-run; Web typecheck/lint; schema parse and contract fixture tests.
- Acceptance criteria: Both clients build empty framework shells; dependency graph points inward; no provider SDK type enters shared contracts; no secrets are committed.
- Risks: AGP 9 built-in Kotlin migration, dependency sprawl, accidental app behavior.
- Rollback/failure considerations: Revert only the new skeleton commit/change set; preserve documentation and toolchain; remove an unnecessary dependency before adding another.

## Phase 2 - Design system foundation

- Purpose: Implement semantic tokens, themes, typography, icons, glass materials, contrast protection, and fallbacks.
- Prerequisites: Phase 1; `AURORA_MASTER_DESIGN_SYSTEM.md`, `TYPOGRAPHY_FINAL.md`, `ICONOGRAPHY_FINAL.md`, `ACCESSIBILITY_SPEC.md`.
- Affected modules: Android design system, Web design system, shared token/schema fixtures.
- Relevant skills: `aurora-liquid-glass-ui`, `ui-ux-pro-max`, `frontend-ui-engineering`, `design-system`, `minimalist-ui`, `compose-component-design`, `compose-focus-navigation`, Android Compose theming/styles.
- Expected files: Android theme/tokens/components, Web token CSS/types/components, font/icon licensing records, contrast test fixtures.
- Implementation sequence: Add primitive tokens; map semantic tokens; implement light/dark themes; add Inter and Material Symbols delivery; implement glass/opaque/reduced-transparency variants; add focus and text-scale primitives.
- Tests: Token snapshot/contract tests; Android Compose semantics and contrast tests; Web keyboard/zoom/contrast checks; font fallback rendering tests.
- Acceptance criteria: Themes render without unreadable artwork text; opaque fallback preserves meaning; icon-only controls have labels; no proprietary assets are used.
- Risks: Blur cost, font loading layout shifts, one-note palette, platform visual drift.
- Rollback/failure considerations: Disable blur/dynamic theming behind the semantic fallback; retain token names and revert only implementation values that fail contrast or performance.

## Phase 3 - Application shell/navigation

- Purpose: Establish navigation, adaptive layouts, route state, top-level state holders, error/loading/empty states, and the shared player host location.
- Prerequisites: Phase 2; `NAVIGATION_ARCHITECTURE.md`, `SCREEN_MAP.md`, `COMPONENT_SYSTEM.md`.
- Affected modules: Android shell/navigation, Web routes/layouts, application state holders.
- Relevant skills: `aurora-web`, `ui-ux-pro-max`, `frontend-ui-engineering`, `compose-focus-navigation`, `compose-state-and-effects`, `chrisbanes-skills:compose-component-design`.
- Expected files: Android navigation graph/shell, Web App Router routes/layouts, route contracts, state-holder tests.
- Implementation sequence: Define destinations; wire adaptive navigation; mount one player coordinator host; add URL/deep-link and back handling; implement real loading/empty/error states with fixture data only in tests.
- Tests: Navigation/back/deep-link tests; Android/Web keyboard focus tests; window-size and text-scale layout tests; state restoration tests.
- Acceptance criteria: All routes are reachable, keyboard/focus order is coherent, no screen creates a player, and no fake user-facing data is shipped.
- Risks: Route state duplication, inaccessible sheets, responsive overflow.
- Rollback/failure considerations: Keep route contracts stable; disable an unfinished destination behind navigation config rather than adding placeholder content.

## Phase 4 - Local music library

- Purpose: Import and index user-owned local files with truthful metadata and privacy-preserving local behavior.
- Prerequisites: Phase 3; `DATABASE_MODEL.md`, `DATABASE_DECISION_MATRIX.md`, `AUDIO_QUALITY_POLICY.md`.
- Affected modules: Android file/URI adapter, Web file-handle adapter, repositories, Room/IndexedDB stores, metadata parser boundary.
- Relevant skills: `aurora-architecture`, `aurora-audio`, `aurora-security`, `aurora-web`, `security-and-hardening`, Android `testing-setup`.
- Expected files: Local file port/adapters, Room schema/DAOs/migrations, IndexedDB stores/repositories, metadata mappers, import state models.
- Implementation sequence: Request platform access; fingerprint/identify files; parse actual metadata; persist references and nullable facts; add rescan/revoke/delete behavior; expose library queries.
- Tests: Metadata fixtures for supported/unsupported files; migration tests; invalid URI/permission/error tests; duplicate identity tests; offline tests; privacy delete tests.
- Acceptance criteria: Imported files play only from user-owned references; metadata never fabricates quality; original bytes are untouched; rescan and removal are recoverable.
- Risks: Permission revocation, corrupt files, duplicate fingerprints, browser quota.
- Rollback/failure considerations: Keep import records and retry state; remove a failed index entry without deleting the source file; roll back migrations with a forward corrective migration.

## Phase 5 - Player engine

- Purpose: Implement the canonical playback coordinator and platform engine ports for local files.
- Prerequisites: Phase 4; `PLAYER_STATE_MACHINE.md`, `PLAYER_ARCHITECTURE.md`.
- Affected modules: Domain player state machine, application coordinator, Android Media3 adapter, Web media adapter, platform audio focus/session adapters.
- Relevant skills: `aurora-player`, `aurora-audio`, `aurora-architecture`, `aurora-web`, `compose-state-and-effects`, `kotlin-concurrency-and-flow`, `kotlin-control-flow`, `kotlin-api-design`.
- Expected files: Player contracts/state reducer, coordinator, Media3 adapter, HTML media adapter, capability projection, lifecycle/session adapters.
- Implementation sequence: Encode exhaustive states/events; add queue-independent engine port; implement local source loading; guard stale callbacks/cancellation; add focus/interruption/becoming-noisy; expose immutable StateFlow-like state.
- Tests: Exhaustive transition tests; duplicate/stale callback tests; seek/unknown duration tests; Media3/browser adapter contract tests; focus/interruption/cancellation tests.
- Acceptance criteria: One coordinator owns playback; local track play/pause/seek/recovery works; surfaces can subscribe to the same state; no fake active playback state is emitted.
- Risks: Callback ordering, lifecycle leaks, high-frequency position recomposition, platform engine differences.
- Rollback/failure considerations: Gate new engine adapters behind the port; keep a deterministic fake engine for tests; on adapter failure return `Error` and release resources rather than silently skipping.

## Phase 6 - Queue and Now Playing

- Purpose: Add queue traversal, mini-player, Now Playing, repeat/shuffle, scrubber, and external control projections.
- Prerequisites: Phase 5; final player state and design/motion contracts.
- Affected modules: Queue domain/application, Android/Web player surfaces, media-session/notification projections.
- Relevant skills: `aurora-player`, `aurora-motion`, `aurora-haptics`, `ui-ux-pro-max`, `compose-animations`, `compose-performance`, `compose-ui-testing-patterns`.
- Expected files: Queue policy, player projections, Now Playing/mini-player surfaces, Android media session/notification mapping, Web Media Session mapping.
- Implementation sequence: Implement stable IDs and traversal; add repeat/shuffle; add mini-player expansion continuity; add queue reorder; add scrubber with unknown-duration state; add external controls.
- Tests: Queue invariants, shuffle determinism, repeat modes, reorder-current preservation, scrub accessibility, cross-surface projection, notification/media-session contract tests.
- Acceptance criteria: Mini-player, Now Playing, queue, and external controls show one truth; 90% completion policy is applied without seek-only completion; controls reflect capabilities.
- Risks: Competing screen state, scrubber frame cost, focus/notification platform differences.
- Rollback/failure considerations: Disable a surface projection without creating a second coordinator; preserve queue snapshot and state contracts.

## Phase 7 - YouTube integration

- Purpose: Add compliant YouTube discovery, metadata, artwork, and visible foreground embedded playback.
- Prerequisites: Phase 6; `ADR-011-YOUTUBE-INTEGRATION.md`, `YOUTUBE_CAPABILITY_MATRIX.md`, current policy review.
- Affected modules: YouTube Data API gateway/client boundary, provider adapter, Web IFrame adapter, Android WebView adapter, attribution/policy UI.
- Relevant skills: `aurora-music-provider`, `aurora-web`, `aurora-security`, `api-and-interface-design`, `security-and-hardening`, `source-driven-development`.
- Expected files: Provider DTOs/mappers, capability snapshot, quota-aware search repository, visible player host, policy/error models, attribution components.
- Implementation sequence: Configure API project outside source; implement bounded public search/metadata; filter embeddability; map thumbnails; render visible IFrame player on Web/Android; wire provider callbacks to the canonical coordinator; add autoplay-blocked and policy errors.
- Tests: DTO/mapping tests; quota/debounce/cache expiry tests; embed attribution and referer/origin tests; blocked autoplay; visibility/background restriction; no-URL-extraction security tests.
- Acceptance criteria: Search result can play through official visible player; app queue advances by provider ID; no audio-only URL, download, offline cache, hidden player, or quality promise exists; YouTube actions are capability-gated.
- Risks: Policy/API changes, quota exhaustion, WebView behavior, embedded player availability.
- Rollback/failure considerations: Disable YouTube provider while retaining local playback; evict policy-invalid cache data; never substitute an extraction path.

## Phase 8 - Audio quality system

- Purpose: Implement truthful preferred-quality resolution and local/YouTube capability presentation.
- Prerequisites: Phases 4-7; `AUDIO_QUALITY_POLICY.md`.
- Affected modules: Domain quality policy, local metadata mapper, provider capability adapters, settings and track metadata UI.
- Relevant skills: `aurora-audio`, `aurora-music-provider`, `api-and-interface-design`, `ui-ux-pro-max`.
- Expected files: Requested/Provider/Track/Actual quality types, resolver, capability projections, quality settings/metadata surfaces.
- Implementation sequence: Add enum and provenance types; parse local facts; map YouTube provider-determined state; resolve Auto/manual preferences; add downgrade/unknown reasons; expose accessible settings and track details.
- Tests: Every preference against variants/lower variants/unknowns; local codec/lossless vectors; YouTube no-selection behavior; offline and policy-blocked cases; no fabricated labels.
- Acceptance criteria: `RequestedQuality + ProviderCapabilities + TrackCapabilities` resolves to a truthful actual state; Hi-Res/Lossless never appear without evidence.
- Risks: Confusing preference with result, provider capability drift, metadata parser errors.
- Rollback/failure considerations: Fall back to `Unknown`/provider-determined and preserve playback; do not roll back by changing a factual stored value.

## Phase 9 - Loudness normalization

- Purpose: Add local ReplayGain/offline analysis and bounded non-destructive runtime DSP.
- Prerequisites: Phases 4-5; `ADR-014-LOUDNESS-NORMALIZATION.md`, `LOUDNESS_POLICY_FINAL.md`.
- Affected modules: Audio analysis worker/cache, domain gain resolver, platform DSP adapter, settings/track diagnostics.
- Relevant skills: `aurora-audio`, `aurora-player`, `performance-optimization`, `observability-and-instrumentation`, `security-and-hardening`.
- Expected files: Loudness/peak models, analysis queue/cache, gain calculation, limiter policy, runtime processor port, diagnostics and tests.
- Implementation sequence: Read trusted ReplayGain; add offline LUFS/peak analysis; fingerprint/cache analysis; calculate bounded gain; apply true-peak protection and limiter only when needed; expose actual applied state; map YouTube to provider-managed/unavailable.
- Tests: LUFS/peak vectors, caps, album-vs-track precedence, missing/stale metadata, limiter bypass/ceiling, source immutability, CPU and cancellation tests.
- Acceptance criteria: Local processing is reversible/non-destructive and technically bounded; YouTube is never falsely described as normalized; fallback is unity/explicit.
- Risks: DSP clipping, CPU/battery cost, analyzer disagreement, audible pumping.
- Rollback/failure considerations: Disable runtime DSP and use unity gain while preserving source; invalidate analysis by policy version, not by deleting source metadata.

## Phase 10 - Library/favorites/playlists

- Purpose: Add durable library organization, favorites, playlists, history, resume, and queue snapshots.
- Prerequisites: Phases 4-6; Room/IndexedDB model and privacy rules.
- Affected modules: Repositories, migrations, library/domain use cases, Android/Web library UI.
- Relevant skills: `aurora-architecture`, `aurora-player`, `aurora-security`, `aurora-web`, `test-driven-development`, Android `testing-setup`.
- Expected files: DAO/object-store implementations, migrations, playlist/favorite/history use cases, UI projections, export/delete privacy controls.
- Implementation sequence: Add transactional entities; implement favorites/playlists; record play/skip/completion/resume; persist queue snapshots; add search indexes; add retention/export/delete controls.
- Tests: Migration upgrades, transaction atomicity, duplicate/reorder, history threshold, resume debounce, privacy deletion/export, offline behavior, browser quota.
- Acceptance criteria: Local library organization works offline; queue can restore from snapshot; history is private and deletable; remote cache policy remains bounded.
- Risks: Data loss during migration, event duplication, private-data overcollection.
- Rollback/failure considerations: Use forward migrations and backups/export; disable sync-like behavior; never silently discard user playlists/history.

## Phase 11 - AI foundation

- Purpose: Implement provider-neutral AI contracts, gateway boundary, schema validation, redaction, and tool authorization without a provider client.
- Prerequisites: Phases 3, 6, 7, 10; `AURORA_AI_AGENT_SPEC.md`, `ADR-012-AI-AUTHENTICATION.md`.
- Affected modules: Shared contracts, domain tool executor, AI gateway service boundary, client AI state holders.
- Relevant skills: `aurora-ai-music-agent`, `aurora-security`, `api-and-interface-design`, `security-and-hardening`, `observability-and-instrumentation`, `test-driven-development`.
- Expected files: AI request/response/error schemas, allowlist validator, tool authorization, privacy context filter, gateway interface, redacted audit model.
- Implementation sequence: Define versioned envelopes; validate input/output; implement provider capability checks; implement confirmation policy; add cancellation/timeouts; add typed failures and deterministic fallback; add gateway auth boundary without secrets.
- Tests: Schema rejection, unknown tools, arbitrary URLs, out-of-range IDs, duplicate calls, confirmation, redaction, offline fallback, timeout/cancellation, capability mismatch.
- Acceptance criteria: AI cannot mutate arbitrary state or invent URLs; client has no provider secret; deterministic fallback works when gateway is unavailable.
- Risks: Prompt injection, overbroad context, unsafe tool execution, accidental logging.
- Rollback/failure considerations: Disable AI entry points while keeping search/playback; reject all unrecognized envelopes; rotate credentials outside code if exposure occurs.

## Phase 12 - Gemini integration

- Purpose: Connect the gateway to Gemini API access using the selected project/API or explicit Google Cloud OAuth boundary.
- Prerequisites: Phase 11; Google project, billing/quota owner, consent/redirect configuration, current Gemini docs.
- Affected modules: Gateway Gemini adapter, secure secret/config management, AI provider capability mapping, auth/account state.
- Relevant skills: `aurora-ai-music-agent`, `aurora-security`, `source-driven-development`, `security-and-hardening`.
- Expected files: Gemini adapter, provider error mapper, server configuration schema, OAuth lifecycle adapter only if required, integration fixtures with no real secrets.
- Implementation sequence: Configure credentials in secret manager; implement documented request/response mapping; validate output envelope; map rate/safety/auth errors; implement refresh/revocation; add usage limits and redacted telemetry.
- Tests: Contract tests with deterministic provider stub; malformed/safety/quota/auth/timeout errors; OAuth state/PKCE/expiry if used; no-key client bundle scan.
- Acceptance criteria: Gemini calls work only through gateway; consumer Gemini app session is not used; billing/quota ownership is explicit; failures fall back cleanly.
- Risks: Billing surprise, model/schema drift, leaked key, consent misconfiguration.
- Rollback/failure considerations: Disable Gemini provider flag and retain deterministic AI/search; revoke/rotate gateway credentials; never embed a temporary key in client code.

## Phase 13 - OpenAI integration

- Purpose: Connect the gateway to OpenAI API project access using server-held API credentials.
- Prerequisites: Phase 11; OpenAI API project/billing owner, current API docs, gateway secret management.
- Affected modules: Gateway OpenAI adapter, secure config, AI capability/error mapping.
- Relevant skills: `aurora-ai-music-agent`, `aurora-security`, `source-driven-development`, `security-and-hardening`.
- Expected files: OpenAI adapter, provider error mapper, server configuration, integration fixtures with no real credentials.
- Implementation sequence: Configure API credential server-side; implement documented API request/response mapping; validate tool envelope; map limits/safety/auth errors; add budgets and redacted telemetry; keep ChatGPT identity separate.
- Tests: Stubbed API contract, malformed/safety/quota/auth/timeout, key-not-in-bundle scan, provider selection, cancellation.
- Acceptance criteria: OpenAI API access is gateway-only; ChatGPT app/plan is not assumed to fund API access; no unverified Sign in with ChatGPT flow is shipped.
- Risks: API billing, API contract changes, provider data retention/privacy obligations.
- Rollback/failure considerations: Disable OpenAI provider flag; revoke/rotate key; preserve deterministic recommendations and existing playback.

## Phase 14 - AI music agent

- Purpose: Deliver natural-language discovery, validated tool calls, candidate resolution, queue proposals, and explicit Play All/play actions.
- Prerequisites: Phases 7, 10-13; validated AI provider contracts.
- Affected modules: AI application use cases, tool executor, search/recommendation services, Android/Web AI surfaces.
- Relevant skills: `aurora-ai-music-agent`, `aurora-music-provider`, `aurora-player`, `aurora-recommendations`, `ui-ux-pro-max`, `aurora-security`.
- Expected files: Intent parser orchestration, candidate resolver, confirmation state, AI conversation UI, tool audit projections.
- Implementation sequence: Parse bounded intent; resolve references; generate candidates; capability-check; rank deterministically; show provenance/uncertainty; require confirmation; create queue through coordinator; persist allowed session records.
- Tests: Prompt fixtures, ambiguity, provider mismatch, no results, safety refusal, confirmation, arbitrary URL rejection, queue handoff, privacy-disabled context, offline fallback.
- Acceptance criteria: "similar but darker/slower" yields validated candidates and a queue proposal; user action/explicit command is required where specified; existing playback is not disrupted by AI failure.
- Risks: Ambiguous entities, prompt injection, unintended durable writes, provider policy mismatch.
- Rollback/failure considerations: Disable individual tools or AI entirely; retain ordinary search and manual queue; invalidate unsafe cached proposals.

## Phase 15 - Recommendation engine

- Purpose: Implement deterministic candidate generation/ranking and optional AI semantic candidates with explanations.
- Prerequisites: Phase 10 and Phase 14; `RECOMMENDATION_ENGINE.md`, `AURORA_RECOMMENDATION_SPEC.md`.
- Affected modules: Recommendation domain, history/library repositories, provider candidate adapters, Home/AI surfaces.
- Relevant skills: `aurora-recommendations`, `aurora-ai-music-agent`, `aurora-security`, `test-driven-development`, `performance-optimization`.
- Expected files: Candidate source interfaces, signal normalization, versioned ranker, diversity/exclusion filters, reason codes, snapshot persistence.
- Implementation sequence: Define signal privacy/freshness; generate candidates from local/provider sources; normalize signals; rank with deterministic tie-break; apply availability/diversity; optionally merge AI semantic candidates; persist bounded snapshots.
- Tests: Sparse history, ties, repeats/skips, privacy-disabled signals, unavailable metadata, diversity, deterministic ordering, AI unavailable, large candidate sets.
- Acceptance criteria: Recommendations work without AI; AI never supplies authoritative URLs; every displayed result has source/provenance and actionable failure behavior.
- Risks: Filter bubbles, feedback loops, privacy leakage, non-deterministic ranking.
- Rollback/failure considerations: Turn off AI candidates or a signal; use deterministic baseline and explicit empty state; keep ranker version for reproducibility.

## Phase 16 - Haptics and advanced motion

- Purpose: Complete semantic HapticEngine mappings and continuity motion after workflows are stable.
- Prerequisites: Phases 2, 6, 14; `AURORA_MOTION_SPEC.md`, `AURORA_HAPTIC_SPEC.md`.
- Affected modules: Android haptic adapter, Web fallback, shared semantic events, motion components across clients.
- Relevant skills: `aurora-motion`, `aurora-haptics`, `aurora-liquid-glass-ui`, `compose-animations`, `compose-performance`, `gsap-core`, `gsap-performance`.
- Expected files: HapticEngine/adapter, motion tokens/transitions, gesture/reorder/scrub components, reduced-motion settings and tests.
- Implementation sequence: Map semantic events to capabilities; rate-limit sparse ticks; add album/player and mini-player continuity; add sheets/search/queue/download/AI transitions; implement reduced-motion/transparency variants; measure frame cost.
- Tests: Fake haptic mapping/fallback/rate limit; gesture interruption; reduced-motion snapshots; motion cancellation; frame/performance checks.
- Acceptance criteria: Haptics never run per scroll/frame; motion communicates hierarchy; reduced motion preserves state/focus; all transitions can be interrupted and cancelled.
- Risks: Sensory fatigue, battery/frame cost, gesture conflicts.
- Rollback/failure considerations: Reduce motion/haptics through semantic settings; preserve instantaneous state changes; disable expensive blur animation independently.

## Phase 17 - Web application

- Purpose: Complete the separate Web product surface with browser media, IndexedDB, routes, responsive behavior, and Web-specific controls.
- Prerequisites: Phases 1-16; `WEB_ARCHITECTURE.md`, `WEB_PRODUCT_ARCHITECTURE.md`, Node/React/Next matrix.
- Affected modules: `web/` routes/components/adapters, IndexedDB repositories, browser media/Media Session, Web auth/session boundary.
- Relevant skills: `aurora-web`, `frontend-ui-engineering`, `ui-ux-pro-max`, `browser-testing-with-devtools`, `aurora-player`, `aurora-security`.
- Expected files: Next App Router pages, Web coordinator/adapters, IndexedDB stores, Media Session integration, responsive UI, browser auth/session handling.
- Implementation sequence: Implement URL-addressable product routes; wire local library/player; add visible YouTube iframe; add IndexedDB; add keyboard/focus/zoom; add Web Media Session where supported; keep server secrets server-side.
- Tests: Browser unit/contract tests, Playwright/DevTools journeys, autoplay/visibility, keyboard navigation, zoom/mobile/desktop, IndexedDB quota and reload restoration.
- Acceptance criteria: Web uses no Android APIs; local playback and YouTube restrictions are truthful; routes are shareable; browser limitations are explicit and recoverable.
- Risks: Browser autoplay/storage variance, hydration mismatches, server/client secret leakage.
- Rollback/failure considerations: Disable a browser feature based on capability; retain local/manual flows; use opaque fallback and no server secret in client bundle.

## Phase 18 - Accessibility hardening

- Purpose: Validate and fix accessibility across state, themes, dynamic artwork, input methods, and platform settings.
- Prerequisites: All user-facing flows; `ACCESSIBILITY_SPEC.md`, UI/UX Pro Max checks.
- Affected modules: Every presentation module, semantics, focus/navigation, haptics fallback, glass renderer.
- Relevant skills: `ui-ux-pro-max`, `aurora-liquid-glass-ui`, `aurora-motion`, `aurora-haptics`, `compose-focus-navigation`, `compose-ui-testing-patterns`, `browser-testing-with-devtools`.
- Expected files: Semantics/ARIA labels, focus order, keyboard alternatives, contrast/fallback logic, accessibility test suites.
- Implementation sequence: Test screen readers; apply target sizes; test text scaling/zoom; test dark/light/artwork contrast; add non-color statuses; verify reduced motion/transparency; verify queue/scrub alternatives.
- Tests: Android Compose semantics/accessibility; Web axe-equivalent/manual browser checks; keyboard-only journeys; 1.5x text scale and zoom; contrast after artwork; reduced settings.
- Acceptance criteria: No critical action depends on color, motion, blur, haptic, or hover; all controls are labeled/focusable; text and controls fit at required scales.
- Risks: Late layout regressions, dynamic theme contrast failures, inaccessible embedded player constraints.
- Rollback/failure considerations: Prefer opaque surfaces and visible text; remove nonessential animation; defer a decorative feature instead of weakening semantics.

## Phase 19 - Performance optimization

- Purpose: Measure and optimize frame time, startup, memory, artwork, database, playback, and AI latency.
- Prerequisites: Representative Android devices, browsers, datasets, and completed workflows.
- Affected modules: Rendering, artwork pipeline, repositories/indexes, player position projection, AI gateway limits.
- Relevant skills: `performance-optimization`, `aurora-liquid-glass-ui`, `aurora-player`, `compose-performance`, `gsap-performance`, `observability-and-instrumentation`.
- Expected files: Profiling baselines, image/cache policies, pagination/worker code, metrics dashboards, performance regression tests.
- Implementation sequence: Measure first; isolate position updates; lazy-load/dimension artwork; bound caches/concurrency; optimize query indexes/pagination; reduce blur; profile startup and AI first-useful response; add regression budgets.
- Tests: 16.7 ms frame checks where applicable, startup traces, memory/artwork stress, large-library queries, battery/network/buffering, gateway rate limits.
- Acceptance criteria: Budgets in `PERFORMANCE_BUDGET.md` are measured or explicitly adjusted with evidence; no optimization fabricates state or removes accessibility.
- Risks: Premature optimization, cache staleness, memory pressure, device-specific regressions.
- Rollback/failure considerations: Revert the smallest measured optimization; use capability/performance tiers; lower visual effects before dropping core functionality.

## Phase 20 - Testing

- Purpose: Complete the behavior, contract, integration, UI, accessibility, security, performance, and end-to-end test matrix.
- Prerequisites: Implemented phases and `TEST_ARCHITECTURE.md`, `TESTING.md`.
- Affected modules: All test source sets and deterministic fakes.
- Relevant skills: `aurora-testing`, `test-driven-development`, `focused-tdd`, Android `testing-setup`, `compose-ui-testing-patterns`, `browser-testing-with-devtools`, `review-test-coverage`, `review-security-privacy`.
- Expected files: Unit/contract/integration/UI/E2E tests, fixtures, fakes, screenshot references, test reports, migration fixtures.
- Implementation sequence: Close owner-layer gaps; run provider/player/persistence/AI/security negative tests; add accessibility and responsive suites; add a small E2E smoke set; run full matrix on CI.
- Tests: All tests are the phase deliverable; include offline, unknown, unsupported, retry, stale callbacks, permission/auth, policy, and deletion paths.
- Acceptance criteria: Critical behavior is covered at its owner; tests use deterministic fakes; screenshots supplement semantics; no claim is based only on line coverage.
- Risks: Flaky device/browser tests, overreliance on snapshots, missing real-boundary behavior.
- Rollback/failure considerations: Quarantine and fix flakes with evidence; do not delete failing tests to make CI green; narrow E2E scope while preserving critical journeys.

## Phase 21 - CI/release

- Purpose: Make builds, checks, security scanning, documentation validation, and release artifacts reproducible.
- Prerequisites: Phase 20; stable signing/release policy, privacy/policy review, `TOOLCHAIN_MATRIX.md`.
- Affected modules: `.github/workflows/`, build configuration, release metadata, documentation checks.
- Relevant skills: `ci-cd-and-automation`, `shipping-and-launch`, `release-notes`, `aurora-security`, `review-architecture-scope`, `review-security-privacy`, `review-test-coverage`.
- Expected files: Android/Web CI workflows, dependency/version checks, secret configuration docs, release checklist, policy/privacy links, artifact provenance.
- Implementation sequence: Pin environment/action majors and eventually SHAs; run Android wrapper builds with Temurin 17, `compileSdk 37`, `targetSdk 36`, and `minSdk 26`; run Web Node 24/npm 11 checks; run tests/security/docs/link audits; build signed artifacts in protected environment; prepare release notes.
- Tests: Clean CI build, unit/integration/UI suites, dependency/license/security checks, documentation links, no-secret scan, artifact install/smoke test.
- Acceptance criteria: CI is green from a clean checkout; no secrets appear in logs/artifacts; release behavior respects YouTube/AI/privacy boundaries; rollback artifact and feature flags exist.
- Risks: Credential/signing exposure, provider policy changes, unreproducible dependency resolution, release-only regressions.
- Rollback/failure considerations: Stop release promotion; revoke exposed credentials; roll back to last verified artifacts; disable provider/AI flags without disabling local playback.
