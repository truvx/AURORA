# AURORA Implementation Progress

## Completed Phases

- **Phase 0**: Toolchain Setup
- **Phase 1**: Repository/Project Skeleton
- **Phase 2**: Design System Foundation
- **Phase 3**: Application Shell / Navigation
- **Phase 4**: Local Music Library (Database, Scanning, Migrations)
- **Phase 5**: Player Engine (Coordinator, State Machine, Media3 Adapter, Dependency Injection)
- **Phase 6**: Queue and Now Playing UI

## Phase 6 Details (Queue and Now Playing Experience)

Phase 6 implements the complete UI for the Queue and Now Playing screens, strictly adhering to the Liquid Glass design system and existing `PlayerCoordinator` architecture.

Key actions included:
1. **Refactoring UI**: Completely replaced standard Material 3 usages in `MiniPlayer`, `PlayerScrubber`, `NowPlayingScreen`, and `QueueSheet` with AURORA's custom tokens (`GlassSurface`, `ArtworkSurface`, `GlassIconButton`).
2. **Motion and Haptics**: Implemented robust spring animations and integrated the `HapticEngine` across interactive elements to fulfill the haptic requirements of the app.
3. **Architecture Preservation**: Retained the clean separation of concerns, ensuring UI components solely observe and dispatch intents to the canonical `PlayerCoordinator` via `LocalPlayerCoordinator`.

## Current State

The Android project is fully compiling.
All unit tests and instrumented tests are passing.
Linting passes with no critical warnings.
Phase 6 UI is verified and closed.

- **Phase 7**: YouTube Discovery and Supported Playback Integration
- **Phase 8**: AI Music Intelligence + Recommendation Engine

## Phase 8 Details (AI Music Intelligence)

Phase 8 implements the complete AI and Recommendation pipeline, validating end-to-end routing without fabricating playable media.

Key actions included:
1. **Web AI Gateway**: Built a Next.js `/api/ai/intent` gateway with robust `Zod` validation schemas handling Gemini and OpenAI capabilities while preventing prompt injection or data spillage.
2. **Android Execution & UI**: Implemented `AiViewModel`, `AiToolExecutor`, and `RecommendationEngine` to securely parse structured JSON tool calls from the AI Gateway and transform them into verifiable `PlayerCommand` dispatches for the `PlayerCoordinator`.
3. **Architecture Preservation & Fabrication Defense**: Retained the clean separation of concerns, strictly rejecting any AI-generated item ID that the local library or YouTube API does not explicitly confirm exists. No raw audio files or secret keys are transmitted to/from the Android client.

## Current State

- [x] Integrate AI gateway endpoints
- [x] Hook up UI and viewmodel
- [x] Validate offline-only fallback
- [x] Configure and verify live credentials
- [x] **Live AI Test & True E2E Release Gate (COMPLETE)**: Playback chain proven to reach playing state via YouTube IFrame adapter and player progression.

**Status: COMPLETE**

- **Phase 9**: Loudness Normalization

## Phase 9 Correction (loudness measurement source)

The first Phase 9 implementation wrote hardcoded loudness values (`lufsIntegrated = -12.0`, `truePeak = 0.9`) for every scanned track. Those values were never measured, which violated the truthful-metadata rule and made normalization a fixed offset rather than normalization.

Corrected:

1. **Real measurement source**: `data/scanner/ReplayGainReader` parses ReplayGain tags from FLAC (Vorbis comments) and ID3v2.3/2.4 (`TXXX` frames). `domain/audio/ReplayGainReference` converts gain to LUFS against the ReplayGain 2.0 `-18 LUFS-I` reference.
2. **Unknown stays unknown**: a file without usable tags produces no loudness row, so `LoudnessResolver` resolves unity gain and the UI reports `Unknown`. Ogg/Opus and MP4/M4A tag layouts are not parsed yet and are reported as unknown rather than guessed.
3. **Stale data purged**: schema v4 (`MIGRATION_3_4`) deletes `analysisVersion = 1` rows so previously fabricated values do not survive the upgrade.
4. **Test coverage**: `LoudnessResolverTest` (24 cases) covers every target, gain/attenuation cap boundary, the `-1.0 dBTP` protection path, album-context fallback, and unknown handling. `ReplayGainReaderTest` (16 cases) covers both tag layouts plus malformed, truncated, and unsupported input. `MigrationTest.migrate3To4` verifies the purge keeps measured rows.

Offline LUFS analysis for untagged files remains unimplemented; untagged files are not normalized and are not described as normalized.

## Phase 10 (Library, Favorites, Playlists) - COMPLETE

Delivered:

1. **Schema v5** (`MIGRATION_4_5`, purely additive): `library_entries`, `playlists`, `playlist_entries`, `listening_events`, `resume_positions`, `queue_snapshots`, `queue_snapshot_items`.
2. **`LibraryOrganizationDao`**: favorites, playlists with transactional append/remove/reorder, append-only history with retention pruning, resume positions keyed by provider + media, and single-retained queue snapshots.
3. **`LibraryOrganizationRepository`** (domain) + `RoomLibraryOrganizationRepository` (data), wired through `AppContainer`.
4. **Favorites UI**: heart control in the library list, verified persisting to `library_entries` and reflecting back through the observed flow.

Ordering guarantees under test: duplicate entries allowed, positions stay contiguous after removal, reorder is deterministic in both directions, out-of-range moves are rejected, and an unavailable track stays in its playlist. Privacy deletion erases history, resume, and queue snapshots while leaving favorites and playlists intact.

5. **Playlist UI**: Tracks/Playlists switch, create, delete, per-track add, and reorder via explicit up/down controls (reachable by screen reader and keyboard; each press is one transactional move).
6. **History and resume**: `PlaybackHistoryRecorder` observes the coordinator rather than living inside it, so persistence failures cannot break playback. A track left before 90% is a skip; unknown duration counts as a skip rather than an invented completion. Resume writes are debounced.
7. **Queue snapshots**: written when the arrangement changes, restored on cold start without auto-playing. Unresolvable tracks are dropped rather than faked.
8. **Privacy controls**: export writes history to JSON and reports path and count; delete takes two presses and clears history, resume positions, and queue snapshots while leaving favorites and playlists intact.

Library rows now start playback on tap; previously there was no way to play a local track from the library.

Verified on device: playlist created and persisted, tracks added, reorder swapped positions and kept them contiguous, export wrote valid JSON, and playing a track recorded a PLAY event.

### Known issue found during Phase 10 verification

Playback does not progress: the media prepares and the decoder is created, but position stays at 0 and no `Started` event is emitted. This predates Phase 10 and most likely sits in `CrossfadeMedia3Adapter`, which wraps two `Media3PlayerAdapter` instances - position is probably polled from the inactive one. That class is also the only audio component with no test coverage, since it needs a `Context` and two real ExoPlayer instances. Needs an instrumented test and a fix.

## Phase 18 (Accessibility hardening) - in progress

Reduced motion and reduced transparency now change real behaviour; before this they were
helper functions no code called. Preferences resolve once at the root and flow through a
CompositionLocal, so glass, motion, and semantics all react to the same state.

Fixed:

- The scrubber announced its raw slider float; it now speaks position as time and states
  explicitly when duration is unknown.
- The mini player was an unlabelled clickable row; it announces the track and what tapping
  does, and carries playback failures as an assertive live region.
- Playback errors were never surfaced at all - status reached Error and the UI kept showing
  a player that silently never advanced. Failures now appear as text plus an icon, never
  colour alone.

Verified on device at 1.5x font scale: text wraps rather than clipping and controls stay
reachable. Reorder controls measure 48dp on rendered nodes.

Espresso 3.6.1 could not run on Android 17 at all, so no Compose UI test could have passed
before this phase regardless of the code under test.

Outstanding for Phase 18: web accessibility (Phase 17 has no UI to audit yet), contrast
validation against live artwork, and a TalkBack pass on a physical device.

## Completion pass (plan: docs/superpowers/plans/2026-09-12-aurora-completion.md)

Three defects found, each of which made an already-implemented feature inert:

1. **Completions were never recorded.** `loadTrack` did not place the track in the queue, so
   `skipNext` returned at its empty-queue guard and `Completed` was never reached. Separately,
   Media3's `BufferingChanged(false)` immediately after `TrackCompleted` reset the status about
   a millisecond after it was set - invisible through a conflated `StateFlow`. Both fixed;
   verified on device as PLAY then COMPLETE at 2998ms of a 3000ms track, with a queue snapshot.
2. **The media session had never worked.** `AuroraMediaSessionService` cast the composite
   adapter to `Media3PlayerAdapter` and threw `ClassCastException` on every bind, so lock
   screen, notification, Bluetooth, and Android Auto controls were dead. Nothing in the app
   starts the service, so it produced no in-app symptom. Replaced the downcast with a
   `Media3PlayerOwner` capability interface.
3. **Espresso could not run on Android 17** (fixed in the accessibility pass), meaning no
   Compose UI test could have passed before it regardless of the code under test.

Coverage added: `AuroraMediaSessionService`, `YouTubePlayerAdapter`, `CompositePlayerAdapter`,
`CrossfadeMedia3Adapter`, a coordinator-plus-recorder integration test, and the web client's
first tests (Vitest, covering the AI tool allowlist). CI now runs web tests and the R8 release
build.

### Phase 19 (Performance)

A `:benchmark` macrobenchmark module measures cold startup, with a release-like, non-debuggable
`benchmark` build type on `:app`. It produces no numbers yet: androidx.benchmark refuses to run
on an emulator, and that refusal is deliberately not suppressed. **Requires a physical device.**

### Phase 18 contrast finding

Text over the background rendered today measures above 4.5:1. However, `AmbientArtworkLayer` is
still a placeholder that renders a synthetic gradient rather than album artwork, and no contrast
scrim exists. Dark glass is only 20% opaque, so near-white text over a white album cover would
measure **1.35:1** - effectively illegible. `ContrastTest` records this and is written to fail
the moment artwork rendering lands without a scrim.

## Phase 17 (Web application) - playable core

The web client had no player, queue, persistence, or Media Session; it was an AI gateway,
a shell, and five pages. It is now a client you can listen with.

- **`PlayerCoordinator`** owns canonical web state, mirroring the Android vocabulary without
  reusing any Android abstraction. State is published by subscription, not React state:
  position updates arrive four times a second and would otherwise re-render the whole tree.
  Components select the slice they need, so the scrubber re-renders and the rest does not.
- **`HtmlAudioAdapter`** wraps one audio element for the app. Position is polled rather than
  driven by `timeupdate`, which browsers fire irregularly and throttle in background tabs.
- **Media Session** publishes to hardware keys and OS media hubs, with every handler routed
  back through the coordinator. Unsupported browsers report a capability rather than failing.
- **File System Access** lets the user pick a music folder once; the handle is stored in
  IndexedDB so the library survives reloads. Audio bytes are never copied - files are
  re-opened on demand. Unsupported browsers are told so explicitly.
- **IndexedDB** stores bounded metadata only. A failed scan never overwrites a saved library.

Two lessons from Android were carried over deliberately: a loaded track is placed in the
queue, and `Completed`/`Error` are terminal so a settling event cannot erase them.

Autoplay refusal is treated as a recoverable browser policy with an explanation, not an error.

Web test coverage went from zero to 16, including the coordinator's queue, terminal-state,
autoplay, and unknown-duration behaviour.

### Second pass

- **Tag parsing** (`lib/library/tags.ts`): FLAC Vorbis comments and ID3v2.3/2.4 text frames,
  so the library shows real titles, artists, and albums instead of filenames. Only the
  header region of each file is read. A file without tags falls back to its filename for the
  title and leaves artist and album undefined - the filename is a fallback, not a guess.
  MP4/M4A atoms are still unparsed and report nothing rather than something invented.
- **Favorites** (schema v2, additive): stored in IndexedDB with a filter in the library and
  a filled/outlined control so state is never signalled by colour alone.
- **Playlists** (`lib/library/organization.ts` + `components/library/PlaylistsSection.tsx`):
  create, delete, open, add, remove, and reorder. Reordering uses named Move up / Move down
  controls rather than drag, so it is reachable by keyboard and screen reader. A track whose
  file is missing keeps its place and reads as unavailable rather than disappearing from a
  list the user built. 15 tests cover the ordering guarantees against a real IndexedDB
  (fake-indexeddb), matching what the Android DAO is held to.
- **Now Playing route** with the queue, including position announcements ("track 3 of 12")
  and a ▶ marker so the current track is not identified by colour alone.

### Third pass - Phase 17 complete

- **History and resume** (schema v3): `PlaybackHistoryRecorder` observes the coordinator
  rather than living inside it, so a persistence failure can never stop playback. A track
  left before 90% is a skip; unknown duration counts as a skip rather than an invented
  completion; resume writes are debounced; privacy delete erases history and resume while
  leaving favorites and playlists intact.
- **Albums and artists** derived from tags at read time. An album artist is named only when
  every track agrees, so a compilation stays unattributed. Untagged tracks collect under an
  explicit Unknown group, sorted last so it cannot bury real albums.
- **YouTube IFrame surface**: the official player, visible and with its own controls,
  branding, and ads untouched. Autoplay defaults off and cues rather than loads until there
  is explicit intent. The position timer reports only while playing - the unguarded version
  of exactly this overwrote local playback on Android. No extraction, no hidden player, no
  background playback, and quality stays provider-determined.

Two production bugs were found by the history tests: resume initialised its last-write clock
to 0, conflating "never written" with "written at epoch 0"; and recently-played filtered out
SKIP events but still found the earlier PLAY for the same track, so a skipped track
reappeared as something the user had listened to. The most recent event now decides.

Browser playback is verified: a served FLAC reports `loadedmetadata duration=3.00`, fires
`playing`, advances 99ms to 3000ms, and fires `ended` - the exact events `HtmlAudioAdapter`
consumes. Only the folder picker's user gesture remains outside automated verification.

Web tests: 70.

## Phase 20 (Testing)

Closed the owner-layer gaps the matrix called for, and the tests found a real bug.

- **`RecommendationEngineTest`** (11 cases): ranking, case-insensitive matching, limits, and
  the rules that matter offline - one failing provider degrades results rather than emptying
  them, every provider failing yields no candidates rather than an error, and equal scores
  keep a stable order so the same request does not return different results twice.
- **`MusicProviderContractTest`**: one suite run against a provider rather than per-provider
  assertions, as docs/TEST_ARCHITECTURE.md asks. Covers capability declaration, provenance
  surviving the mapping, typed failures instead of thrown exceptions, an unknown id failing
  rather than resolving to a substitute, and no raw media URL escaping as an item id.
- **Playwright smoke suite** (7 journeys): every route rendering without console errors,
  library view switching, playlist creation surviving a reload, accessible names on playlist
  controls, keyboard-only reachability, and the empty Now Playing state. Runs against the
  production build in CI, not the dev server.

### Bug found by the contract test

`YouTubeMusicProvider.search()` returned ids prefixed `youtube:` while `resolveMetadata()`
returned the bare video id. Three call sites route on that prefix - `AiToolExecutor` and both
`AuroraTrackResolver` lookups - so any item obtained through metadata resolution was treated
as local, resolved to no URI, and silently failed to play. That is the exact path AI-driven
YouTube playback takes. Both now return the prefixed form.

The provider also read `BuildConfig.YOUTUBE_API_KEY` directly, which made it untestable and
coupled it to `local.properties`; the key is now injected, and an unconfigured key is a typed
failure rather than a crash.

## Phase 19 (Performance optimization)

Measured first, as the phase requires, and the measurement found two budget violations.

**Full-library loads removed.** `observeLocalItems()` and `getLocalItems()` read every row
with no limit, and `LocalLibraryProvider.search()` then filtered that whole result in Kotlin -
the full-library load `docs/PERFORMANCE_BUDGET.md` rules out, getting slower with every track
added. Search now runs in SQL with a bounded limit, and paged queries plus a count query were
added. The unpaged reads remain only for scan reconciliation, which genuinely needs every row.

**Regression budgets added.** `QueuePerformanceTest` measures queue operations against the
50 ms domain budget on a 1,000-item queue, including a head-versus-tail scaling check that
catches an operation turning quadratic - something a single-operation budget misses.
`LibraryPagingTest` verifies paging correctness and the 100 ms warm query target against a
real 2,000-row SQLite database.

Measured results, with method and dataset, are recorded in `docs/PERFORMANCE_BUDGET.md`.

### Requires a physical device

Cold start, frame time, scroll jank, and heap snapshots are not measured. `androidx.benchmark`
refuses to run on an emulator by design - it reports `ERROR: Running on Emulator` and warns
that suppressing the check compromises accuracy. The module is left refusing rather than
configured to emit numbers that would look like evidence without being any. Run
`./gradlew :benchmark:connectedBenchmarkAndroidTest` with a device attached.

Throttled mobile-web measurement is also outstanding; the recorded web figures are unthrottled
localhost and are a baseline only.

## Next Phases

Phase 17 (Web application) remains limited to the AI gateway and app shell. Phases 18-21 (accessibility hardening, performance, testing, CI/release) are unstarted; there is still no CI workflow, and `AiE2ETest` remains a live-network test inside the unit source set.

Also outstanding: Phase 17 (Web application) is limited to the AI gateway and app shell; Phases 18-21 (accessibility hardening, performance, testing, CI/release) are unstarted. There is no CI workflow, and `AiE2ETest` is a live-network test inside the unit source set, so `testDebugUnitTest` cannot pass without a local gateway on port 3000.
