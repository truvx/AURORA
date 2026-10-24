# AURORA Typography

Status: final v1 specification, accepted by ADR-015.

## Family and weights

Android: Source Sans Pro, Regular 400 and Bold 700 (ADR-015 update, 2026-10-08), with
tabular figures (`tnum`) switched on for the numeric roles because its figures are
proportional. Web: still Space Mono, described below, until it is switched.

Space Mono, in the two weights it ships: Regular 400 and Bold 700. Nothing asks for a
weight outside those two, because a weight the family does not ship is synthesised, and a
synthesised medium is a smeared Regular - so a role that wants a 500 takes 400.

One family for the whole interface. The register is a record sleeve's back cover rather
than a magazine's front - catalogue numbers, durations, track listings - and hierarchy
comes from size, weight and colour, never from a second typeface.

Sizes sit a step below the previous scale because Space Mono sets about 15% wider than a
proportional sans at the same point size. Holding the old sizes would have pushed every
title into an ellipsis; stepping down keeps the same number of characters on a line, which
is what the layouts were built around.

| Role | Weight | Size / line height | Use |
| --- | ---: | --- | --- |
| Display | Bold 700 | 34 / 42 | Screen-level hero titles and large artwork context. |
| Heading 1 | Bold 700 | 26 / 34 | Page titles. |
| Heading 2 | Bold 700 | 20 / 28 | Section titles and album/playlist titles. |
| Section label | Bold 700 | 12 / 18, tracked 0.9 | Small-caps labels over a shelf of content. |
| Track title (player) | Regular 400 | 26 / 34 | A song's name where it is the subject of the screen. |
| Track title | Regular 400 | 18 / 26 | A song's name in a row, a card or the queue. |
| Track artist | Regular 400 | 15 / 22 | Whoever made it, under the name. |
| Track artist (compact) | Regular 400 | 13 / 18 | The same, where 15 would crowd the title. |
| Lyrics | Regular 400, Bold 700 when sung | 26 / 34 | The words on the lyrics sheet. The sung line is Bold and lit; weight and colour together, never colour alone. |
| Body | Regular 400 | 16 / 24 | Descriptions, dialogs, and readable content. |
| Label | Regular 400 | 14 / 20 | Actions, tabs, filters, and form labels. |
| Metadata | Regular 400 | 12 / 16 | Source, quality, and secondary facts. |
| Numeric / timing | Regular 400 | 14 / 20; player values 30 / 36 | Duration, position, counters, and queue order. Monospaced, so already tabular. |

Uppercase is used selectively and only where it means something: section headers, category
and metadata labels. It is never applied to a song, an album or a person's name, because
those are how they are written.

Letter spacing stays near zero: a monospaced face is already wide, and tightening it closes the counters. Display and headline take a touch of negative tracking, and only the small-caps section label and caption open up - capitals at 12sp need the air. Do not scale font size with viewport width. Platform text scaling and browser zoom must be honored; long metadata wraps or truncates with an accessible full value and never changes a fixed control's dimensions unexpectedly.

## Rendering rules

- Ship Space Mono at the two weights it has - Regular 400 and Bold 700 - then fall back to the platform monospace, so the measure barely moves if the face fails to load.
- Numerals are tabular by construction in a monospaced family, so timing values do not shift the layout as they tick. Do not substitute a proportional face for numeric roles.
- Keep body measure readable, support at least 1.5 text scaling on Android test configurations, and support browser zoom without horizontal scrolling.
- Do not place important text directly over unprotected artwork. Apply contrast protection from the Liquid Glass design system.
- Define the family once, globally - the Android theme's Material typography and the web's root `font-family` and form-control reset - rather than declaring it per component.
- Use semantic roles and platform-native text rendering; do not encode text in artwork.
