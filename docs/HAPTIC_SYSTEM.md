# AURORA haptic system

Status: final v1 specification. `HapticEngine` is the only haptic boundary.

## API

All haptics go through a centralized `HapticEngine` abstraction. UI sends semantic intents; the Android adapter maps them to available platform primitives. The web client may provide a no-op or browser-appropriate fallback and must not assume vibration support.

## Categories

Tap, selection, toggle, slider tick, scrub tick, drag start/move/drop, favorite, queue reorder, download complete, success, warning, error, and carefully justified navigation transition.

## Policy

Effects are subtle, non-blocking, capability-aware, cancellable, and rate-limited. There is no continuous vibration on scroll or every scrub frame. Use meaningful snap points, selection boundaries, and sparse ticks only. Respect reduced-feedback/user settings and degrade to a simpler one-shot/no-op when needed.

## Tests

Use a fake engine to verify semantic mapping, fallback, rate limiting, disabled settings, and lifecycle cancellation. Verify on a device without advanced haptics before claiming broad support.
