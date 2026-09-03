# AURORA accessibility specification

Status: final v1 contract. Accessibility is a release gate for both platform clients, not a visual polish pass.

## Non-negotiable outcomes

Every action, state, and error is perceivable without color, translucency, animation, sound, or haptics. Glass is a visual enhancement, never the only contrast or affordance mechanism.

## Interaction

- Minimum target: 48 × 48 dp on Android. On web, evaluate each target against WCAG 2.5.8 and applicable exceptions, with a comfortable pointer/keyboard target even where the minimum permits less.
- Maintain logical reading/focus order and visible focus indication.
- Provide keyboard operation for every web action, including named Move up/Move down or position-menu alternatives to drag.
- Keep hit areas larger than visible icon glyphs.
- Make disabled/unavailable capability states explain the unmet condition.

## Text and contrast

Support platform text scaling and browser zoom without clipping essential controls. Validate text/icon contrast against the final artwork, blur, scrim, and surface combination. Use semantic status colors with text/icon/pattern redundancy. Do not use opacity alone for disabled or secondary content when it harms readability.

## Semantics

Expose roles, names, values, ranges, selected/expanded/pressed/disabled states, progress, current track, queue position, errors, and live updates appropriately. Avoid announcing every playback position tick; announce meaningful state changes.

## Motion/transparency

Respect reduced-motion and reduced-transparency preferences. Reduced motion removes travel/parallax/bounce; reduced transparency replaces glass with opaque or higher-contrast surfaces. State change and progress remain clear.

## Haptics and alternate feedback

Haptics are supplemental. Unsupported, disabled, or reduced-feedback devices receive visual/audio/semantic feedback without blocking. No continuous scroll haptics.

## Media and artwork

Provide meaningful artwork alternatives (title/artist/album), avoid flashing or rapidly changing background effects, preserve captions/transcripts where available, and never make an inaccessible provider limitation look like an app failure.

## Verification

Test screen-reader semantics, large text, zoom, keyboard/focus, contrast over dark/light/artwork themes, reduced motion/transparency, color-blind-safe statuses, touch target geometry, error recovery, offline state, and non-drag reorder controls.
