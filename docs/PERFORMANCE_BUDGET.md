# AURORA performance budget

These are initial engineering budgets to validate against real devices and representative libraries. They are guardrails, not permission to remove required functionality. Record measurement method and device/browser matrix when implementation begins.

| Area | Initial target | Measurement |
|---|---|---|
| Android cold start to interactive | ≤ 2.0 s on a representative mid-tier device; establish baseline before optimization | startup tracing |
| Web cold start to interactive | ≤ 2.5 s on representative mobile 4G and desktop | browser performance trace |
| Frame time | 60 FPS path stays within 16.7 ms; higher refresh paths target device budget | frame/jank profiler |
| Scroll jank | no sustained jank in a 500-item library with lazy artwork | runtime profile |
| Recomposition/render waste | position ticks update only subscribed player surfaces; list rows remain stable | Compose/browser profiler |
| Artwork | decode/downsample off UI thread; reserve layout space; bounded cache | decode/memory trace |
| Memory | bounded artwork/audio/cache; no full-library or full-history load | heap snapshots |
| Database | paged library/search queries; no main-thread blocking; common local queries ≤ 100 ms warm target | query tracing |
| Search | local search interaction feedback ≤ 100 ms target; remote response has explicit loading state | interaction trace |
| Queue operations | reorder/add/remove remains responsive for large queues; domain operation ≤ 50 ms target | benchmark |
| AI latency | first useful response target ≤ 2 s when provider permits; bounded timeout and fallback | request tracing |
| Metadata scan | bounded concurrency, progress reporting, cancellable, no UI starvation | scan benchmark |
| Playback | play/pause/seek command acknowledgment is promptly reflected; buffering is truthful | media trace |

## Performance principles

Use stable immutable parameters, derived state, lazy lists, cache keys with source identity, downsampled artwork, bounded concurrency, cancellation, background I/O, and transform/opacity animation. Isolate high-frequency player clocks from broad screen recomposition. Prefer measured simplification over visual effects that harm frame time.

## Quality gates

Profile representative dark/light/artwork themes, large and small libraries, poor network, AI unavailable, reduced effects, low-end Android hardware, high-refresh displays, keyboard web usage, and background/foreground transitions. A performance regression must name the scenario, metric, baseline, and evidence.

## Measured results (2026-09-17)

Recorded per the rule above: method, device, and dataset travel with every number.

### Android domain operations

Measured by `QueuePerformanceTest` (JVM, no Android or IO) on a 1,000-item queue.

| Operation | Budget | Result |
|---|---|---|
| Queue add | 50 ms | within budget |
| Queue remove | 50 ms | within budget |
| Queue reorder (first to last) | 50 ms | within budget |
| Queue clear | 50 ms | within budget |
| Per-add scaling, head vs tail of 1,000 | no severe degradation | within budget |

Thresholds sit far above observed values on purpose. A timing assertion set close to its real
value becomes a CI flake, and a flaky budget gets deleted rather than fixed; these catch an
operation turning quadratic, not a few milliseconds of noise.

### Android database

Measured by `LibraryPagingTest` against a real in-memory SQLite database of 2,000 tracks on
the Pixel 8 Pro emulator (API 37).

| Query | Budget | Result |
|---|---|---|
| Paged library read (50 rows, offset 500), warm | 100 ms | within budget |
| Bounded search (limit 25), warm | 100 ms | within budget |

Paging is verified for correctness as well as speed: pages do not overlap or skip rows, and
the final page is short rather than empty.

### Web cold start

Navigation Timing on the built output, Chromium, desktop, over localhost.

| Metric | Result |
|---|---|
| First contentful paint | 220 ms |
| DOMContentLoaded | 54 ms |
| Load complete | 164 ms |
| Script transfer | 4 KB |
| Resource count | 18 |

**Not measured:** the budget asks for representative mobile 4G as well as desktop. These
figures are unthrottled localhost on a developer machine and are a baseline only - they do
not demonstrate the 2.5 s mobile target.

## Not yet measurable here

These need a physical Android device and are the remaining Phase 19 work:

- Cold start to interactive, frame time, and scroll jank. `androidx.benchmark` refuses to run
  on an emulator by design: it reports `ERROR: Running on Emulator` and warns that
  suppressing the check compromises accuracy. Suppressing it would produce numbers that look
  like evidence and are not, so the module is left refusing rather than configured to lie.
  Run `./gradlew :benchmark:connectedBenchmarkAndroidTest` with a device attached.
- Heap snapshots and artwork decode traces.
- Throttled mobile-web measurement.
