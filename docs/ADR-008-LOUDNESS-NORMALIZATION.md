# ADR-008: Non-destructive capability-aware loudness normalization

## Status

Accepted as foundation; superseded in detail by ADR-014 and LOUDNESS_POLICY_FINAL.md.

## Context

AURORA wants consistent perceived loudness while preserving dynamics and avoiding claims of exact parity or bit-perfect output when processing is active.

## Decision

Use Off/Quiet/Normal/Loud policy over a source → analysis → target → gain → protection/limiter → output pipeline. Prefer reliable ReplayGain/analysis, apply bounded playback-time gain, detect/protect clipping, and report provider-managed/unavailable behavior. Never rewrite local files.

## Alternatives considered

- Normalize by peak alone — rejected because peak does not represent perceived loudness.
- Always apply a limiter — rejected because it may harm dynamics and is unnecessary when attenuation suffices.
- Claim service parity — rejected without measured reference data.

## Consequences

DSP targets, gain caps, and limiter parameters need a measured corpus and documented version. Technical UI must disclose processing.

## Future migration

Improved loudness analysis can be added behind the same analysis/gain contract. Existing files remain untouched.
