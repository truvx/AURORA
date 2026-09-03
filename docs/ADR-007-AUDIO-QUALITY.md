# ADR-007: Capability-resolved audio quality

## Status

Accepted as foundation; the final policy is AUDIO_QUALITY_POLICY.md.

## Context

Users need one quality preference across providers, but quality labels differ by source and a provider may not expose selection.

## Decision

Separate `RequestedQuality`, provider capabilities, track capabilities, factual `ActualQuality`, and resolved `ActualPlaybackQuality`. Resolve only to a capability-backed option; report provider-determined/unknown states and downgrade reasons. Local files expose detected technical metadata.

## Alternatives considered

- Treat preference labels as guarantees — rejected as misleading.
- Use provider-name heuristics — rejected because capabilities and policies change.
- Transcode to manufacture a higher label — rejected because it cannot add source fidelity.

## Consequences

The UI must explain requested vs available/effective quality. Metadata extraction and provider capability tests are mandatory.

## Future migration

New provider variants or device constraints can extend the resolution policy without changing the user-facing preference enum.
