# AURORA motion specification

Status: final v1 motion contract. See `FINAL_ARCHITECTURE_BASELINE.md` for the cross-cutting accessibility and performance gates.

## Motion principles

Motion explains ownership, hierarchy, cause/effect, and continuity. It is not ambient decoration by default. One dominant movement per interaction is preferred. Entering content may be slower than exiting transient content; controls respond quickly; immersive transitions may take longer only when the relationship is meaningful.

## Duration and easing tokens

| Category | Duration | Curve |
|---|---:|---|
| instant feedback | 80 ms | linear/quick |
| quick control | 140 ms | standard |
| standard surface/content | 220 ms | decelerate in, accelerate out |
| emphasized transformation | 360 ms | emphasized/spring |
| ambient artwork | 600–1200 ms | low-amplitude, interruptible |

Use springs for connected surfaces and drag release; use time-based easing for opacity, disclosure, and transient menus. Exits normally target roughly 60–70% of matching enter duration when context permits. No per-component arbitrary curves without a reason, and correctness never depends on `animationEnd`/`transitionend`.

## Spring families

Define named platform-mapped families: `responsive` for controls, `standard` for surfaces, `soft` for artwork/ambient, `settle` for drag release. Stiffness/damping values are implementation parameters to calibrate on Android/web; they must be measured for overshoot, interruption, and frame cost rather than copied blindly between platforms.

## Behavior by pattern

| Pattern | Motion |
|---|---|
| Page transition | preserve hierarchy; short content fade/translate; avoid full-screen parallax |
| Shared element | interpolate bounds/scale/clip from source to destination; keep identity visible |
| Album transition | artwork continuity into player; title/metadata reflow after art settles |
| Mini-player expansion | drag-linked height/position/scale; release settles to nearest state |
| Now Playing | artwork and controls reveal in sequence, never all at once |
| Bottom sheet | spring from gesture anchor with dim/scrim; velocity-aware but bounded |
| Dialog/menu | quick opacity + small scale/translation; focus enters after visible state |
| Search | field expands into destination; preserve query/focus and support cancel |
| Queue | list remains spatially stable; item follows drag, siblings make room |
| Playlist | card/header continuity; rows load progressively without jumping |
| Artwork | crossfade/scale; ambient palette interpolates slowly and clamps chroma |
| Scrub/progress | thumb tracks gesture directly; track fill follows confirmed position |
| Loading/skeleton | restrained opacity shimmer only if helpful; static fallback for reduced motion |
| Download | button morphs to progress/status; progress is truthful and cancellable |
| AI result | staged insertion/fade/slide with provenance; never animate all rows simultaneously |
| Error/success | concise status transition; preserve recovery action and focus |

## Gesture model

Gesture interaction is direct manipulation: track pointer/finger position, clamp to bounds, use velocity only at release, and settle to a deterministic target. Define thresholds in relation to component size (not arbitrary screen pixels). Cancel/reverse returns to the nearest valid state without a jump. Drag reorder provides a non-drag accessible alternative.

## Performance

Prefer transforms, opacity, clip, and compositor-friendly properties. Avoid layout reads/writes in a frame loop, allocations per frame, unbounded blur, or animation-triggered I/O. Keep high-frequency progress isolated from broad UI state. Measure frame time on low-end Android and mobile web.

## Accessibility/reduced motion

When reduced motion is enabled, remove travel, parallax, bounce, shared-element flight, and animated blur. Use immediate or short opacity/state changes, preserve focus, and announce meaningful changes. Reduced transparency replaces backdrop blur with opaque/tinted surfaces. Users must still understand hierarchy and progress.

## Cancellation and lifecycle

Animations cancel on disposal, route change, backgrounding, or new gesture. Effects are idempotent where possible. A transition must not leave an invisible focus target, stuck scrim, stale artwork, or inaccurate player state.
