# ADR-015: Typography and Iconography

- Status: Accepted
- Date: 2026-09-03

## Decision

AURORA uses Inter as its primary type family across Android and Web. The family is used as a variable or appropriately subsetted font where platform support permits, with system sans-serif fallback. One primary family keeps the bundle and rendering behavior predictable while providing strong display, metadata, numeric, and multilingual coverage.

Material Symbols Outlined is the primary icon family. It is used through platform-supported vector/font delivery rather than copied Apple assets or hand-drawn replacements. Icons are semantic, not decorative; icon-only controls require accessible names and tooltips where appropriate.

## Alternatives considered

| Candidate | Decision | Reason |
| --- | --- | --- |
| Inter | Chosen | Open Font License, broad language coverage, strong readability, variable weights, and reliable Android/Web rendering. |
| Manrope | Not primary | Strong display voice, but an extra family would increase delivery and coverage cost without a clear product benefit. |
| Plus Jakarta Sans | Not primary | Good UI family, but Inter has a stronger existing system fit and broad ecosystem support here. |
| Geist | Not primary | Good web display family, but platform parity and language coverage are less predictable for the initial cross-platform baseline. |
| Apple proprietary fonts/assets | Rejected | Licensing and platform-copy concerns; not required for the AURORA identity. |
| Material Symbols | Chosen | Apache 2.0, broad action coverage, Android/Web availability, and consistent outlined optical style. |
| Lucide | Accepted fallback | Permissive icon set suitable for a future web-only or missing-symbol case, but not the primary cross-platform set. |

## Consequences

Font files are not added in the foundation phase. At implementation, use licensed Inter assets or the approved platform delivery route, subset only after language requirements are known, reserve layout space, and test fallback rendering. Do not use emoji as UI icons. If a Material Symbol is unavailable, use a named Lucide fallback or text label; never invent a visually similar Apple asset.

Confidence: high for the license and cross-platform role fit; medium for final glyph subset and language coverage until the product's actual language set is measured.

## Sources

- [Inter project and license](https://github.com/rsms/inter)
- [Material Symbols guide](https://developers.google.com/fonts/docs/material_symbols)
- [Material Symbols repository and license](https://github.com/google/material-design-icons)
- [Lucide license](https://github.com/lucide-icons/lucide/blob/main/LICENSE)
