# AURORA product specification

## Objective

AURORA is a single-user personal music player for Android and web. Its core value is a calm, premium listening experience across user-owned local music and legitimate YouTube discovery/playback, with one consistent queue and player model. AI is an optional assistant for natural-language discovery and queue actions; the app remains useful without it.

## Scope for the first implementation phase

- Android local-file library and high-quality offline playback.
- Supported YouTube discovery/playback integration, subject to applicable API and platform policy.
- Shared provider-neutral metadata, queue, player, settings, and recommendation concepts.
- Original Liquid Glass-inspired visual system with accessibility and reduced-motion paths.
- Gemini/OpenAI connection flows only through supported authorization mechanisms.
- Hybrid deterministic + optional AI recommendations.

Explicitly out of scope for v1: Spotify and Apple Music adapters, unauthorized downloading, YouTube audio extraction, DRM bypass, credential scraping, fake account connection, and fabricated lossless/Hi-Res streams.

## User outcomes

The user can import and browse local music, inspect truthful technical metadata, play offline, control one queue from every surface, discover music through supported providers, set universal quality/normalization preferences, and optionally ask the AI agent to search or shape a queue through controlled tools.

## Product principles

Truthful capabilities; one canonical playback state; local-first privacy; graceful offline behavior; premium but restrained motion; readable glass; observable failures; deterministic behavior where possible; small reversible increments.

## Acceptance gates

No implementation begins until architecture, design, playback, provider, AI, security, testing, and performance decisions are reviewed. Every implemented feature needs focused behavior tests, platform-appropriate build checks, accessibility coverage, and updated docs for changed contracts.
