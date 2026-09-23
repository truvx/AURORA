# AURORA Typography

Status: final v1 specification, accepted by ADR-015.

## Family and weights

Montserrat, in three weights: Light 300, Regular 400, Black 900. Nothing asks for a weight
outside those three, because a weight the family does not ship is synthesised, and a
synthesised semibold is a smeared Regular.

| Role | Weight | Size / line height | Use |
| --- | ---: | --- | --- |
| Display | Black 900 | 40 / 46 | Screen-level hero titles and large artwork context. |
| Heading 1 | Black 900 | 30 / 36 | Page titles. |
| Heading 2 | Black 900 | 22 / 28 | Section titles and album/playlist titles. |
| Section label | Black 900 | 14 / 20, tracked 1.2 | Small-caps labels over a shelf of content. |
| Track title (player) | Regular 400 | 30 / 36 | A song's name where it is the subject of the screen. |
| Track title | Regular 400 | 22 / 28 | A song's name in a row, a card or the queue. |
| Track artist | Light 300 | 16 / 24 | Whoever made it, under the name. |
| Track artist (compact) | Regular 400 | 14 / 20 | The same, where 16 would crowd the title. Light thins out below 16. |
| Lyrics | Black 900 | per surface | Reserved; AURORA has no lyrics feature yet. |
| Body | Regular 400 | 16 / 24 | Descriptions, dialogs, and readable content. |
| Label | Regular 400 | 14 / 20 | Actions, tabs, filters, and form labels. |
| Metadata | Regular 400 | 12 / 16 | Source, quality, and secondary facts. |
| Numeric / timing | Regular 400 | 14 / 20; player values 32 / 36 | Duration, position, counters, and queue order. Use tabular numerals. |

Letter spacing is size-specific rather than `0` across the scale: display and headline tighten, label and caption open up, and the small-caps section label is tracked wide. Do not scale font size with viewport width. Platform text scaling and browser zoom must be honored; long metadata wraps or truncates with an accessible full value and never changes a fixed control's dimensions unexpectedly.

## Rendering rules

- Prefer the Montserrat variable font with only used weights - Light 300, Regular 400, Black 900 - then fall back to platform sans-serif.
- Use tabular numerals for time and counts; do not rely on proportional digits for aligned controls.
- Keep body measure readable, support at least 1.5 text scaling on Android test configurations, and support browser zoom without horizontal scrolling.
- Do not place important text directly over unprotected artwork. Apply contrast protection from the Liquid Glass design system.
- Use semantic roles and platform-native text rendering; do not encode text in artwork.
