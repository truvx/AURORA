# AURORA master design system

Status: final v1 specification. This is an original Liquid Glass-inspired system; it does not copy Apple's implementation, assets, or proprietary icons.

## Design thesis

AURORA is a calm, premium listening instrument: an original translucent material language shaped by album art, with strong typography, quiet depth, and tactile continuity. It borrows principles—not code, assets, or an exact visual reproduction—from current Liquid Glass-style interfaces. Usability, contrast, platform conventions, and performance outrank spectacle.

## Material architecture

The visual stack is: stable canvas → optional artwork atmosphere → contrast-protecting scrim → glass surface → soft highlight → content/control layer. Primary surfaces are translucent only when the backdrop is stable and measurable. Scrolling lists use tint or opaque surfaces when blur would impair frame time. Reduced transparency and low-power paths use opaque semantic surfaces with the same hierarchy.

Material levels:

1. `Canvas`: base background and artwork-safe tint.
2. `Glass Primary`: main content grouping.
3. `Glass Secondary`: quiet, subordinate grouping.
4. `Glass Elevated`: player, floating sheet, or dialog.
5. `Opaque Fallback`: accessible/performance variant.

## Color, type, spacing, shape

Use `docs/DESIGN_TOKENS.md` as the source of token names and baseline values. Typography is large and confident but stays within dynamic text scaling. Spacing uses a 4-unit rhythm with generous section separation. Rounded shapes are expressive at surfaces and restrained in dense rows. Avoid pills, gradients, borders, and shadows as defaults. The baseline is not a blanket “glass everywhere” rule: navigation and high-value controls may use the material most strongly, while dense content can use quiet tint or opaque surfaces.

## Artwork-driven atmosphere

```text
artwork
  → decode/downsample
  → robust palette extraction
  → luminance/saturation/complexity analysis
  → contrast-safe accent candidates
  → scrim + surface recipe
  → semantic theme
```

Extract multiple dominant colors with spatial weighting and discard colors that are too close to the intended foreground, excessively saturated, or likely to compromise skin-tone/artwork integrity. Choose a light/dark text mode based on measured composite contrast, then raise scrim/opacity or use neutral/opaque fallback until all required content passes contrast. Black-and-white art may generate a neutral theme; bright, dark, or complex art may cause a stronger scrim. Cache derived palettes by artwork identity and algorithm version.

Dynamic theming never changes error/warning/success meaning, focus visibility, or accessible labels. It is optional and can be disabled.

## Component language

`GlassSurface`, `GlassCard`, `GlassButton`, `GlassIconButton`, `ArtworkCard`, `TrackRow`, `AlbumRow`, `ArtistRow`, `MiniPlayer`, `PlayerControls`, `ProgressScrubber`, `QueueRow`, `PlaylistCard`, `SearchField`, `BottomGlassSheet`, `GlassDialog`, `QualitySelector`, `NormalizationSelector`, `AICommandBar`, `RecommendationCard`, `DownloadButton`, `HapticFeedback`, and `AnimatedArtwork` are specified in `docs/COMPONENT_SYSTEM.md`. Each is state-driven, slot-friendly, semantically labeled, and platform-adapted. None owns domain logic.

## Screen patterns

- Home is a curated vertical narrative, not a wall of carousels. Lead with one primary resume/discovery action, then adapt sections to real data. Any horizontally scrolling section exposes an accessible alternative, pauses/stops motion on focus, and does not become the only path to content.
- Search is an immediate universal entry point with scoped Songs, Albums, Artists, Playlists, YouTube, Local, and AI modes.
- Library privileges dense, scan-friendly rows and intentional empty/import states over decorative cards.
- Now Playing is an immersive but readable player canvas with large artwork, clear title/artist, progress, primary controls, queue access, and technical disclosure.
- Queue is a task surface: stable order, current-item emphasis, accessible reorder/remove, and no competing decorative content.
- AI uses a clear command bar and recommendation provenance. It never impersonates a human or hides provider limitations.
- Settings group playback/audio, appearance/accessibility, AI/accounts, storage/privacy, notifications, and about. Every provider-dependent setting states its scope.

## Controls and states

Buttons have one primary action and visible focus/pressed/disabled states. Lists preserve scanability and stable geometry. Sheets trap and restore focus; dialogs are reserved for meaningful decisions. Sliders expose a value and keyboard increments. Toggles communicate on/off textually or semantically. Loading, empty, offline, unavailable, error, and recovery states are designed—not placeholders.

## Navigation

Android uses platform-appropriate bottom/rail navigation and modal sheets/full-screen destinations according to width; web uses responsive nav/rail/sidebar and URL-addressable pages. Information architecture stays aligned, while gesture/keyboard conventions remain native. Playback state persists across navigation.

## Interaction hierarchy

Primary listening actions are play/resume, pause, seek, next/previous, queue, favorite, and add. Discovery actions are search, related, recommendation, and AI. Configuration stays secondary. Provider capabilities determine which controls exist. A missing capability is not hidden behind a dead button.

## Accessibility contract

Readable contrast is tested after compositing, not on token swatches. Android targets are at least 48 × 48 dp; web targets are evaluated against WCAG 2.5.8 with comfortable pointer/keyboard affordances. Text scaling, screen-reader semantics, focus, keyboard reorder, reduced motion/transparency, non-color status, and haptic fallback are required. See `docs/ACCESSIBILITY_SPEC.md`.

## Motion and tactility

Use `docs/AURORA_MOTION_SPEC.md` and `docs/AURORA_HAPTIC_SPEC.md`. Motion preserves relationships: album art becomes player art; mini-player expands into Now Playing; queue movement follows the dragged item. Haptics are sparse semantic confirmation, never a frame loop.

## Platform translation

Android Compose uses stable immutable parameters, slot APIs, lifecycle-aware effects, and measured blur/animation. Web uses CSS/compositor-friendly transitions, responsive layout, browser accessibility, and isolated high-frequency player updates. Tokens and state meanings remain shared; rendering mechanics do not.

## Validation checklist

Before accepting a design, check hierarchy, typography, contrast, spacing, ergonomic reach, focus, responsive behavior, artwork edge cases, excessive glass/gradients/shadows, animation load, reduced modes, and graceful provider/AI failure. Use UI/UX Pro Max as a design review tool, then validate against these AURORA constraints and actual platform behavior.
