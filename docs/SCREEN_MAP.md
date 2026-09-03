# AURORA screen map

This map defines screen contracts; it does not implement screens.

| Screen | Purpose / entry points | Primary / secondary actions | States and accessibility | Motion / haptics |
|---|---|---|---|---|
| Home | Default destination; app launch, nav | Resume/play recommendation; search, library, AI, sections | Loading skeleton, intentional empty library, offline local-first, provider error; headings/landmarks; horizontal sections have accessible alternatives | Section reveal, artwork atmosphere; selection/play |
| Search | Nav, keyboard shortcut, search expansion | Submit query; scope/filter; voice/AI entry if supported | Idle, typing, loading, no results, mixed-source error; focus lands in field | Field expansion; focus/select |
| Search Results | Search submit, deep link | Play/add/open result; source filters | Songs/albums/artists/playlists/YouTube/local/AI sections; pagination; capability labels | Result insertion; selection |
| Artist | Result, deep link, track context | Play artist; follow/favorite if supported | Partial provider data, unavailable, offline cached/local; semantic artist heading | Shared artwork/header; play |
| Album | Result, library, track context | Play album; add/favorite | Track list, partial metadata, unavailable; accessible row labels | Album → player; play |
| Playlist | Library, result, deep link | Play all; edit/reorder; add tracks | Empty, loading, offline, conflict/error; non-drag reorder | Card → page; reorder |
| Library | Nav | Browse local/recent/favorites/playlists | Scan/import progress, empty, permission denied, offline; filters announced | Tab/section transition; selection |
| Favorites | Library shortcut | Play all; remove favorite | Empty, loading, persistence error; explicit favorite state | Favorite feedback; favorite haptic |
| Downloads/Imports | Library/settings | Import local files; inspect status; remove reference | Permission, scanning, unsupported file, success/error; no YouTube download affordance | Progress morph; completion haptic |
| Queue | Player/menu | Reorder/remove/clear/play next | Empty, unavailable item, large queue; keyboard reorder | Sheet/list reorder; drag start/drop |
| Mini-player | Persistent when active | Play/pause; expand; next | Confirmed playback state, buffering/error; compact semantics | Expand continuity; tap |
| Now Playing | Mini-player, notification, lock screen | Play/pause/seek/skip; queue/settings | Loading/buffering/error/unknown duration; full semantics | Artwork morph/scrub; control haptics |
| AI Music | Nav/search | Start prompt; choose provider; use recommendation | Not connected/offline/rate limited; privacy notice; focus | Command bar reveal; submit |
| AI Conversation | AI Music | Send prompt; inspect tool/result; cancel | Streaming/loading/error/empty; message semantics and tool provenance | Message insertion; submit/success |
| AI Recommendation Results | AI result | Play all; add/open; explain | Candidate unavailable/partial/AI unavailable; source labels | Result insertion → queue; add/play |
| Settings | Nav | Open category; search settings | Loading/persistence error; headings and current values | Category transition; selection |
| Playback Settings | Settings | Quality, normalization, crossfade, gapless, autoplay, volume behavior | Provider/track caveats; controls have accessible descriptions | Toggle/slider feedback |
| Audio Settings | Settings | ReplayGain, equalizer, technical details | DSP/bit-perfect disclosure; unavailable controls | Selector/sheet; selection |
| Appearance | Settings | Theme, transparency, artwork atmosphere, motion | Light/dark/dynamic, reduced motion/transparency; preview remains readable | Theme transition; toggle |
| AI Accounts | Settings/AI | Manage AURORA provider connection state; disconnect | Connecting/connected/expired/revoked/error; no consumer-app session reuse or secret paste | Account status update; success/error |
| Storage | Settings | Scan/import/cache/clear derived cache | Permission, progress, failure/recovery; size labels | Progress; completion |
| About | Settings | View version/licenses/policies | Readable legal/support content; external links safe | Minimal/no motion |

## Global destinations and surfaces

Queue and Now Playing may be full-screen or modal based on platform width. Playback persists while navigating. Dialogs are reserved for decisions/confirmation; bottom sheets are for contextual actions; menus are short and keyboard/screen-reader navigable.
