---
name: aurora-haptics
description: Design or review AURORA's centralized Android haptic abstraction, capability fallbacks, interaction categories, and non-annoying feedback policy.
---

# AURORA haptics

Use for Android tactile feedback, haptic intent mapping, device capability handling, or interaction review. Read `docs/HAPTIC_SYSTEM.md`.

## Central abstraction

All UI requests go through a `HapticEngine` interface. UI emits semantic intents (`Tap`, `Selection`, `Toggle`, `SliderTick`, `ScrubTick`, `DragStart`, `DragMove`, `DragDrop`, `Favorite`, `QueueReorder`, `DownloadComplete`, `Success`, `Warning`, `Error`, and justified navigation transitions); the platform implementation selects the available feedback primitive.

Keep haptic policy separate from visual state. It must be non-blocking, lifecycle-safe, capability-aware, and safe when no vibrator or advanced actuator exists. Unsupported patterns degrade to no-op or a simpler one-shot effect.

## Frequency rules

Never vibrate continuously on scroll or every scrub frame. Use discrete meaningful snap points, selection boundaries, or sparse scrub ticks with rate limiting. Do not fire haptics from recomposition; fire from user events or a centralized state transition dispatcher.

## Verification

Test intent mapping with fake engines, fallback behavior, rate limiting, disabled/reduced-feedback settings, and lifecycle cancellation. Verify on at least one device without advanced haptics before claiming support.
