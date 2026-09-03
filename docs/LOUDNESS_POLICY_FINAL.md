# AURORA Loudness Policy

Status: final v1 policy, accepted by ADR-014.

## Scope

The global `Volume Normalization` preference is `Off`, `Quiet`, `Normal`, or `Loud`. It applies uniformly to the product setting surface. Actual processing is capability-backed and source-specific:

```text
GlobalPreference + TrackAnalysis + ProviderCapabilities + PlaybackContext
    -> ActualAppliedGain
```

Track-level UI must distinguish preference, measured metadata, provider capability, and actual gain. `Unknown` is a valid result.

## Local files

Local files use the measured integrated loudness path in ADR-014. Normalization is non-destructive. The source file, URI, container, codec, tags, and original peak data are not altered. Analysis may be deferred, cached, invalidated by content fingerprint, and recomputed when the analyzer policy changes.

Default behavior is album-aware: use reliable album gain while playing an album context; use reliable track gain for an individual track or mixed queue. If neither is reliable, use unity gain until offline analysis completes. Peak protection and limiter behavior are explicit and bounded.

## YouTube

The official embedded player controls the audiovisual stream and does not expose a supported AURORA loudness-DSP or audio-only contract. AURORA therefore does not run its own LUFS normalization for YouTube and does not claim that its `Normal` or `Loud` setting changes YouTube program loudness. The UI may show `Provider managed` or `Unavailable` actual normalization.

## Defaults

`Normal` is the default. Targets are `-18 LUFS-I` for Quiet, `-14 LUFS-I` for Normal, and `-11 LUFS-I` for Loud. All local modes use a `+6 dB` gain cap and true-peak protection at `-1.0 dBTP`; attenuation caps are `-18 dB` for Quiet/Normal and `-12 dB` for Loud. These are initial product defaults and must be validated with measured listening and test vectors before release, without changing their meaning silently.
