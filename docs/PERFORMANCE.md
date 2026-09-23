# AURORA performance requirements

## Targets

Prioritize smooth scrolling and gestures, low recomposition/render waste, fast startup, efficient artwork loading, bounded memory, off-main-thread work, efficient database access, reliable buffering, and responsive AI result lists.

## Hot paths

Measure large artwork, music lists, motion/blur, waveform/scrubber, AI results, queue reorder, library scans, metadata extraction, and audio buffering. Prefer lazy/paginated data, downsampled/cached artwork, stable parameters, immutable state, cancellation, bounded concurrency, and transforms/opacity over layout work.

## Frame rate on high-refresh displays

AURORA renders at 60Hz and does not opt into a higher refresh rate, even on a 120Hz panel.
Measured on a Nothing Phone (A015): median frame 10ms, 90th 12ms, 99th 16ms, and 0.09%
jank against the 60Hz deadline. A 120Hz deadline is 8.3ms, which more than half of all
frames already exceed, so requesting it would make the app late rather than smooth. See
`docs/ADR-017-FRAME-RATE.md`, which also records what would justify revisiting it.

## Evidence

Profile before optimizing. Track startup, frame time/jank, memory, scan throughput, artwork decode cost, queue operation latency, and playback buffering. Keep a low-power/reduced-effects path. Do not trade correctness, accessibility, or truthful state for a benchmark.
