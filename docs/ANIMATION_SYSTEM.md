# AURORA animation system

Status: final v1 specification. Motion tokens and accessibility behavior are locked here and in `AURORA_MOTION_SPEC.md`.

## Intent

Motion communicates hierarchy, continuity, and physical relationship. Use restrained spring physics for connected surfaces and chosen easing for transient content. Entering content may be slower than exiting transient content; avoid excessive bounce and simultaneous competing animations.

## Priority transitions

Album → now playing, mini-player → full player, queue presentation/reorder, search expansion, bottom sheets, playlist transitions, artwork transforms, context menus, toggles, sliders, download states, and AI recommendations entering the queue.

## Rules

Preserve transform continuity, support interruption/reversal, cancel with lifecycle, keep gesture physics coherent, and animate transforms/opacity where possible. Avoid layout thrash, per-frame allocations, and animated blur over large artwork unless measured safe. Scrolling and scrubbing remain responsive.

## Reduced motion

Respect platform/user preference. Remove spatial travel, bounce, parallax, and nonessential blur animation while preserving state changes, focus, and progress clarity. Reduced motion is a designed variant, not a no-op.

## Verification

Test state transitions and interruption in unit/UI tests. Use runtime profiling and screenshots/video for material visual changes once the app exists. Establish frame-time and jank budgets before optimizing effects.
