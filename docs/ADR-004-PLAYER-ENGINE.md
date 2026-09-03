# ADR-004: One canonical playback coordinator

## Status

Accepted as foundation; the final state contract is PLAYER_STATE_MACHINE.md.

## Context

Mini-player, Now Playing, queue, notification, lock screen, external controls, Android lifecycle, and web browser controls must agree about playback.

## Decision

Use one application-level `PlayerCoordinator` with a platform-specific `PlayerEngine` adapter. Canonical immutable `PlayerState` and `QueueState` are projected to every surface. Typed commands/events pass through a testable state machine.

Android may use Media3/ExoPlayer where legitimately supported; web uses browser media APIs. Media session, audio focus, interruptions, headset/Bluetooth, autoplay, and visibility are adapters.

## Alternatives considered

- One player per screen — rejected because state drift and resource contention are unavoidable.
- Direct UI-to-player SDK calls — rejected because it makes lifecycle and testing brittle.
- Platform-specific independent domain models — rejected because Android/web behavior would diverge.

## Consequences

The coordinator is a critical shared boundary and must be lifecycle-safe, serialized, and well-tested. Platform adapters remain replaceable.

## Future migration

A remote/synchronized player can be introduced as another engine or coordinator mode only with explicit state ownership and conflict semantics.
