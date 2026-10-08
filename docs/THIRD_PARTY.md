# Third-party code in AURORA

AURORA is GPL-3.0. Code taken from other projects is listed here with its origin and
licence, as the licence requires and so that the next person can find the upstream when
it changes.

## Source Sans Pro

- Source: Adobe, designed by Paul D. Hunt (https://github.com/adobe-fonts/source-sans)
- Licence: SIL Open Font License 1.1, Reserved Font Name "Source"
- Shipped unmodified: `android/app/src/main/res/font/source_sans_pro_regular.ttf` and
  `source_sans_pro_bold.ttf` (v1.036). Licence text in `res/raw/source_sans_pro_ofl.txt`
  and `docs/licenses/SourceSansPro-OFL.txt`.

## NomaTune

- Source: https://github.com/Shahdullah/NomaTune
- Licence: GPL-3.0-only
- Compatible with AURORA's GPL-3.0: yes, which is why AURORA is GPL-3.0.

Ported from it:

| AURORA | NomaTune origin | Notes |
|---|---|---|
| `android/.../ui/screens/NowPlayingScreen.kt` (Immersive layout, colour tones, `codecLabel`) | `ui/player/Player.kt`: `V7PlayerBackdrop`, `V7BackdropPalette`, `v7BackdropTone`; `ui/player/PlayerComponents.kt`: `V8PlayerControlsContent`, `V8MetadataActions`, `V8PlaybackProgress`, `V8QualityChip`, `codecLabel`, `V8TransportControls`, `V8VolumeControls` | Rewritten against `PlayerCoordinator`. The blurred backdrop layer is left out: V7's floor gradient is opaque at every stop, so it never showed. |
| `android/.../ui/components/PlayerScrubber.kt` (flat track and time row) | `ui/player/PlayerComponents.kt`: `V8PlaybackProgress`, `V8FlatSlider` | Drawing only; AURORA's own gestures and screen-reader node are kept. |

Every file derived from NomaTune carries a header naming the upstream file and its
licence. A port is not a fork: where AURORA's architecture differs - one
`PlayerCoordinator` rather than a service plus Hilt graph, Room schemas that are not
theirs - the adapted code says so rather than pretending to be a copy.
