# AURORA design system

Status: final v1 specification. `AURORA_MASTER_DESIGN_SYSTEM.md` and `FINAL_ARCHITECTURE_BASELINE.md` are the canonical design references.

## Direction

AURORA is an original Liquid Glass-inspired system: translucent layered surfaces, adaptive blur, soft highlights, artwork-derived ambient color, large type, floating controls, morphing transitions, and minimal clutter. It is not Apple's source code, assets, or exact recreation.

## Tokens

Define primitive tokens for color, type, spacing, radii, elevation, blur, opacity, and motion; map them to semantic tokens for canvas, glass, elevated glass, text, muted text, controls, focus, selection, warning, and error. Components consume semantic/component tokens, never raw colors sprinkled through code.

## Material hierarchy

Use a stable canvas, ambient artwork layer, readable scrim, primary glass surface, elevated/floating surface, and controls. Blur and lighting are adaptive effects, not required for meaning. Provide reduced-transparency and low-power fallbacks.

## Accessibility

Contrast must be checked after background artwork, blur, and scrim. Support large text, screen readers, keyboard/focus navigation on web, minimum touch targets, clear selected/disabled/error states, and reduced motion/transparency preferences. Never encode meaning by translucency or color alone.

## Component contract

Components receive immutable state and callbacks, use slots for variable content, expose semantics, and avoid owning domain logic. Required states include loading, empty, error, recovery, focused, selected, disabled, offline, and unavailable capability.

## Design workflow

Use `ui-ux-pro-max` for future visual exploration and review. Validate a design at Android and web breakpoints before implementation; keep the same semantic language while allowing platform-native interaction patterns.
