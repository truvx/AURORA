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
| Lyrics | Not from YouTube | Not from YouTube | LRCLIB, a separate lyrics database | Supported from that source only | Captions/descriptions are still not treated as licensed lyrics, and never will be. | Look the record up by its resolved identity; show nothing when the source has nothing. |
| Recommendations | Limited provider UI | Limited provider UI | Embedded player related-video behavior; Data API search for candidate discovery | Limited | YouTube controls related content; no general AURORA claim or unrestricted derived dataset. | Treat embedded recommendations as provider-owned; use AURORA deterministic ranking for permitted candidates. |
| Metadata caching | Bounded | Bounded | Data API metadata plus local policy-compliant cache | Conditional | Non-authorized API data has current retention/refresh limits; audiovisual bytes are never cached. | Expire/refresh metadata and artwork according to policy; never make a media-byte cache. |
| Audio separation / stream extraction | No | No | None; playback remains inside the official player | Unsupported | No separate audio URL, scraping, DRM bypass, or alternate audio path. | Reject any path that requests or returns one. |
| Authentication | Public discovery can be unauthenticated; OAuth only for authorized user data | Same | Data API public access and documented Google OAuth | Conditional | Never request/store YouTube credentials; service accounts are not used. | Keep public search separate from optional authorized account features. |
| Branding and attribution | Yes | Yes | Official embedded player and YouTube API branding requirements | Required | Do not obscure attribution, controls, ads, or required player behavior. | Show the official player and provider/source identity. |
| API quota | Yes | Yes | YouTube Data API project quota | Conditional | At research time the default is 10,000 units/day; `search.list` costs 100 units and `videos.list` costs 1 unit. Recheck before implementation because quotas/policies can change. | Debounce, paginate, bound requests, monitor quota, and surface quota errors. |

## Authentication and quota

Public search and public metadata do not require a user's YouTube login. OAuth 2 is required only for authorized user data or actions that need it; service-account authentication is not used. AURORA never asks for or stores YouTube credentials. The project must monitor quota, with the current default quota and per-method costs verified at implementation time from the official quota documentation.

## Metadata shown for a YouTube track

A video title is not a song and a thumbnail is not a cover. AURORA reads an artist and a
title out of the upload - "keshi - Soft Spot (Official Music Video)" becomes keshi and Soft
Spot - and looks the record up in MusicBrainz for its name and its sleeve from the Cover
Art Archive.

This changes what is displayed, never what is played or claimed. Playback stays on the
visible official player. Quality stays provider-determined and unstated. When the lookup
finds nothing, or is not confident enough to ask, the video's own title and thumbnail are
shown rather than a guess.

## Where the player is

The player is visible wherever a YouTube track is playing, and it is never the artwork.

Now Playing shows the record's sleeve as its hero and carries the official player beneath
the transport controls, labelled and at 16:9. While the full player is closed, the same
player is docked above the floating chrome at the end of the row. Nothing is drawn over
either one, and neither is shrunk to stand in for a cover - a 44dp circle of video is not
a user-recognizable YouTube experience, and it is not a picture of the record.

## The media session

Local playback owns a Media3 `MediaSessionService`: a media notification, lock-screen and
headset controls, and playback that continues with the app in the background and the screen
off.

A YouTube track owns none of it. Handing over to YouTube empties the Media3 player rather
than pausing it, so the session has nothing to claim and the foreground service drops. A
paused player would have kept its last item, which would have put a background media
session behind YouTube playback and had the lock screen name a local track while a video
made the sound - two different untruths from one shortcut. The player says so on screen:
"Keeps playing only while the player is on screen."

## Waveform

The scrubber draws the real shape of a local file, decoded once by the platform decoder and
remembered. It draws no shape at all for a YouTube track, and every bar is the same height
instead.

That is not a gap waiting to be filled. AURORA never receives those bytes, so there is
nothing to measure, and a pattern generated to look like a waveform would be a picture of a
sound nobody heard - the same class of claim as stating a bitrate the provider never gave.
Bars of one height are what "unknown" looks like here.

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

Leaving the app while a YouTube track is playing shrinks the official player into a
floating window, where it keeps playing. This is the only circumstance in which a YouTube
track continues when AURORA is not the foreground app, and it is permitted for one reason:
the player is still there. It is the same WebView, carrying YouTube's own controls,
branding and advertising - the "Includes paid promotion" disclosure renders inside the
window - and nothing about the stream is touched.

That is the line between this and a background player, which stays forbidden. The window
is not a convenience wrapped around audio; the audio is a consequence of the player still
being on screen.

Consequences, all of them deliberate:

- Only a *playing* YouTube track opens a window. Paused or idle would be a window asking
  to be dismissed, or showing nothing.
- Local files never use it. They have a media session and are allowed to play with the
  screen off, because AURORA owns the file.
- Closing the window stops the track. So does the screen going off. Both arrive as the
  Activity no longer being visible, and both mean the player is gone - so the audio goes
  with it (`PictureInPicturePolicy.shouldPauseOnLeaving`).
- The window carries previous, play/pause and next. They dispatch `PlayerCommand`s to the
  one `PlayerCoordinator`, like every other control surface; the play/pause icon is drawn
  from the same state the button reads, so it cannot say one thing and do another.

The rules this does not change: nothing is extracted, no audio is separated, no stream URL
is constructed, no media bytes are cached, and there is still no hidden or screen-off
player. True background YouTube remains available only through YouTube Premium in
YouTube's own apps.
