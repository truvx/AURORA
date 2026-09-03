# AURORA architecture review

## Review status

Final architecture review completed against `AGENTS.md`, the foundation docs, the AURORA skills, UI/UX Pro Max, Android/Compose guidance, official provider documentation, security/testing/performance guidance, and ADR-010 through ADR-016. UI/UX Pro Max reinforced artwork-aware but restrained glass, contrast checks, mobile-first priority, platform-adaptive targets, keyboard/reorder alternatives, and reduced-motion/cancellable transitions. No application code was reviewed because none was created.

## Verdict

Ready for implementation of the next documented phase. This is an architecture/documentation review, not a runtime or application build validation.

## Findings and resolutions

| Area | Risk | Resolution |
|---|---|---|
| Layering | Shared code could accidentally import platform SDKs | Five-layer dependency direction, typed ports, mapping boundary, and composition roots are locked |
| Player | Mini-player/full-player/notification drift | One application-level coordinator and canonical `PlayerState`/`QueueState` |
| Providers | Capability assumptions and giant interface | Small capability interfaces plus explicit capability snapshots |
| YouTube | Temptation to substitute extraction for supported playback | Explicit policy boundary forbids extraction, unauthorized download, DRM bypass, scraping, and fake quality |
| Quality | User preference presented as source truth | Separate requested, provider, track, actual, and effective quality with downgrade reasons |
| Normalization | “Universal” mode could imply identical DSP | Final local LUFS/peak policy, non-destructive processing, and explicit provider-managed YouTube state |
| AI | Model could become arbitrary controller | Allowlisted typed tools, schema validation, application authorization, provider resolution |
| Auth | Confusing app identity with API access | Separate supported identity/auth and API access states; secure token store boundary |
| Recommendations | AI-only ranking and non-reproducibility | Deterministic candidate/ranking baseline with optional AI candidate generation |
| UI | Glass could harm contrast/performance | Compositing-time contrast checks, scrim/opaque fallback, reduced transparency, measured blur |
| Motion | Decorative animation could harm usability/FPS | One dominant story, named motion families, cancellation, reduced-motion path, performance budgets |
| Haptics | Continuous buzz/side effects during recomposition | Central semantic `HapticEngine`, sparse/rate-limited events, fake-engine tests |
| Web | Android playback concepts leak into web | Separate platform adapters with shared domain contracts and information architecture |
| Data | History/artwork/audio data over-collection | Local-first retention, evictable caches, no secrets in ordinary DB, explicit privacy controls |

## Structural review

### Circular dependencies

No intended cycle exists if each platform domain remains inward-facing and the shared layer contains only contracts/fixtures/tokens. Enforce this with build/module rules when implementation begins.

### Duplicated state

The main duplication risk is high-frequency player state in screens. Keep a single coordinator, derive projections, and isolate position subscriptions for performance.

### Provider leakage

The main leakage risk is a YouTube-specific URL/DTO crossing the provider boundary. Only stable provider/source IDs and validated domain metadata should cross it.

### AI/provider coupling

AI may request search or queue intent but never constructs source URLs or calls adapters directly. The application/tool executor resolves intent through provider capabilities.

### Platform leakage

Media3, browser media, URI handles, platform auth, and haptic primitives remain in infrastructure/platform modules. Shared contracts use values, IDs, and typed results.

### Missing failure paths covered

The design includes no network, provider policy/auth/consent, unsupported capability, stale data, offline, invalid local URI/format, decoder, audio focus, interruption, buffering, persistence, AI invalid output, rate limit, expired auth, haptic fallback, contrast fallback, reduced motion, and browser autoplay states.

### Testing gaps to guard

Implementation must add contract tests for every provider capability matrix, state-machine tests for duplicate/stale callbacks, security negative tests for arbitrary AI URLs/secrets, accessibility tests over dynamic themes, and performance tests on large artwork/library inputs.

## Design review

UI/UX Pro Max principles are applied as a review lens: clear hierarchy, intentional typography/spacing, semantic color, explicit responsive behavior, accessible controls, and restrained glass. The AURORA system rejects excessive gradients, glass layers, blur, carousels, and motion when they reduce clarity or performance. Final UI validation requires runtime evidence when implementation exists.

## Final gate resolutions

1. Cross-platform: separate Kotlin/Compose and React/TypeScript implementations with versioned contracts and fixtures; no KMP runtime in v1.
2. YouTube: Data API v3 for discovery/metadata and visible IFrame Player API embedding on Android/Web; no extraction, downloads, offline, audio-only, or hidden playback.
3. AI: provider-neutral `AIProvider` adapters behind an AURORA gateway; provider keys remain server-side; consumer Gemini/ChatGPT sessions are not reused.
4. Persistence: Room/SQLite on Android, IndexedDB on Web, PostgreSQL only if a later sync service is approved.
5. Loudness: local LUFS/ReplayGain-aware non-destructive processing with bounded gain and `-1.0 dBTP` protection; YouTube is provider-managed/unavailable.
6. Typography/icons: Inter and Material Symbols Outlined under their respective permissive licenses, with system/Lucide fallback rules.
7. Toolchain: the version and CI matrix is locked in `TOOLCHAIN_MATRIX.md`; JDK 17, stable Android API 37 compile tooling, and AGP-required Build Tools 36.0.0 are installed and verified. The initial release target remains API 36.

Future changes to these decisions require a new ADR, updated contract tests, and a consistency review.
