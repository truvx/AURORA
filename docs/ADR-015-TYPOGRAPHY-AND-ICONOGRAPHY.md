# ADR-015: Typography and Iconography

- Status: Accepted
- Date: 2026-09-03

## Decision

AURORA uses Space Mono as its primary type family across Android and Web (raised from Montserrat, 2026-09-25, which was itself raised from Inter). It is licensed under the SIL Open Font License, which permits bundling; the licence ships with the app in `res/raw/space_mono_ofl.txt` and at `docs/licenses/SpaceMono-OFL.txt`. One primary family keeps the bundle and rendering behaviour predictable, and it is declared once per client - the Android theme's Material typography, the web's root `font-family` plus a form-control `font: inherit` reset - rather than per component.

The design is a music archive rather than a magazine: catalogue numbers, durations, track
listings. A monospaced face is what that reads like, and it brings tabular numerals for
free, so a running time does not shift the layout as it ticks.

Space Mono ships Regular 400 and Bold 700, and nothing between. Headings, album and
playlist titles and small-caps section labels are Bold; everything else - a song's name,
whoever made it, metadata, controls - is Regular, with hierarchy carried by size and
colour. A role that wants a 500 takes 400 rather than a synthesised weight, which is a
smeared Regular.

Sizes step down about one notch from the previous Montserrat scale, because Space Mono
sets roughly 15% wider at the same point size and the layouts were built around a number
of characters per line, not a point size. Tracking stays near zero, opening only on small
capitals. Uppercase is used selectively - navigation categories, section headers, metadata
labels - and never on a song, album or person's name.

Material Symbols Outlined is the primary icon family. It is used through platform-supported vector/font delivery rather than copied Apple assets or hand-drawn replacements. Icons are semantic, not decorative; icon-only controls require accessible names and tooltips where appropriate.

## Alternatives considered

| Candidate | Decision | Reason |
| --- | --- | --- |
| Space Mono | Chosen | Open Font License, and a monospaced face that reads as a catalogue rather than an app - technical, editorial, and tabular by construction, which is what timings and track numbers want. |
| Montserrat | Superseded | Shipped a three-weight geometric sans; replaced because the product's voice is an archive, not a display poster. |
| Inter | Superseded | Was chosen for readability and coverage; never actually loaded on Android, where the app ran on the system sans until Montserrat shipped. |
| Manrope | Not primary | Strong display voice, but an extra family would increase delivery and coverage cost without a clear product benefit. |
| Plus Jakarta Sans | Not primary | Good UI family, but without the weight range this design leans on. |
| Geist | Not primary | Good web display family, but platform parity and language coverage are less predictable for the initial cross-platform baseline. |
| Apple proprietary fonts/assets | Rejected | Licensing and platform-copy concerns; not required for the AURORA identity. |
| Material Symbols | Chosen | Apache 2.0, broad action coverage, Android/Web availability, and consistent outlined optical style. |
| Lucide | Accepted fallback | Permissive icon set suitable for a future web-only or missing-symbol case, but not the primary cross-platform set. |

## Consequences

The font ships with the app: one variable file at `android/app/src/main/res/font/montserrat.ttf` (727 KB) and, on Web, self-hosted at build time by next/font so nothing is fetched from Google at runtime. Subset only after language requirements are known, reserve layout space, and test fallback rendering. Do not use emoji as UI icons. If a Material Symbol is unavailable, use a named Lucide fallback or text label; never invent a visually similar Apple asset.

Confidence: high for the license and cross-platform role fit; medium for final glyph subset and language coverage until the product's actual language set is measured.

## Sources

- [Inter project and license](https://github.com/rsms/inter)
- [Material Symbols guide](https://developers.google.com/fonts/docs/material_symbols)
- [Material Symbols repository and license](https://github.com/google/material-design-icons)
- [Lucide license](https://github.com/lucide-icons/lucide/blob/main/LICENSE)
