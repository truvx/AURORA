# YouTube Capability Matrix

Status: final v1 contract, 2026-09-03.

"Supported" means supported by an official mechanism and allowed by the current policy boundary. It does not mean guaranteed for every video, browser, account, network, or device.

| Capability | Android | Web | Official mechanism | Supported? | Restrictions | AURORA behavior |
| --- | --- | --- | --- | --- | --- | --- |
| Search | Yes | Yes | YouTube Data API `search.list` | Supported | Public requests consume quota; request embeddable videos where playback is needed; refresh and retention policy applies. | Debounced, bounded search with provider attribution and capability filtering. |
| Metadata | Yes | Yes | YouTube Data API `videos.list`, `search.list` | Supported | Public metadata is not an eternal local cache; authorized data needs the supported OAuth flow. | Map into provider-neutral metadata; expire or refresh according to policy. |
| Artwork | Yes | Yes | Data API thumbnail fields and embedded player | Supported with policy limits | URL availability and retention/caching rules apply; source attribution remains visible. | Display artwork only when available; unknown remains unknown. |
| Playback | Yes | Yes | Visible YouTube IFrame Player API; Android WebView embedding | Supported | Requires a visible player and user-recognizable YouTube experience; no audio-only extraction. | Embed the official player; expose play state from player callbacks. |
| Queue | Yes | Yes | IFrame API `cueVideoById`, `loadVideoById`, `cuePlaylist`, `loadPlaylist` | Supported in foreground | The queue contains provider IDs, not media bytes; player remains visible; deprecated search playlist flow is not used. | Maintain one app queue and advance through official loads. |
| Autoplay | Conditional | Conditional | IFrame API `autoplay`/load methods | Conditional | Browser/user-agent policy can block it; autoplay can trigger data sharing on load. | Default off; use only after explicit user intent and handle blocked-autoplay state. |
| Background playback | No | No | None permitted for AURORA | Unsupported | YouTube policy forbids hidden/background players; no workaround. | Disable background promise and expose the limitation clearly. |
| Screen-off playback | No | No | None permitted for AURORA | Unsupported | Same visible-player and policy boundary. | Local files may support platform media controls; YouTube stops or remains unavailable when the player is not visible. |
| Quality selection | No app-controlled contract | No app-controlled contract | IFrame player quality methods are not a supported contract | Unsupported | Do not claim bitrate, codec, sample rate, lossless, or Hi-Res from YouTube. | Resolve `Preferred Audio Quality` to provider-determined/unknown actual quality. |
| Downloads | No | No | None | Unsupported | AURORA does not download or cache YouTube audiovisual content. | Hide download action for YouTube. |
| Offline playback | No | No | None | Unsupported | No offline media bytes or hidden cache. | Offline mode includes local files only. |
| Lyrics | No provider lyrics API | No provider lyrics API | None in the selected mechanisms | Unsupported | Captions/descriptions are not treated as licensed lyrics. | Do not show a lyrics action for YouTube unless a separately authorized source is added later. |
| Recommendations | Limited provider UI | Limited provider UI | Embedded player related-video behavior; Data API search for candidate discovery | Limited | YouTube controls related content; no general AURORA claim or unrestricted derived dataset. | Treat embedded recommendations as provider-owned; use AURORA deterministic ranking for permitted candidates. |
| Metadata caching | Bounded | Bounded | Data API metadata plus local policy-compliant cache | Conditional | Non-authorized API data has current retention/refresh limits; audiovisual bytes are never cached. | Expire/refresh metadata and artwork according to policy; never make a media-byte cache. |
| Audio separation / stream extraction | No | No | None; playback remains inside the official player | Unsupported | No separate audio URL, scraping, DRM bypass, or alternate audio path. | Reject any path that requests or returns one. |
| Authentication | Public discovery can be unauthenticated; OAuth only for authorized user data | Same | Data API public access and documented Google OAuth | Conditional | Never request/store YouTube credentials; service accounts are not used. | Keep public search separate from optional authorized account features. |
| Branding and attribution | Yes | Yes | Official embedded player and YouTube API branding requirements | Required | Do not obscure attribution, controls, ads, or required player behavior. | Show the official player and provider/source identity. |
| API quota | Yes | Yes | YouTube Data API project quota | Conditional | At research time the default is 10,000 units/day; `search.list` costs 100 units and `videos.list` costs 1 unit. Recheck before implementation because quotas/policies can change. | Debounce, paginate, bound requests, monitor quota, and surface quota errors. |

## Authentication and quota

Public search and public metadata do not require a user's YouTube login. OAuth 2 is required only for authorized user data or actions that need it; service-account authentication is not used. AURORA never asks for or stores YouTube credentials. The project must monitor quota, with the current default quota and per-method costs verified at implementation time from the official quota documentation.

## Policy links

- [IFrame Player API Reference](https://developers.google.com/youtube/iframe_api_reference)
- [Player parameters](https://developers.google.com/youtube/player_parameters)
- [Data API search.list](https://developers.google.com/youtube/v3/docs/search/list)
- [Data API videos.list](https://developers.google.com/youtube/v3/docs/videos/list)
- [Developer Policies](https://developers.google.com/youtube/terms/developer-policies)
