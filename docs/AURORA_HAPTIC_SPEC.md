# AURORA haptic specification

Status: final v1 haptic contract. `HapticEngine` is the only haptic boundary.

## Central contract

`HapticEngine` accepts semantic events and exposes capability/availability state. UI, player, download, and navigation code do not call raw vibration APIs. Android maps events to supported primitives; web may no-op or use only a supported, user-permitted mechanism.

## Events

Seven, and the count is the point. An earlier nineteen were not nineteen sensations:
several compiled to the same rhythm under different names, and others differed by a tenth
of a scale factor and a few milliseconds - a difference on paper and nothing at all
through a phone's chassis. A vocabulary whose words cannot be told apart is not a
vocabulary, and the ones that could be told apart stopped registering because everything
buzzed.

| Event | Use | Frequency policy |
|---|---|---|
| `Tap` | a control did the thing it says it does | once per activation |
| `Selection` | which one of a set is current has changed - tab, segment, picker | once on committed change |
| `Toggle` | a two-state thing committed: played or paused, favourited, switched on | once on committed change |
| `Tick` | a boundary crossed under a gesture still in progress - a scrub snap, a slider step, an insertion point passed | rate-limited by the engine; never per frame |
| `Lift` | something came off the list and is now following the finger | once |
| `Commit` | it landed, or it worked - a reorder accepted, a playlist made, a secret saved | once |
| `Refused` | it did not happen, refused on purpose or failed | once per surfaced outcome |

`Tick` is the only event that arrives under a moving finger, so it is the only one the
engine holds back, and the quietest of the seven. `Refused` is two heavy beats well apart,
because a single beat of anything reads as "done".

Adding an eighth needs a sensation the other seven do not already produce.
`HapticEngineTest` asserts no two events compile to the same rhythm, which is the half of
that claim a machine can check.

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
