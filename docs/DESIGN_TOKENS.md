# AURORA design tokens

Tokens are the stable language between design and later Android/web implementations. Values below are a starting specification and must be validated with real contrast tooling and representative artwork before production use. Components consume semantic tokens, never raw primitives.

## Color primitives

Use perceptual color (OKLCH or equivalent) where the platform supports it, with sRGB fallbacks. The palette is neutral-first so artwork can provide atmosphere without becoming the UI’s only source of meaning.

| Token | Light seed | Dark seed | Role |
|---|---:|---:|---|
| `color.neutral.0` | `#FFFFFF` | `#0E0E0F` | canvas |
| `color.neutral.10` | `#F6F6F8` | `#151517` | canvas tint |
| `color.neutral.20` | `#ECECEF` | `#1F1F22` | opaque fallback surface |
| `color.neutral.40` | `#D2D2D8` | `#3A3A3F` | muted border |
| `color.neutral.70` | `#5A5A63` | `#ABABB2` | secondary text |
| `color.neutral.90` | `#17171A` | `#F5F5F7` | primary text |
| `color.accent.blue` | `#2458D6` | `#6A98FF` | selection and progress only |
| `color.favorite` | `#D7263D` | `#F9555B` | liked heart |
| `color.orb.core / mid / rim / glow` | `#28A4FE` `#3D6FF8` `#2A3FC7` `#B5C3FC` | same | AI orb gradient |
| `color.semantic.success` | `#197A4A` | `#67D49A` | success |
| `color.semantic.warning` | `#8B5A00` | `#F2C66D` | warning |
| `color.semantic.error` | `#B3261E` | `#FF9F99` | error |

The palette is neutral by design (2026-09-22, from the approved reference designs): colour reaches the interface through album artwork refracting in the glass, not through the chrome. Two consequences worth stating, because both were got wrong first:

- **Dark-theme glass is a white tint at low alpha** (12% primary, 7% secondary, 18% elevated). These materials lighten what is behind them - the reference nav bar reads about 22% white over near-black content. A dark tint darkens instead, and never reads as glass.
- **The primary action is a solid pill** using the theme's strongest contrast (white on dark, near-black on light), as in the playlist reference. The reference app's blue carries white text at 3.45:1 and cannot be a button fill.

Pairs are asserted in `ColorContrastTest`, and text over glass across every possible artwork colour in `GlassContrastTest` (both Android).

## Semantic color tokens

```text
background.primary       stable application canvas
background.secondary     alternate section/canvas tint
background.artwork       sampled ambient layer, never text backdrop alone
surface.glass.primary    main translucent material
surface.glass.secondary  quiet grouped material
surface.glass.elevated   floating/player/sheet material
surface.opaque.fallback  reduced-transparency/low-power fallback
text.primary             highest-priority content
text.secondary           supporting content with validated contrast
text.tertiary            metadata only when still readable
text.inverse              content on strong accent
accent.primary           primary action/progress
accent.secondary         secondary/discovery accent
control.primary          main control fill/icon
control.on-primary       content on control
focus.ring               keyboard/accessibility focus
divider.subtle           structural separator, not primary grouping
player.background        now-playing canvas
player.control           main player controls
player.progress          progress track/fill
favorite                 liked state; always paired with a filled/outlined icon change
ai.orb.core|mid|rim|glow the assistant's orb, identical in both themes
status.success|warning|error  redundant semantic status colors
```

## Typography

Use a platform-appropriate humanist sans for UI and a display treatment only for short hero moments. Do not lock future implementation to an unlicensed font. The semantic scale is:

| Token | Suggested size/line height | Use |
|---|---:|---|
| `type.display` | 40/46 | rare hero/now-playing title |
| `type.headline` | 30/36 | screen title |
| `type.title` | 22/28 | section/card title |
| `type.body` | 16/24 | primary reading/action text |
| `type.label` | 14/20 | controls and metadata |
| `type.caption` | 12/16 | tertiary metadata only |

Weight and contrast carry hierarchy more reliably than opacity. Support dynamic type/text scaling and browser zoom without truncating essential controls.

## Spacing

Base unit 4: `space.1=4`, `space.2=8`, `space.3=12`, `space.4=16`, `space.5=20`, `space.6=24`, `space.8=32`, `space.10=40`, `space.12=48`, `space.16=64`. Use larger structural gaps between sections and tighter rhythm inside a row. Avoid arbitrary one-off values.

## Shape

`shape.control=12`, `shape.card=20`, `shape.sheet=28`, `shape.dialog=24`, `shape.artwork=16` (all dp/px-equivalent tokens, platform-adjusted). Full pills are reserved for compact controls/status, not every container. Artwork aspect ratio is preserved.

## Depth and glass

```text
depth.canvas       no shadow; stable color
depth.surface      low separation via tint + 1 px highlight
depth.elevated     modest blur/tint + soft low-alpha shadow
depth.floating     strongest separation, reserved for player/sheet/dialog
```

Glass recipes combine background tint, backdrop blur, a readable scrim, a soft top/edge highlight, and an optional restrained shadow. Blur levels: `none`, `subtle`, `surface`, `floating`; each has an opaque fallback. Blur is never a requirement for contrast or state.

## Opacity and borders

Use named opacity steps `opacity.tint.08/.12/.16/.24`, `opacity.highlight.20/.36/.56`, `opacity.scrim.20/.40/.64`, and `opacity.disabled.48` only when contrast remains valid. Borders are optional structural highlights, typically 1 px, and never stacked around every card.

## Motion tokens

`motion.duration.instant=80ms`, `quick=140ms`, `standard=220ms`, `emphasis=360ms`, `ambient=600ms+` only for nonessential atmosphere. Easing: `standard` for transitions, `decelerate` for entering, `accelerate` for exiting, `emphasized` for major surfaces. Springs use named stiffness/damping families in the motion spec, not arbitrary per-component tuning. Exits normally target roughly 60–70% of the matching enter duration when spatial context permits; correctness must never depend on `animationEnd`/`transitionend`.

## Haptic tokens

Semantic intents only: `tap`, `selection`, `toggle`, `scrubTick`, `sliderTick`, `dragStart`, `dragDrop`, `favorite`, `queueReorder`, `downloadComplete`, `success`, `warning`, `error`. The Android adapter maps these to device capabilities; web may no-op.

## Icon and touch tokens

Icons use 20 dp for compact metadata, 24 dp standard controls, 28–32 dp prominent player controls, and 40+ dp hero controls. Every interactive target is at least 48 × 48 dp on Android and a comfortable equivalent on web, regardless of glyph size. On web, evaluate targets against WCAG 2.5.8 and applicable exceptions rather than treating Android’s 48 dp as a universal CSS rule. Use one icon family with consistent optical weight and accessible labels.

## Themes

Light and dark themes map semantic tokens to different surfaces and text pairs. Dynamic artwork themes provide only ambient/surface accents after contrast analysis. If contrast, saturation, skin-tone preservation, or background complexity is unsafe, reduce chroma, increase scrim, use neutral surfaces, or use the opaque fallback. Theme generation never changes semantic meaning or status color requirements.
