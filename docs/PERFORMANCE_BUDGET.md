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
