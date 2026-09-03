# Phase 7: YouTube Implementation

## Overview
This document outlines the architecture, constraints, and implementation details for the YouTube integration (Phase 7) within the AURORA application, strictly utilizing official and supported APIs.

## Official YouTube APIs
- **YouTube Data API v3**: Used for discovery and metadata resolution. 
  - `search.list` endpoint is used with `part=snippet` and `type=video` to locate music tracks.
  - `videos.list` endpoint is used to resolve video metadata (`part=snippet,contentDetails,status`).
  - Search requests explicitly include `videoEmbeddable=true` to guarantee that results can be played in the embedded player.

## Official Playback Mechanism
- Playback is achieved using the **YouTube IFrame Player API** embedded within an Android WebView.
- The WebView acts as a bridge, listening to events from the official JavaScript player via a bound JavascriptInterface (`AuroraBridge`) and controlling playback state via JavaScript evaluation.

## API-Key Handling
- API requests require a valid `YOUTUBE_API_KEY`.
- For security reasons, the key is NOT committed to source control. It is injected into the client using `local.properties` (which is git-ignored) and Gradle's `BuildConfig`.
- **Security Constraint**: While this bundles the key into the compiled `classes.dex`, it relies on Google Cloud Platform's Android package name and SHA-1 signing certificate restrictions to prevent unauthorized quota usage. The app gracefully handles a blank or missing API key, returning a structured error.

## Quota
- Quota is managed by the provider. The `search.list` endpoint consumes exactly 100 units per request. The `videos.list` endpoint consumes 1 unit per request.
- The app relies on explicit user actions to search, preventing infinite loops, unbound paging, or aggressive background polling to avoid quota exhaustion.

## Embeddability
- Results from `search.list` are filtered by `videoEmbeddable=true`.
- The `videos.list` API specifically requests the `status` part and verifies `item.status.embeddable == true` before returning success.
- If a video becomes non-embeddable or unavailable, the player will correctly propagate the player error.

## Android WebView Architecture
- The `YouTubePlayerAdapter` is a singleton wrapping a `WebView` instance.
- The WebView `loadDataWithBaseURL` is set to `https://aurora.dev` to assert the app's `origin` client identity accurately according to the IFrame API requirements.
- The WebView settings are hardened: File access (`allowFileAccess`) and Content access (`allowContentAccess`) are disabled, and JavaScript is enabled strictly for the IFrame API.
- The WebView is hoisted in Jetpack Compose via `YouTubePlayerSurface` inside `AndroidView`. It must be visible in the Compose hierarchy (`NowPlayingScreen` or `MiniPlayer`) when playing.

## Provider Capabilities
YouTube items are processed by `YouTubeMusicProvider`, which explicitly declares the following capabilities:
- `SEARCH`
- `METADATA`
- `ARTWORK`
- `PLAYBACK`
- `QUEUE`

## Provider Limitations & Unsupported Features
- **UNSUPPORTED**: Offline caching, downloads, or media byte extraction.
- **UNSUPPORTED**: Audio-only playback (A visible 0px or hidden alpha=0 player is prohibited; the player must be visible).
- **UNSUPPORTED**: Audio Normalization (LUFS/ReplayGain) because the raw audio streams are inaccessible. Volume control is managed by the provider.
- **UNSUPPORTED**: True Background playback. While `mediaPlaybackRequiresUserGesture=false` allows autoplay, playing YouTube media while the screen is off or app is fully backgrounded may be terminated by the OS/WebView or violate provider terms unless YouTube Premium state is officially passed through via official SDKs (which IFrame does not support natively in this context).

## Playback Lifecycle & Single Authority
- A single `PlayerCoordinator` orchestrates playback.
- A `CompositePlayerAdapter` serves as a router: it delegates to `Media3PlayerAdapter` for local tracks, and `YouTubePlayerAdapter` for YouTube tracks.
- **Switching**: Switching from a Local track to a YouTube track (and vice versa) explicitly issues a `pause()` command to the previous adapter, guaranteeing no simultaneous overlapping playback.
- **Release**: Calling `release()` tears down the WebView entirely via `destroy()`.

## Testing Status & Runtime Verification
- **Unit Tests**: Provider and API models have been tested for successful mapping, empty states, and errors.
- **Connected Tests**: Integrated UI tests verified that `YouTubePlayerSurface` successfully composes without exceptions on a real device/emulator.
- **Runtime Verification**: Verification confirmed that the official IFrame player renders correctly in the `NowPlayingScreen`, responds to the `AuroraBridge`, and restricts unauthorized media extraction.
