# AURORA web product architecture

Status: final v1 Web product boundary. React/TypeScript and Next.js are separate from Android playback/storage implementations.

## Information architecture

The web mirrors Android’s concepts: Home, Search, Library, Favorites, Playlists, Albums, Artists, Downloads/Imports, Queue, Now Playing, AI, and Settings. Layout adapts from compact/mobile navigation to a wider persistent navigation rail/sidebar without changing domain meaning.

## Runtime layers

React/TypeScript presentation uses application state holders and shared contracts in Next.js App Router. A web player coordinator wraps browser media APIs and Media Session where supported. IndexedDB stores bounded metadata/library/playlists/history/settings; React state stores views and small transient state, never large audio blobs. Server components/API routes remain separate from client secrets and playback.

## Playback

One web coordinator owns media resources and publishes canonical `PlayerState`. Autoplay policy failures are explicit. Visibility changes, browser suspension, Media Session actions, keyboard controls, route changes, and network changes enter through typed events. Browser limitations are reflected in capabilities.

## Authentication and AI

Use supported web authorization flows. Keep server-only credentials server-side; browser code receives short-lived, scoped results. Gemini/OpenAI remain behind `AIProvider` and the same tool authorization boundary as Android. Never store provider tokens in localStorage when a safer supported storage/session path exists.

## Provider integration

YouTube integration uses supported browser/provider playback surfaces. Local imports use browser-supported file APIs and IndexedDB metadata references where justified. The web does not implement an extraction or unauthorized download workaround.

## Responsive and accessible behavior

Use the shared semantic design tokens with platform-specific layout adaptations. Support keyboard/focus navigation, reduced motion/transparency, readable glass fallback, responsive queue/player controls, reserved artwork space, and explicit loading/empty/offline/error states.

## Synchronization

If Android/web sync is added later, synchronize versioned domain records and intent/state contracts, not live player handles or raw provider sessions. Conflicts use entity-specific rules: playlist order, favorites, history retention, settings precedence, and resume positions are distinct.

## Performance

Lazy-load lists/artwork, cancel stale requests, avoid large React trees subscribed to high-frequency position updates, isolate the scrubber/player clock, and keep IndexedDB/network work asynchronous. Measure startup, search, artwork decode, queue operations, and playback response.
