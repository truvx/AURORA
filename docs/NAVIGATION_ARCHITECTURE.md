# AURORA navigation architecture

## Information architecture

The consistent product map is `Home`, `Search`, `Library`, `AI`, and `Settings` as primary destinations. Within Library: `Favorites`, `Playlists`, `Albums`, `Artists`, `Downloads/Imports`, and `Recently Played/Added`. `Queue` and `Now Playing` are global playback surfaces, not duplicated per tab.

## Android

Use a typed navigation graph with stable destination arguments. Compact widths may use bottom navigation; wider layouts may use a navigation rail or adaptive two-pane layout. Queue can be a bottom sheet or full destination; Now Playing can be a full destination or expanded player based on width and accessibility settings. The canonical player coordinator lives above screen destinations and survives route changes/process lifecycle according to platform policy.

## Web

Use URL-addressable destinations for Home, Search/Results, Artist, Album, Playlist, Library subsections, AI, and Settings. The player coordinator is mounted at the app shell, while the mini-player is a persistent region and Now Playing is a route/overlay with a shareable URL where appropriate. Browser back/forward restores route state without creating a second player.

## Modal and transient destinations

- Bottom sheets: queue, contextual track actions, quality/normalization selectors, filters.
- Dialogs: destructive confirmation, auth/privacy decisions, blocking errors.
- Menus: short action sets anchored to a row/control.
- Full-screen: Now Playing, AI Conversation, Settings categories on compact screens.

Sheets/dialogs trap and restore focus, support keyboard escape/back, and never hide a critical error behind an unannounced dismissal.

## Deep links

Deep links resolve provider/source/ID and optional versioned context through a typed resolver. Invalid, unavailable, unauthorized, or stale items land on a safe error/empty state with recovery; they never become arbitrary playback URLs. Deep-link navigation does not bypass capability checks or confirmation.

## Playback persistence while navigating

The player state and queue are application-level. Route transitions do not pause playback by default. The current track, play/pause state, progress, queue, and errors project to every surface. Screen-local state is disposable and never becomes a competing source of truth.

## Navigation tests

Test deep links, back/forward/back behavior, sheet/dialog focus, keyboard/back dismissal, process/background restoration, player continuity, invalid provider IDs, unavailable capabilities, and compact/wide layout projections.
