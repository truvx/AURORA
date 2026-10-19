# ADR-011: YouTube Integration & NomaTune Playback Architecture

- Status: Accepted (Superseded & Modernized)
- Scope: YouTube discovery, audio stream extraction, and background playback architecture

## Decision

AURORA adopts the native playback and streaming architecture from **NomaTune** (https://github.com/Shahdullah/NomaTune, GPL-3.0), combining high-performance audio stream resolution with native AndroidX Media3 / ExoPlayer playback:

- **Audio Streaming via InnerTube**: Audio streams are resolved directly using InnerTube endpoints (`youtubei/v1/player`) and stream extractors, selecting optimal audio-only `adaptiveFormats` (e.g., Opus/AAC by itag).
- **Native Media3 / ExoPlayer Pipeline**: Extracted audio streams feed directly into AURORA's unified Media3 playback engine on Android (and browser audio on Web).
- **Background & Screen-Off Playback**: Full continuous playback as an Android Foreground Service with complete MediaSession integration, notification transport controls (play/pause, skip, scrub), lock-screen metadata, and Bluetooth/headphone control.
- **Now Playing UI**: Adopt NomaTune's V7/V8 player design system (`V7PlayerBackdrop`, `V7BackdropPalette`, `V8PlayerControlsContent`, `V8MetadataActions`, `V8PlaybackProgress`, `V8TransportControls`, `V8VolumeControls`) with deep backdrop blur, dynamic palette extraction, and lyrics integration.
- **Previous Prohibitions Superseded**: All former requirements mandating visible WebView/IFrame players or prohibiting stream extraction and background audio are eliminated.
