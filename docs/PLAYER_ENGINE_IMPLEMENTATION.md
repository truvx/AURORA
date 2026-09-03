# Player Engine Implementation (Phase 5)

## Architecture Overview
The Player Engine is the core component responsible for media playback, lifecycle management, and coordination of state across the AURORA application. It adheres to the canonical architecture defined in `PLAYER_ARCHITECTURE.md`.

### Core Components
- **AppContainer**: Manual dependency injection container providing application-scoped singletons for the database, track resolver, and player engine.
- **AuroraTrackResolver**: Resolves domain `trackId`s into playable Android `Content URI`s by querying the `LocalLibraryDao`.
- **Media3PlayerAdapter**: The low-level interface bridging the domain `PlayerCommand`s and state events to the `androidx.media3.exoplayer.ExoPlayer`.
- **PlayerCoordinator**: The domain-level canonical playback authority. It houses the state machine, manages the playback queue, and exposes the `PlayerState`.
- **AuroraMediaSessionService**: Exposes the application-scoped `Media3PlayerAdapter` to the OS via a `MediaSession`.

## Lifecycle and Ownership
- The `ExoPlayer`, `Media3PlayerAdapter`, and `PlayerCoordinator` are owned strictly by the `AppContainer` (Application scope).
- They are initialized lazily upon first access (e.g., when the `MainActivity` requests the `PlayerViewModel` or the `AuroraMediaSessionService` is started).
- The `PlayerViewModel` acts purely as a UI state adapter and does NOT own the player.
- The `AuroraMediaSessionService` binds a `MediaSession` to the existing application-scoped player; it does not instantiate or release the player upon its own destruction.

## Features Implemented
- **Queue Management**: Loading, queue traversal (next, previous), queue mutations (add, remove, move).
- **Shuffle & Repeat**: Modes implemented in `QueueState`. Shuffle traversal maintains a shuffled list of indices (`shuffledOrder`) decoupled from the canonical queue order to ensure deterministic randomized playback.
- **Audio Focus**: Managed natively via ExoPlayer's `setAudioAttributes(..., handleAudioFocus = true)`.
- **Track Resolution**: Secure mapping from domain string IDs (e.g., `local_123`) to `Content URIs` via `AuroraTrackResolver`.
- **MediaSession Forwarding**: `AuroraMediaSessionService` wraps ExoPlayer in a `ForwardingPlayer` to declare lock-screen/headset Skip capabilities (`COMMAND_SEEK_TO_NEXT`/`COMMAND_SEEK_TO_PREVIOUS`) and forwards these intents accurately into the canonical `PlayerCoordinator` rather than relying on ExoPlayer's internal queue.

## Known Limitations and Deferred Work
- **Advanced Error Models**: While errors propagate to `PlayerState`, advanced classification into domain exceptions is deferred to future polishing phases.
- **Notification**: Minimum Media3 notifications are currently relied upon. Polished custom notifications are deferred to Phase 6 UI.
- **Phase 6 Containment**: Some UI components (`NowPlayingScreen`, `QueueSheet`, etc.) were prematurely drafted during Phase 5. They remain in the codebase but have been contained so as not to break Phase 5 architectural purity.
