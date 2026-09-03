# AURORA performance requirements

## Targets

Prioritize smooth scrolling and gestures, low recomposition/render waste, fast startup, efficient artwork loading, bounded memory, off-main-thread work, efficient database access, reliable buffering, and responsive AI result lists.

## Hot paths

Measure large artwork, music lists, motion/blur, waveform/scrubber, AI results, queue reorder, library scans, metadata extraction, and audio buffering. Prefer lazy/paginated data, downsampled/cached artwork, stable parameters, immutable state, cancellation, bounded concurrency, and transforms/opacity over layout work.

## Evidence

Profile before optimizing. Track startup, frame time/jank, memory, scan throughput, artwork decode cost, queue operation latency, and playback buffering. Keep a low-power/reduced-effects path. Do not trade correctness, accessibility, or truthful state for a benchmark.
