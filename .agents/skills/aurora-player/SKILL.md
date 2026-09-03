---
name: aurora-player
description: Design or review AURORA's centralized playback engine, queue state machine, media session integration, interruptions, and canonical playback state.
---

# AURORA player

Use for playback commands, queue behavior, Media3 integration, notifications, lock-screen controls, audio focus, background playback, resume state, and player-related UI. Read `docs/PLAYER_ARCHITECTURE.md` first.

## Single canonical owner

There is exactly one playback coordinator per application process. Screens never create independent players. Mini-player, now-playing, queue, notification, media session, lock screen, headset/Bluetooth actions, and web controls consume the same canonical state.

Separate immutable state into:

- `PlaybackState`: status, current track, position, duration, buffering, error, volume, and capability-aware quality/normalization state.
- `QueueState`: ordered items, current index, shuffle mapping, repeat mode, and pending edits.
- `PlayerCommand`: play, pause, seek, next, previous, replay, add/remove/reorder, set shuffle/repeat/volume, and reload.
- `PlayerEffect`: user-safe messages, haptic intents, and navigation requests; never use effects as durable state.

Commands are serialized, cancellation-aware, and reduced through a testable state machine. UI events do not mutate state directly.

## Android direction

Use Kotlin and AndroidX Media3/ExoPlayer where appropriate. Put the player engine behind an application-owned interface. Keep Media3 types in the infrastructure adapter. Integrate a media session, notification controls, audio focus, becoming-noisy/headphone handling, Bluetooth route changes, interruptions, and lifecycle-safe foreground/background behavior.

## Invariants

- A queue index always points to an existing item or an explicit empty state.
- Shuffle changes traversal order without losing stable queue identity.
- Repeat-one does not advance; repeat-all wraps; off stops at the end.
- Seek and position updates are clamped to known duration and report unknown duration honestly.
- Playback errors are recoverable when the provider says they are; do not silently skip without recording the transition.
- Resume positions are scoped to a stable track identity and provider/source, not title text alone.

## Forbidden

No player creation in composables, no polling loop on the UI thread, no blocking I/O in commands, no duplicate queue state per screen, and no fake “playing” state before the engine confirms it.
