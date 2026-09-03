# ADR-011: YouTube Integration

- Status: Accepted
- Date: 2026-09-03
- Scope: Initial YouTube discovery and playback provider

## Decision

AURORA will use public YouTube Data API v3 for discovery and metadata, and the official visible YouTube IFrame Player API for playback:

- Web playback uses a visible, user-recognizable IFrame Player API surface.
- Android playback uses the same official embedded player mechanism in a visible Android WebView surface, with the current Android WebView Media Integrity requirements evaluated during implementation.
- Search results request embeddable content where appropriate (`videoEmbeddable=true`) and include provider attribution.
- The app queue owns an ordered list of provider video IDs. The embedded player is loaded or cued through official API methods while its player surface remains visible.
- Autoplay is opt-in, initiated only after an explicit user action, and subject to browser and platform policy. The default is off.

No native YouTube stream is exposed to AURORA. The app never receives or fabricates a separate audio URL.

## Explicitly unsupported behavior

AURORA will not scrape YouTube, extract audiovisual streams, split audio from video, download or offline-cache YouTube media bytes, bypass DRM or geographic restrictions, block player advertising, modify or hide the player, scrape credentials/cookies/tokens, or create a hidden/background player. YouTube playback is foreground and visible; AURORA will not promise screen-off, notification, lock-screen, or background playback for YouTube.

## Capability and policy constraints

- The IFrame Player API supports loading and queuing videos and playlists, but browser autoplay can be blocked. Search-result queuing uses video IDs returned by the Data API; the deprecated search playlist mechanism is not used.
- The IFrame API no longer provides a supported AURORA-controlled quality-selection contract. `getPlaybackQuality`, `setPlaybackQuality`, and `getAvailableQualityLevels` must not be used to claim user-selectable quality.
- Public Data API metadata is subject to the current YouTube API Services policies, including retention and refresh limits. Non-authorized API data is not treated as an indefinite local catalog.
- The player must preserve YouTube attribution, required branding, user controls, and policy-required behavior. `origin`/client identity and an HTTP Referer or equivalent must be supplied as required by the current IFrame API.
- Data API quota is finite. `search.list` is high cost relative to `videos.list`; cache only within policy and bound requests with pagination, debouncing, and result limits.

## Alternatives considered

| Option | Decision | Reason |
| --- | --- | --- |
| Visible IFrame Player API | Chosen | Official Web mechanism and the currently documented embedded-player path that preserves YouTube's player experience. |
| Native Android YouTube player SDK | Not selected | The current official Android player page consulted does not provide a supported v1 contract; Android uses the documented WebView/IFrame path instead. |
| Native stream/audio extraction | Rejected | Violates the YouTube API Services policies and would create an unsupported DRM, licensing, caching, and quality boundary. |
| Hidden/background player | Rejected | Explicitly conflicts with current policy and the visible-player requirement. |

Assumptions: the IFrame API remains available to approved clients and Android WebView can satisfy current embedded-player integrity requirements. Confidence: high for the prohibition and visible-player boundary; medium for long-term platform availability, so the adapter must surface policy/provider failure without an extraction fallback.

## AURORA behavior

The provider advertises capabilities at runtime. YouTube exposes discovery, metadata, artwork, visible foreground playback, and a foreground app queue. It does not expose downloads, offline playback, audio-only playback, screen-off playback, or quality guarantees. Lyrics are not inferred from captions or descriptions. Recommendations shown by the embedded player remain YouTube-owned; AURORA's recommendation engine may use only permitted provider metadata and its own deterministic signals.

## Sources consulted

- [YouTube IFrame Player API Reference](https://developers.google.com/youtube/iframe_api_reference)
- [YouTube player parameters](https://developers.google.com/youtube/player_parameters)
- [YouTube Data API `search.list`](https://developers.google.com/youtube/v3/docs/search/list)
- [YouTube Data API `videos.list`](https://developers.google.com/youtube/v3/docs/videos/list)
- [YouTube Data API authentication](https://developers.google.com/youtube/v3/guides/authentication)
- [YouTube Data API quota and compliance audits](https://developers.google.com/youtube/v3/guides/quota_and_compliance_audits)
- [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies)
- [YouTube API Services Terms of Service](https://developers.google.com/youtube/terms/api-services-terms-of-service)
