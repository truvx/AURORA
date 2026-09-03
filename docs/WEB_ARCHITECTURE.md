# AURORA web architecture

Status: final v1 specification. The web client is a separate React/TypeScript implementation using Next.js App Router and browser-native platform adapters.

## Direction

Preferred stack: React + TypeScript with Next.js App Router. Share domain concepts, metadata models, provider contracts, and design tokens with Android where practical; do not share Android playback/storage abstractions. Keep browser media, Media Session, IndexedDB, and lifecycle behavior behind web adapters.

## Playback

The web has one playback coordinator and canonical state. Respect autoplay restrictions, visibility changes, keyboard controls, focus, Media Session support, provider playback rules, and offline/error states. Do not add a prohibited YouTube extraction path to compensate for browser API limits.

## Storage and performance

Use IndexedDB for bounded local metadata/library state where justified; do not put large audio blobs in React state. Keep server-only secrets out of bundles. Validate external JSON, cancel requests, lazy-load artwork/lists, reserve image space, avoid unnecessary re-renders, and preserve responsive/accessibility behavior.
