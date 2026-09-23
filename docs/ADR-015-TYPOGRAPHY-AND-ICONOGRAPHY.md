# ADR-015: Typography and Iconography

- Status: Accepted
- Date: 2026-09-03

## Decision

AURORA uses Montserrat as its primary type family across Android and Web (raised from Inter, 2026-09-23). It is licensed under the SIL Open Font License, which permits bundling; the licence ships with the app in `res/raw/montserrat_ofl.txt` and at `docs/licenses/Montserrat-OFL.txt`. The family is used as a variable or appropriately subsetted font where platform support permits, with system sans-serif fallback. One primary family keeps the bundle and rendering behavior predictable while providing strong display, metadata, numeric, and multilingual coverage.

Weights carry meaning rather than decoration. Headings, lyrics and small-caps section
labels are Black; a song's name is Regular; whoever made it is Light. Light is used at
16sp/16px and above - below that a 300 weight over glass starts to disappear, so compact
artist lines are Regular instead. The family ships exactly those three, so no role asks
for a weight that would have to be synthesised.

One variable file carries all three on both clients, which is one download and one
resource rather than three.


Material Symbols Outlined is the primary icon family. It is used through platform-supported vector/font delivery rather than copied Apple assets or hand-drawn replacements. Icons are semantic, not decorative; icon-only controls require accessible names and tooltips where appropriate.

## Alternatives considered

| Candidate | Decision | Reason |
| --- | --- | --- |
| Montserrat | Chosen | Open Font License, variable weights, and a geometric face whose Black is heavy enough to carry a heading against artwork while its Light stays legible under it. |
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
