---
name: aurora-motion
description: Define or review AURORA's unified motion language, Compose/web transitions, gesture physics, and reduced-motion behavior.
---

# AURORA motion

Use for animation, transitions, gesture interactions, bottom sheets, queue reordering, artwork transforms, or motion performance. Read `docs/ANIMATION_SYSTEM.md` and the relevant Compose animation/performance skills.

## Motion language

Use spring physics for physically connected surfaces and restrained easing for transient content. Entering content may be slower than exiting transient content. Animate hierarchy and continuity, not every element. Keep one dominant motion story per interaction.

Prioritize continuity for album-to-now-playing, mini-player expansion, queue presentation, search expansion, sheets, playlists, artwork transformations, context menus, toggles, sliders, reorder, download progress, and AI recommendations entering the queue.

## Performance and accessibility

- Animate transforms/opacity where possible; avoid layout thrash and per-frame allocations.
- Keep scrolling and scrubber gestures responsive; never run blocking work in animation callbacks.
- Cancel animations on lifecycle disposal and handle interruption without jumps.
- Respect platform reduced-motion settings. Reduced motion removes spatial travel and bounce while preserving state clarity.
- Avoid blur animation over large images unless measured acceptable.

## Review questions

What user relationship does this motion explain? What happens if interrupted, reversed, or backgrounded? Does the same canonical state drive all surfaces? Is the fallback legible with motion disabled?
