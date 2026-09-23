# ADR-017: AURORA renders at 60Hz on high-refresh displays

- Status: Accepted
- Date: 2026-09-23
- Applies to: the Android client

## Context

The beta was measured on a Nothing Phone (A015) whose panel runs at 120Hz. Android treats
the app as a "normal" frame rate client, so the system drives it against a 60Hz deadline
(16.7ms) rather than a 120Hz one (8.3ms).

Measured with `dumpsys gfxinfo` over ordinary use - the Home feed, Library scrolling, the
player, and the floating chrome that draws glass over a captured backdrop:

| Percentile | Frame time |
| --- | --- |
| Median | 10ms |
| 90th | 12ms |
| 95th | 14ms |
| 99th | 16ms |

Against the 60Hz deadline the system reported 0.09% jank. Against a 120Hz deadline, more
than half of all frames are late.

## Decision

AURORA does not request a higher frame rate. It renders at 60Hz and does not call
`setRequestedFrameRate` or its equivalents to opt into the panel's full refresh.

## Why

At 120Hz every frame has 8.3ms. The median frame already costs 10ms, and the 90th costs
12ms. Asking for 120Hz would not make the app faster; it would move the deadline below
what the app currently achieves and convert a smooth 60Hz experience into a consistently
late 120Hz one. Jank at 120Hz is worse than no 120Hz.

The cost is not incidental. Each glass surface samples a captured backdrop and runs a
blur, and the floating chrome does it over content that is itself moving. That work is
what the interface is; making it twice as frequent is a real doubling, not a rounding
error.

## What would change this

Revisit when the median frame time on a representative device is comfortably under 5ms
with the chrome on screen - roughly half its current cost. That is a measurement, not an
estimate: re-run the same `dumpsys gfxinfo` pass, on a device with a high-refresh panel,
with glass visible and scrolling.

A partial opt-in - high refresh for a single interaction such as a scrub drag, 60Hz
elsewhere - is not taken up here. It adds a second frame-rate regime to reason about for
an effect nobody has asked for, and the same measurement gates it.

## Consequences

- Animation and gesture specs continue to target the 16.7ms budget in
  `docs/PERFORMANCE_BUDGET.md`.
- Scrolling and motion remain smooth as shipped; what is given up is the extra smoothness
  the panel could show, not correctness.
- Nothing in the code requests a frame rate, so this decision is the absence of a call.
  `docs/PERFORMANCE.md` records it so that absence is deliberate rather than an oversight.
