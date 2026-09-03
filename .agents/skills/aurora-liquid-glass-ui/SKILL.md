---
name: aurora-liquid-glass-ui
description: Design or review AURORA's original Liquid Glass-inspired UI system, Compose/web surfaces, adaptive blur, contrast, typography, and accessibility.
---

# AURORA Liquid Glass UI

Use for design system, components, typography, color, responsive layout, glass surfaces, artwork backdrops, accessibility, or UI review. Read `docs/DESIGN_SYSTEM.md` and use the installed `ui-ux-pro-max` skill for visual decisions.

## Visual direction

AURORA uses an original translucent material system: layered depth, adaptive blur, soft highlights, dynamic album-art lighting, large typography, floating controls, and minimal clutter. It must not copy Apple's source code, exact assets, or proprietary implementation. Avoid a generic Material-only look, flat card grids, excessive borders/shadows/gradients, and decorative motion without hierarchy.

## Material rules

- Use semantic surface tokens and an explicit material hierarchy.
- Maintain readable foreground/background contrast after artwork sampling, blur, scrims, and dynamic lighting.
- Provide a reduced-effects path for low-power devices and reduced-transparency preferences.
- Avoid blur on large scrolling content when it causes frame drops; use sampled colors or static scrims where appropriate.
- Keep touch targets, focus order, semantics, keyboard behavior, and screen-reader labels intact.

## Component guidance

Components accept state and callbacks; business logic remains in state holders/use cases. Prefer slots for variable content and stable immutable parameters. Test loading, empty, error, disabled, selected, focused, large text, dark/light, and reduced-motion/reduced-transparency states.

## Quality bar

Every visual change should be checked with the UI/UX Pro Max skill, platform accessibility semantics, responsive breakpoints, and actual runtime screenshots when the app exists. Do not create placeholder screens or fake playback in the foundation phase.
