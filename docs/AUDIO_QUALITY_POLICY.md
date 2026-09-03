# AURORA audio quality policy

Status: final v1 policy. The global preference is a preference, never a guarantee; see the final baseline and ADR-010/011 for platform/provider boundaries.

## Terms

- `RequestedQuality`: user preference `Auto | Low | Medium | High | Lossless | Hi-Res`.
- `ProviderCapabilities`: source-level facts about whether and how quality can be selected, plus available quality descriptors.
- `TrackCapabilities`: track-level facts from the provider or local file.
- `ActualQuality`: factual source/track descriptor (`codec`, `bitrate`, `sampleRate`, `bitDepth`, `channels`, `lossless`, and confidence where relevant).
- `ActualPlaybackQuality`: the resolved quality used by the player, or `ProviderDetermined`/`Unknown` when selection is unavailable.

`Preferred Audio Quality` is a preference ceiling/default, never a guarantee.

## Resolution algorithm

```text
1. Read requested preference.
2. Read provider capabilities for the selected source.
3. Read track capabilities and factual metadata.
4. If the provider controls quality and does not expose variants:
      return ProviderDetermined with actual facts if available.
5. If selection is supported:
      discard variants above the requested ceiling;
      rank remaining variants by fidelity, stability, and policy;
      choose the best supported variant.
6. If no variant meets the request:
      choose the best actually supported lower variant when policy allows;
      report the downgrade reason.
7. If source facts are insufficient:
      return Unknown/ProviderDetermined; never infer Lossless or Hi-Res.
```

`Auto` chooses a provider-appropriate stable quality under current network/device conditions. A manual preference may influence selection only when the provider exposes the required choice. A quality label is rendered only with provenance and an `available/effective` distinction. `Hi-Res` is never inferred from a file extension, provider name, or requested preference.

## Local-file policy

For MP3, AAC, M4A, ALAC, FLAC, WAV, Opus, and OGG where supported by the eventual platform stack, inspect actual container/codec metadata and decode support. Lossless is reported only when codec/container facts justify it; lossy files are never upgraded by a label. A transcoded playback path must not be called bit-perfect.

## Provider policy

YouTube’s allowed integration must determine its own exposed quality. If selectable lossless/Hi-Res variants are not provided, show `Provider determines quality` with known source facts. The quality setting cannot authorize stream extraction, caching, or downloading.

## UI states

Show `Preferred Audio Quality` globally. At track/source level show `Available Quality`, `Effective Quality`, and a concise reason: `Selected`, `Provider determines quality`, `Lower supported quality selected`, or `Unknown`. Unsupported controls are disabled/hidden with an accessible explanation.

## Tests

Test every preference against: multiple variants, only lower variants, no selection capability, unknown metadata, local lossless/lossy formats, provider-determined quality, offline mode, and policy-blocked paths. Test that no path fabricates Hi-Res/Lossless.
