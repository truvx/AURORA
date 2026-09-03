# AURORA loudness normalization

Status: final v1 implementation reference. `LOUDNESS_POLICY_FINAL.md` is the short normative policy.

## User setting

`Volume Normalization` has `Off | Quiet | Normal | Loud`. It expresses a target perceived-loudness policy, not a claim of matching any third-party service.

## Processing model

```text
source signal
    → metadata/analysis lookup
    → reference selection (album gain > track gain according to mode)
    → target level from normalization mode
    → bounded gain calculation
    → peak/clipping prediction
    → optional playback-time limiter/protection
    → output
```

The original local file is never rewritten. Processing is playback-time. If DSP is active, playback is not bit-perfect; the UI must communicate that in technical details where relevant.

## Analysis sources

Prefer reliable embedded ReplayGain album/track gain and peak values. If absent, use measured loudness/peak analysis when feasible, off the UI thread, with bounded work. If no analysis exists, use a conservative no/low-gain fallback and label normalization as limited/unknown rather than inventing a measurement.

## Gain and protection

Use the final target levels and gain caps from `ADR-014-LOUDNESS-NORMALIZATION.md`. Album context uses reliable album gain; other playback uses track gain. Predict clipping from peak metadata or analysis. Apply attenuation when needed; use a bounded look-ahead limiter only when the final policy requires peak protection, with a `-1.0 dBTP` ceiling.

## Provider behavior

For providers that do not expose raw signal control or normalization metadata, report `Provider managed` or `Unavailable`. Do not imply that local ReplayGain settings control a provider stream.

## Accessibility and UX

Explain the setting in plain language, expose processing status, and avoid a level meter that suggests false precision. Changing the setting applies to future/current playback according to engine capability and must not interrupt playback unexpectedly.

## Verification

Use a reference corpus with loud, quiet, dynamic, clipped, mono, stereo, and missing-metadata files. Test Off, each mode, album/track precedence, peak protection, gain caps, limiter enabled/disabled, provider-managed sources, and no-destructive-write guarantees.
