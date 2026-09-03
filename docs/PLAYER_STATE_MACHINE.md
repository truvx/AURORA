# AURORA player state machine

Status: final v1 state contract. One `PlayerCoordinator` owns the state for each client process; every playback surface consumes its projection. The coordinator is instantiated exactly once per application lifecycle via the `AppContainer` dependency injection.

## State model

`PlayerState` is immutable and contains:

- `status`: `Idle | Loading | Ready | Playing | Paused | Buffering | Seeking | Completed | Error`.
- `currentTrack`: stable provider/source track identity or none.
- `position`: `PlaybackPosition` with elapsed, duration (`Known` or `Unknown`), and buffered position where available.
- `queue`: a reference to canonical `QueueState`, never a copied screen queue.
- `volume`, `audioFocus`, `effectiveQuality`, `normalization`, and `lastError`.
- engine/platform capability facts needed for truthful control availability.

`AudioFocusState` is `Unknown | Gained | LostTransiently | LostPermanently | Ducking` and is owned by the platform adapter/application coordinator. A transient loss pauses or ducks according to policy and can resume only when the prior intent and focus policy allow; a permanent loss clears play intent. Becoming-noisy events pause when configured. These rules are platform-adapted, but the resulting domain state is canonical.

`PlaybackPosition` uses a monotonic time basis for progress calculations where platform support permits. Persisted resume positions are wall-clock-independent values keyed by source/provider/track identity.

## Commands

`Load(track)`, `Play`, `Pause`, `Seek(target)`, `SkipNext`, `SkipPrevious`, `Replay`, `SetShuffle`, `SetRepeat`, `AddToQueue`, `RemoveFromQueue`, `MoveInQueue`, `ClearQueue`, `SetVolume`, `Reload`, and `Stop`.

System events include `EnginePrepared`, `EngineStarted`, `EnginePaused`, `BufferingChanged`, `PositionChanged`, `SeekStarted`, `SeekCompleted`, `TrackCompleted`, `EngineError`, `AudioFocusChanged`, `BecomingNoisy`, `RouteChanged`, `AppBackgrounded`, and `AppForegrounded`.

## Transition table

| From | Event/command | To | Required behavior |
|---|---|---|---|
| `Idle` | `Load(track)` | `Loading` | Resolve source through provider/player adapter; do not claim playing |
| `Loading` | `EnginePrepared` + play requested | `Playing` | Start only after engine confirms |
| `Loading` | `EnginePrepared` + paused request | `Ready` | Keep item ready without playing |
| `Loading` | retryable error | `Error` | Preserve track, retryability, and safe recovery action |
| `Ready` | `Play` | `Playing` | Request engine start |
| `Ready` | `Seek` | `Seeking` | Seek only if duration/engine support permits |
| `Playing` | buffer underrun | `Buffering` | Preserve position and controls |
| `Playing` | `Pause` | `Paused` | Confirm pause from engine |
| `Playing` | `Seek` | `Seeking` | Freeze/represent position honestly while seeking |
| `Playing` | `TrackCompleted` | `Completed` | Apply repeat/shuffle policy exactly once |
| `Paused` | `Play` | `Playing` | Confirm start |
| `Paused` | `Seek` | `Seeking` | Clamp only to known duration |
| `Buffering` | buffer recovered | `Playing` or `Paused` | Return to prior intent, not blindly to playing |
| `Seeking` | `SeekCompleted` | `Playing` or `Paused` | Preserve prior play intent |
| `Completed` | next/repeat policy | `Loading` or `Ready` | Resolve next item through queue policy |
| any active | non-retryable error | `Error` | Stop unsafe control, retain actionable error |
| `Error` | `Reload` | `Loading` | Re-resolve source with bounded retry |
| `Error` | `ClearQueue`/`Stop` | `Idle` | Release active source safely |

## Queue state

`QueueState` stores stable ordered entries, current stable ID/index, traversal order, shuffle mode/seed if used, repeat mode, and pending reorder state. Shuffle changes traversal order, not item identity. Reordering while playing preserves the current item and re-evaluates the next item deterministically.

`RepeatMode.Off` stops at the end, `One` replays the current item, and `All` wraps through traversal order. Empty queues are an explicit state.

## Failure handling

Classify failures as unsupported, unavailable, authorization/consent required, network, buffering timeout, audio focus/interruption, invalid local URI, decoder/format, persistence, or unknown. Only retry categories marked retryable; exponential backoff is bounded and cancellation-aware. Never silently skip a failed track without a state transition and user-visible recovery path.

## External controls

Media-session, notification, lock-screen, hardware key, headset, Bluetooth, and browser Media Session commands enter as the same typed commands. Their surfaces derive from canonical state and capability facts. Platform callback ordering must be guarded against stale events.

## Resume and completion

Resume writes are debounced and durable without blocking playback. Completion/skip analytics are emitted through a privacy-minimized history interface. The engine's natural `TrackCompleted` event is authoritative for a fully completed play. For recommendation/history completion before a reliable end event, continuous playback reaching at least 90% of known duration counts as completed; seeking past the threshold alone does not. A skip before that threshold is recorded as a skip, not a completion. Unknown duration has no percentage-based completion shortcut.

## State-machine tests

Test every transition above, repeated/duplicate callbacks, cancellation, stale callbacks, empty queues, shuffle determinism, repeat modes, unknown duration, seek bounds, engine failure, audio focus loss/gain, interruption, resume persistence, and cross-surface state projection.
