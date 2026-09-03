# AURORA component system

These are conceptual component contracts. No component implementation is created in this phase.

| Component | Purpose / variants | Required states and accessibility | Motion / haptics |
|---|---|---|---|
| `GlassSurface` | Base material: primary, secondary, elevated, opaque fallback | Contrast, semantics passthrough, reduced transparency | Fade/raise only when meaningful |
| `GlassCard` | Grouped content: artwork, recommendation, playlist | Loading/empty/disabled/focused; whole-card label and action clarity | Press scale restrained; tap |
| `GlassButton` | Primary/secondary/quiet/destructive | Label, role, disabled reason, keyboard focus | Press response; tap/success/error |
| `GlassIconButton` | Compact player/action control | Accessible name, tooltip, 48 dp target, selected/pressed | Press response; tap |
| `ArtworkCard` | Album/playlist/artist visual | Artwork alt text, missing-art fallback, loading | Artwork continuity; selection |
| `TrackRow` | Dense track display | Title/artist/source/quality semantics, overflow menu, unavailable state | Insert/remove; selection/favorite |
| `AlbumRow` | Album summary and action | Album/artist/year labels, partial metadata | Expand/open continuity; selection |
| `ArtistRow` | Artist summary | Name/artwork semantics and unavailable state | List insertion; selection |
| `MiniPlayer` | Persistent canonical playback projection | Playing/paused/buffering/error/unknown duration; action labels | Expansion continuity; tap/play |
| `PlayerControls` | Play/pause/seek/skip/repeat/shuffle | Disabled capability, labels, state announcement | Press, skip; tap/toggle |
| `ProgressScrubber` | Seek/progress | Range value, unknown duration, keyboard increments, no frame buzz | Gesture/snap; sparse scrub ticks |
| `QueueRow` | Queue item/edit | Position, current, unavailable, reorder alternative | Drag/reorder; start/move/drop |
| `PlaylistCard` | Playlist entry point | Empty/cover fallback/private status | Card → page; selection |
| `SearchField` | Search and AI prompt entry | Label, focus, clear, submit, loading/cancel | Expand/focus; tap/submit |
| `BottomGlassSheet` | Contextual or queue presentation | Focus trap/restore, dismiss semantics, height states | Spring/drag; open/dismiss |
| `GlassDialog` | Confirmation/error decision | Modal semantics, explicit consequence, keyboard escape rules | Short scale/fade; success/error |
| `QualitySelector` | Preferred/effective/available quality | Provider/track caveat and unknown state | Selection transition; selection |
| `NormalizationSelector` | Off/Quiet/Normal/Loud | DSP disclosure and provider-managed state | Selection; selection |
| `AICommandBar` | Natural-language discovery/action | Privacy context, connected state, cancel/error | Reveal/send; submit |
| `RecommendationCard` | Candidate explanation and action | Provenance, availability, reason text, Play All semantics | Result insertion; add/play |
| `DownloadButton` | Legitimate local import/download state | Streaming/local/imported/unavailable states; no YouTube download | Progress morph; completion/error |
| `HapticFeedback` | Semantic intent bridge | No visible dependency; no-op fallback | Central policy only |
| `AnimatedArtwork` | Artwork atmosphere/transition | Alt text, static fallback, reduced motion | Transform/crossfade only |

## Shared API principles

Components accept immutable view state and callbacks; they do not own domain state or perform side effects during render. Variable regions use slots. Stable keys identify list content. Component states must include loading, empty, error/recovery, offline, unavailable capability, focused, selected, disabled, and large-text behavior where relevant.

## Naming and platform adaptation

Names describe semantics, not framework implementation. Android and web may use platform-native primitives while preserving token names, states, accessibility outcome, and motion intent. A platform-specific component is allowed when interaction conventions differ; duplicate business logic is not.
