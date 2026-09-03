# AURORA player architecture

Status: final v1 contract. The YouTube embedded player is the explicit provider exception to native local-media engine behavior.

## Single coordinator

One application-level playback coordinator owns the engine and canonical state. Android uses an AndroidX Media3/ExoPlayer adapter where appropriate; web uses a browser media adapter. No screen constructs its own player.

### Application Scope & Dependency Injection
To enforce the single coordinator invariant, the player engine components (e.g., `PlayerCoordinator`, `Media3PlayerAdapter`, `AuroraTrackResolver`) are application-scoped singletons.
On Android, these are strictly owned by a manual Dependency Injection container (`AppContainer`) instantiated in the `Application` class. Neither UI ViewModels nor Android Services instantiate or own the player engine; they retrieve the canonical instance from the `AppContainer`.

## State model

Separate canonical `PlayerState` (status, current track, position, duration, buffering, error, volume, effective quality, normalization) from `QueueState` (stable items, current index, traversal order, repeat, pending edits). `PlayerCommand` represents play, pause, seek, next, previous, replay, shuffle, repeat, volume, add/remove/reorder, and reload. `PlayerEffect` carries one-shot user-safe messages and haptic/navigation intents.

## Platform behavior

Android local playback integrates media session, notification/lock-screen controls, audio focus, becoming-noisy/headphone, Bluetooth route changes, interruptions, and lifecycle-safe foreground/background playback. Web local playback handles autoplay policy, visibility, Media Session support, keyboard controls, and browser lifecycle. YouTube playback remains a visible foreground IFrame/WebView surface with no promised background or screen-off behavior. All actions feed the same domain model.

## Invariants

Queue identity is stable through shuffle. Repeat-off stops at the end; repeat-one stays on the item; repeat-all wraps. Position is clamped to known duration and unknown duration remains unknown. Resume position is keyed by provider/source/track identity. Confirm engine state before showing “playing”.

## Tests

Test the reducer/state machine, queue editing, shuffle, repeat, seek bounds, resume, errors, cancellation, interruption, audio focus, media session mapping, and cross-surface state synchronization.
