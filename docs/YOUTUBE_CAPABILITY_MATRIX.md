# YouTube Capability Matrix

Status: final v1 contract, 2026-10-20.

"Supported" means supported by an official mechanism and allowed by the current policy boundary. It does not mean guaranteed for every video, browser, account, network, or device.

| Capability | Android | Web | Official mechanism | Supported? | Restrictions | AURORA behavior |
| --- | --- | --- | --- | --- | --- | --- |
| Search | Yes | Yes | YouTube Data API `search.list` | Supported | Public requests consume quota; request embeddable videos where playback is needed; refresh and retention policy applies. | Debounced, bounded search with provider attribution and capability filtering. |
| Metadata | Yes | Yes | YouTube Data API `videos.list`, `search.list` | Supported | Public metadata is not an eternal local cache; authorized data needs the supported OAuth flow. | Map into provider-neutral metadata; expire or refresh according to policy. |
| Artwork | Yes | Yes | Data API thumbnail fields and embedded player | Supported with policy limits | URL availability and retention/caching rules apply; source attribution remains visible. | Display artwork only when available; unknown remains unknown. |
| Playback | Yes | Yes | Visible YouTube IFrame Player API; Android WebView embedding | Supported | Requires a visible player and user-recognizable YouTube experience; no audio-only extraction. | Embed the official player; expose play state from player callbacks. |
| Queue | Yes | Yes | IFrame API `cueVideoById`, `loadVideoById`, `cuePlaylist`, `loadPlaylist` | Supported in foreground | The queue contains provider IDs, not media bytes; player remains visible; deprecated search playlist flow is not used. | Maintain one app queue and advance through official loads. |
| Autoplay | Conditional | Conditional | IFrame API `autoplay`/load methods | Conditional | Browser/user-agent policy can block it; autoplay can trigger data sharing on load. | Default off; use only after explicit user intent and handle blocked-autoplay state. |
| Background playback | Yes | No | Android: YouTube Data API + InnerTube `youtubei/v1/player`, audio-only `adaptiveFormats`, stream URLs fed into Media3/ExoPlayer through a foreground `MediaSessionService` | Supported | Stream URLs are short-lived and expiration-aware; no media bytes downloaded or cached by AURORA; no claim of bitrate, codec, lossless, or Hi-Res. | YouTube audio stream URLs are resolved via InnerTube and played by Media3 through the foreground service, so playback survives backgrounding and screen-off. Web has no background path. |
| Screen-off playback | Yes | No | Android: Media3 `MediaSessionService` with foreground service | Supported | Same visible-player and policy boundary for YouTube; local files continue to own the media session. | YouTube audio plays through Media3 behind the foreground media session, so the lock screen and headset controls continue to work with the screen off. |
| Quality selection | No app-controlled contract | No app-controlled contract | IFrame player quality methods are not a supported contract | Unsupported | Do not claim bitrate, codec, sample rate, lossless, or Hi-Res from YouTube. | Resolve `Preferred Audio Quality` to provider-determined/unknown actual quality. |
| Downloads | No | No | None | Unsupported | AURORA does not download or cache YouTube audiovisual content. | Hide download action for YouTube. |
| Offline playback | No | No | None | Unsupported | No offline media bytes or hidden cache. | Offline mode includes local files only. |
| Lyrics | Not from YouTube | Not from YouTube | LRCLIB, a separate lyrics database | Supported from that source only | Captions/descriptions are still not treated as licensed lyrics, and never will be. | Look the record up by its resolved identity; show nothing when the source has nothing. |
| Recommendations | Limited provider UI | Limited provider UI | Embedded player related-video behavior; Data API search for candidate discovery | Limited | YouTube controls related content; no general AURORA claim or unrestricted derived dataset. | Treat embedded recommendations as provider-owned; use AURORA deterministic ranking for permitted candidates. |
| Metadata caching | Bounded | Bounded | Data API metadata plus local policy-compliant cache | Conditional | Non-authorized API data has current retention/refresh limits; audiovisual bytes are never cached. | Expire/refresh metadata and artwork according to policy; never make a media-byte cache. |
| Audio separation / stream extraction | Yes (audio-only) | No | InnerTube `youtubei/v1/player`, audio-only `adaptiveFormats` by itag | Supported | Stream URLs are provider-determined and short-lived; NewPipe's `YoutubeJavaScriptPlayerManager` deobfuscates signature ciphers; PoToken appended where required. | Resolves audio-only stream URLs and feeds them into Media3/ExoPlayer. No video extraction, no download, no caching of media bytes. |
| Authentication | Public discovery can be unauthenticated; OAuth only for authorized user data | Same | Data API public access and documented Google OAuth | Conditional | Never request/store YouTube credentials; service accounts are not used. | Keep public search separate from optional authorized account features. |
| Branding and attribution | Yes | Yes | Official embedded player and YouTube API branding requirements | Required | Do not obscure attribution, ads, or required player behavior. | AURORA plays YouTube audio through Media3 without the official player UI; attribution and source identity remain visible in the player UI. The YouTube logo, title and channel attribution are shown where track metadata is displayed. |
| API quota | Yes | Yes | YouTube Data API project quota | Conditional | At research time the default is 10,000 units/day; `search.list` costs 100 units and `videos.list` costs 1 unit. Recheck before implementation because quotas/policies can change. | Debounce, paginate, bound requests, monitor quota, and surface quota errors. |

## Authentication and quota

Public search and public metadata do not require a user's YouTube login. OAuth 2 is required only for authorized user data or actions that need it; service-account authentication is not used. AURORA never asks for or stores YouTube credentials. The project must monitor quota, with the current default quota and per-method costs verified at implementation time from the official quota documentation.

## Metadata shown for a YouTube track

A video title is not a song and a thumbnail is not a cover. AURORA reads an artist and a
title out of the upload - "keshi - Soft Spot (Official Music Video)" becomes keshi and Soft
Spot - and looks the record up in MusicBrainz for its name and its sleeve from the Cover
Art Archive.

This changes what is displayed, never what is played or claimed. Playback now runs through
Media3/ExoPlayer with audio-only stream URLs resolved from InnerTube's `youtubei/v1/player`.
Quality stays provider-determined and unstated. When the lookup finds nothing, or is not
confident enough to ask, the video's own title and thumbnail are shown rather than a guess.

## Where the player is

Local playback owns a Media3 `MediaSessionService`: a media notification, lock-screen and
headset controls, and playback that continues with the app in the background and the screen
off.

YouTube tracks play through the same Media3 `MediaSessionService`. Audio-only stream URLs are
resolved from InnerTube's `youtubei/v1/player` response (audio-only `adaptiveFormats` by itag,
signature-cipher deobfuscation via NewPipe's `YoutubeJavaScriptPlayerManager`, PoToken
appended where required) and fed into ExoPlayer. There is no separate WebView player, no
picture-in-picture window for YouTube, and no delegation to the YouTube Music app. The player
UI is AURORA's own Immersive layout, showing track title, artist and artwork attribution
alongside the transport controls.

## The media session

Local playback owns a Media3 `MediaSessionService`: a media notification, lock-screen and
headset controls, and playback that continues with the app in the background and the screen
off.

YouTube tracks share that same media session. Handing YouTube playback to Media3 keeps one
session, one notification, one set of lock-screen controls, and one truthful source of state
for every source. AURORA does not pretend to own YouTube's engine; it resolves audio stream
URLs through InnerTube and plays them through Media3, exposing the actual playback state
through the coordinator.

A paused Media3 player would have kept its last item, which would have put a background media
session behind YouTube playback and had the lock screen name a local track while a video made
the sound - two different untruths from one shortcut. Clearing it on hand-off keeps the
session honest: YouTube plays in Media3 like every other source, and AURORA does not pretend
to own either engine behind it.

## Progress bar

The scrubber is a flat track for every source; the waveform drawing was removed with the
Immersive player (database migration 6 to 7 dropped its table). For YouTube that was always
the only truthful option: AURORA never receives those bytes, so there is nothing to measure,
and a pattern generated to look like a waveform would be a picture of a sound nobody heard -
the same class of claim as stating a bitrate the provider never gave.

## Lyrics

The lyrics AURORA shows never come from YouTube. A caption track is a transcript of a video
and a description is whatever the uploader typed, and reading either as the lyric of a
recording is how a player ends up printing "[Music]" and a sign-off as verse. Both are also
somebody else's text, published for a different purpose.

They come from LRCLIB, a lyrics database with a public API and no key. It is asked about
the record AURORA has already identified - "keshi", "Soft Spot" - and never about the
upload's own wording. A candidate is accepted only when its length agrees with what is
playing, because the lyric of a different edit drifts further out of time with every
chorus, and a lyric is the one thing on screen a listener reads along with.

Like MusicBrainz and the Cover Art Archive, LRCLIB is not a `MusicProvider`: nothing is
played, browsed or recommended from it. The source is named on the surface that shows its
answer.

## Policy links

- [IFrame Player API Reference](https://developers.google.com/youtube/iframe_api_reference)
- [Player parameters](https://developers.google.com/youtube/player_parameters)
- [Data API search.list](https://developers.google.com/youtube/v3/docs/search/list)
- [Data API videos.list](https://developers.google.com/youtube/v3/docs/videos/list)
- [Developer Policies](https://developers.google.com/youtube/terms/developer-policies)

## Picture-in-picture

Picture-in-picture is used only for local-file playback, where the media session keeps the
track playing while the app is backgrounded. YouTube tracks play through Media3 and continue
in background/screen-off mode through the foreground media session without needing a visible
player window.

The rules this does not change: nothing is extracted beyond audio-only stream URLs, no
media bytes are downloaded or cached by AURORA, and YouTube quality remains
provider-determined and unstated.
