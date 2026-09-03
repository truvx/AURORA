---
name: aurora-audio
description: Define or review AURORA audio quality preferences, local-file technical metadata, loudness normalization, gain, limiter, and capability-aware audio behavior.
---

# AURORA audio

Use for quality settings, local metadata extraction, ReplayGain/loudness, gain processing, clipping protection, or source capability mapping. Read `docs/AUDIO_PIPELINE.md` before modifying audio policy.

## Quality policy

The universal preference enum is `Auto`, `Low`, `Medium`, `High`, `Lossless`, `HiRes`. It is a preference ceiling/default, not a promise. Resolve it against actual provider capabilities and expose the resolved choice plus evidence.

- YouTube must not be presented as lossless or Hi-Res unless the supported source explicitly provides that capability.
- Local files may report actual codec/container, bitrate, sample rate, channels, bit depth, and lossless status when reliably detected.
- Use `Unknown` rather than guessing. Separate `preferredQuality` from `availableQuality` and `effectiveQuality`.
- Quality selection cannot authorize prohibited downloading or stream extraction.

## Normalization policy

The universal enum is `Off`, `Quiet`, `Normal`, `Loud`. Keep it provider-neutral. Local files may use ReplayGain tags or measured loudness; missing metadata gets a conservative fallback. Preserve dynamics, cap gain, detect likely clipping, and document limiter behavior. Do not claim parity with Apple Music or Spotify without measurements and a documented test set.

## Pipeline rules

- Inspect metadata off the main/UI thread and bound scan concurrency.
- Keep original local files unchanged; processing is playback-time policy.
- Make gain decisions deterministic and testable from metadata and settings.
- Do not resample or transcode merely to display a label.
- Surface processing state and unavailable fields honestly.

## Example

```kotlin
data class ResolvedQuality(
    val preference: AudioQualityPreference,
    val effective: AvailableQuality?,
    val reason: ResolutionReason,
)
```
