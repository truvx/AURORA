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

## Next Phases

Phase 17 (Web application) remains limited to the AI gateway and app shell. Phases 18-21 (accessibility hardening, performance, testing, CI/release) are unstarted; there is still no CI workflow, and `AiE2ETest` remains a live-network test inside the unit source set.

Also outstanding: Phase 17 (Web application) is limited to the AI gateway and app shell; Phases 18-21 (accessibility hardening, performance, testing, CI/release) are unstarted. There is no CI workflow, and `AiE2ETest` is a live-network test inside the unit source set, so `testDebugUnitTest` cannot pass without a local gateway on port 3000.
