# AURORA haptic specification

Status: final v1 haptic contract. `HapticEngine` is the only haptic boundary.

## Central contract

`HapticEngine` accepts semantic events and exposes capability/availability state. UI, player, download, and navigation code do not call raw vibration APIs. Android maps events to supported primitives; web may no-op or use only a supported, user-permitted mechanism.

## Events

| Event | Use | Frequency policy |
|---|---|---|
| `tap` | meaningful button activation | once per activation |
| `selection` | picker/tab/meaningful choice | once on committed change |
| `toggle` | on/off setting | once on committed change |
| `scrub` | sparse meaningful scrub snap | rate-limited; never every frame |
| `sliderTick` | discrete slider boundary/tick | rate-limited |
| `dragStart` | queue/item drag begins | once |
| `dragMove` | only meaningful snap/boundary | sparse, rate-limited |
| `dragDrop` | valid reorder committed | once |
| `favorite` | favorite state committed | once |
| `queueReorder` | reorder committed | once |
| `downloadComplete` | legitimate local import/download completed | once |
| `success` | meaningful successful operation | once |
| `warning` | actionable warning | once, not for passive states |
| `error` | actionable error | once per surfaced failure |

## Capability tiers

1. Advanced actuator supports richer one-shot/impact/selection primitives.
2. Basic vibrator supports a reduced one-shot mapping.
3. No capability, permission, or user setting disabled: no-op with visual/semantic feedback.

Capability detection is cached but may change. Haptics never block state updates or playback.

## Reduced feedback

Respect platform accessibility/user settings and AURORA’s haptic preference. Reduced feedback lowers intensity/frequency or disables optional events; errors and completion still have visual and semantic communication. Never make a haptic event the only signal.

## Scroll and scrub policy

Scrolling produces no continuous vibration. Scrubbing may tick at meaningful snapped positions with a minimum time/distance interval. Queue drag may tick at insertion boundaries, not per pixel.

## Testing

Test event-to-primitive mapping with a fake engine, capability tiers, disabled/reduced settings, rate limits, cancellation, lifecycle disposal, duplicate state callbacks, and events emitted only from user/system transitions rather than recomposition.
