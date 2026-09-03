# AURORA audio pipeline

Status: final v1 audio boundary. Exact normalization values are in `LOUDNESS_POLICY_FINAL.md`.

## Quality preferences

Universal preference: Auto, Low, Medium, High, Lossless, Hi-Res. `Preferred Audio Quality` is a ceiling/default and resolves to the best actually supported `Available Quality`. Keep preference, available, and effective quality separate. YouTube must not be labeled lossless/Hi-Res without source evidence. Local metadata is factual; unknown stays unknown.

## Local metadata

Extract codec/container, bitrate, sample rate, channel count, bit depth, duration, and lossless status when reliable. Scan off the UI thread with bounded concurrency and lazy loading. Do not transcode or rewrite the original file to display metadata.

## Normalization

Universal mode: Off, Quiet, Normal, Loud. Support ReplayGain tags and measured loudness when available. Use the gain caps, clipping protection, limiter behavior, dynamics preservation, and missing-metadata fallback in `ADR-014-LOUDNESS-NORMALIZATION.md`. Processing is playback-time and non-destructive.

## Verification

Test deterministic quality resolution, provider capability mismatches, unknown metadata, ReplayGain precedence, gain limits, clipping cases, and normalization-disabled behavior. Do not claim parity with other services without a measured corpus and documented method.
