---
name: aurora-recommendations
description: Design or review AURORA's hybrid deterministic and AI-assisted recommendation engine, ranking signals, explanations, and offline fallback.
---

# AURORA recommendations

Use for recommendation candidates, ranking, listening signals, explanations, personalization, or AI-assisted discovery. Read `docs/RECOMMENDATION_ENGINE.md`.

## Hybrid design

Use deterministic candidate generation and ranking as the reliable baseline. Signals may include completion, skips, likes, repeats, playlists, artists, genres, language, tempo, mood, similarity, recency, session context, and optional time-of-day. Every signal must have an explicit privacy setting, freshness rule, and testable weight or feature transform.

AI may parse natural language, generate semantic candidates, explain a result, or propose a mood/energy constraint. It must not be the sole ranking mechanism and must not return untrusted URLs.

## Ranking contract

Make ranking a pure function over versioned inputs where possible. Preserve deterministic tie-breaking, provider/source provenance, diversity constraints, and exclusion rules for unavailable or already-rejected items. Log aggregate reason codes, not private prompts or raw histories.

## Graceful degradation

If AI is offline, rate-limited, not connected, or policy-blocked, use local history, library metadata, and provider-supported discovery. Display a truthful explanation such as “Based on your listening history” rather than implying AI participation.

## Test requirements

Cover empty history, sparse history, repeated tracks, all-skipped candidates, ties, unavailable metadata, provider limits, privacy-disabled signals, AI unavailable, and stable ordering across runs.
