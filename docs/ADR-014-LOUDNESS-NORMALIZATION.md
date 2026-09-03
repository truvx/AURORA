# ADR-014: Loudness Normalization

- Status: Accepted
- Date: 2026-09-03

## Decision

AURORA implements non-destructive loudness normalization for local/user-owned audio only. The pipeline uses measured integrated loudness, preferably ReplayGain track/album metadata or an offline analysis cache, plus peak protection. It never rewrites the original file.

YouTube normalization is provider-managed and not controllable through the selected official player contract. The global setting remains visible, but the actual applied gain for YouTube is `Unavailable` or `ProviderManaged`; AURORA must not claim that it normalized YouTube or reproduced another service.

## User levels

| Setting | Target | Gain cap | Attenuation cap | Intended behavior |
| --- | ---: | ---: | ---: | --- |
| Off | None | 0 dB | 0 dB | Bypass AURORA normalization. Peak protection remains an output-safety concern of the active engine. |
| Quiet | -18 LUFS-I | +6 dB | -18 dB | More conservative loudness for quiet environments. |
| Normal | -14 LUFS-I | +6 dB | -18 dB | Default balanced target. |
| Loud | -11 LUFS-I | +6 dB | -12 dB | Louder listening without promising equal perceived loudness across all material. |

Targets are policy defaults, not a claim of parity with Apple Music, Spotify, or any other service. The settings are global; per-track UI may show source measurements and the actual applied result.

## Gain calculation

For local audio with a trusted measured value:

```text
requestedGainDb = targetLufs - measuredIntegratedLufs
boundedGainDb = clamp(requestedGainDb, attenuationCap, gainCap)
```

If album context has reliable album gain and a consistent album identity, use album gain for that playback context. Otherwise use track gain. If both are absent, enqueue offline analysis; until the result is available, apply no normalization gain and report `Pending` or `Unavailable` rather than guessing.

Before applying gain, use a known true-peak estimate when available. Reduce the gain so the predicted post-gain true peak is no higher than `-1.0 dBTP`. If the result still needs protection, use a bounded look-ahead limiter with a `-1.0 dBTP` ceiling. The limiter is a last-resort peak protector, not a loudness generator; limiter activity is measurable and testable.

## Analysis and runtime

- Prefer embedded ReplayGain track/album gain and peak fields when metadata is trustworthy.
- Otherwise analyze local files offline, outside the UI thread, with bounded concurrency. Cache the analysis keyed by durable local-file identity plus content fingerprint and analyzer-policy version.
- Runtime processing applies a scalar gain and only engages the limiter when peak protection requires it. It is reversible and does not mutate source bytes or user metadata.
- A missing, stale, corrupt, or unsupported measurement falls back to unity gain. The fallback is explicit in state and logs contain no audio content or secrets.
- YouTube has no AURORA-controlled sample stream in the selected integration, so no LUFS/DSP guarantee is exposed for it.

## Alternatives considered

| Option | Decision | Reason |
| --- | --- | --- |
| Peak-only normalization | Rejected | Peak does not represent perceived loudness and can produce inconsistent results. |
| Always-on limiter | Rejected | It can alter dynamics when no peak protection is needed. |
| Exact Apple Music/Spotify parity | Rejected | No evidence or shared reference implementation is available; AURORA must not claim parity. |
| ReplayGain/offline LUFS plus bounded runtime protection | Chosen | It is measurable, non-destructive, testable, and works with local user-owned files. |

Assumptions: local playback can expose a runtime DSP seam and offline analysis can be scheduled outside the UI thread. Confidence: medium until a reference corpus and device measurements validate the defaults; the policy remains technically bounded and exposes unknown/pending states.

## Verification

Test the gain calculation with known LUFS/peak vectors, cap boundaries, album-vs-track precedence, missing metadata, stale cache, true-peak protection, and limiter bypass when not required. Compare output with an independent measurement tool during implementation. Do not use line coverage as evidence of audio correctness.

## Sources

- [EBU R 128 loudness recommendation](https://tech.ebu.ch/publications/r128)
- [EBU Tech 3341 loudness metering](https://tech.ebu.ch/publications/tech3341)
- [ReplayGain specification](https://wiki.hydrogenaud.io/?title=ReplayGain_2.0_specification)
- [YouTube IFrame Player API Reference](https://developers.google.com/youtube/iframe_api_reference)
