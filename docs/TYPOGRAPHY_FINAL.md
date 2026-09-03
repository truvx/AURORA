# AURORA Typography

Status: final v1 specification, accepted by ADR-015.

## Family and weights

| Role | Family | Weight | Size / line height | Use |
| --- | --- | ---: | --- | --- |
| Display | Inter | 700-800 | 40 / 46 | Screen-level hero titles and large artwork context. |
| Heading 1 | Inter | 700 | 30 / 36 | Page titles and prominent Now Playing metadata. |
| Heading 2 | Inter | 600-700 | 22 / 28 | Section titles and album/playlist titles. |
| Body | Inter | 400-500 | 16 / 24 | Descriptions, dialogs, and readable content. |
| Label | Inter | 600 | 14 / 20 | Actions, tabs, filters, and form labels. |
| Metadata | Inter | 400-500 | 12 / 16 | Artist, album, source, quality, and secondary facts. |
| Numeric / timing | Inter | 500-600 | 14 / 20; player values 32 / 36 | Duration, position, counters, and queue order. Use tabular numerals. |

Letter spacing is `0` for all tokens. Do not scale font size with viewport width. Platform text scaling and browser zoom must be honored; long metadata wraps or truncates with an accessible full value and never changes a fixed control's dimensions unexpectedly.

## Rendering rules

- Prefer Inter variable font with only used weights, then fall back to platform sans-serif.
- Use tabular numerals for time and counts; do not rely on proportional digits for aligned controls.
- Keep body measure readable, support at least 1.5 text scaling on Android test configurations, and support browser zoom without horizontal scrolling.
- Do not place important text directly over unprotected artwork. Apply contrast protection from the Liquid Glass design system.
- Use semantic roles and platform-native text rendering; do not encode text in artwork.
