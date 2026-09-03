---
name: aurora-web
description: Design or review AURORA's React/TypeScript web client, browser playback boundary, shared contracts, responsive UI, and IndexedDB usage.
---

# AURORA web

Use for the web client, React/TypeScript architecture, browser playback, Web Audio, IndexedDB, responsive UI, or web accessibility. Read `docs/WEB_ARCHITECTURE.md` and `docs/DESIGN_SYSTEM.md`.

## Shared concepts, separate implementations

Share domain concepts, metadata models, provider contracts, and design tokens with Android where practical. Do not import Android/Media3 classes into web code. Keep browser audio and storage behind web platform adapters.

Preferred direction is React + TypeScript, with Next.js where it materially helps routing/server boundaries. Use browser media APIs only where permitted and supported. Do not implement a prohibited YouTube extraction path just because browser playback APIs are limited.

## Web rules

- Keep server-only secrets out of client bundles and validate external JSON at boundaries.
- Use IndexedDB for bounded local metadata/library state when justified; do not put large audio blobs in React state.
- Keep a single web playback coordinator and canonical state, mirroring the Android architecture.
- Handle autoplay policy, visibility/lifecycle, media-session support, keyboard controls, focus, reduced motion, responsive breakpoints, and offline/error states honestly.
- Lazy-load artwork and lists, reserve image space, cancel fetches, and avoid unnecessary re-renders.
