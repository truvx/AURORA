# AURORA recommendation specification

Status: final v1 recommendation contract. Deterministic generation/ranking remains available without AI.

## Pipeline

```text
signals and context
  → candidate generation
  → eligibility filtering
  → feature normalization
  → deterministic ranking
  → diversity/novelty constraints
  → explanation/provenance
  → presentation
```

Candidate generation and ranking are separate contracts. AI can contribute semantic candidates and intent parsing but is not required for ranking or playback.

## Inputs

Listening history, completion percentage, skips, likes, replays, favorites, playlists, artists, genres, language, mood, tempo, energy, recency, session context, and optional time-of-day. Each input has a privacy toggle, retention rule, freshness window, and fallback when unavailable.

## Candidate sources

- Local library similarity and favorites.
- Recently played/recently added continuation.
- User playlists and artist/album relationships.
- Provider-supported recommendations and related items.
- AI semantic candidate generation from an authorized prompt/context.

Every candidate retains provider/source identity, availability, reason codes, and confidence/provenance.

## Ranking

Rank with a versioned deterministic function where possible. Normalize feature ranges, apply explicit weights, penalize recent skips/repeats according to policy, add novelty/diversity constraints, remove unavailable or blocked items, and use stable source/ID tie-breaking. Do not let a model’s natural-language ordering silently override the ranking contract.

## Explanations

Explain with truthful reason codes such as `Because you liked this artist`, `Similar tempo and mood`, `Recently added`, or `Provider related result`. If AI contributed, say so only when it actually did; do not expose private signal details unnecessarily.

## No-AI mode

With AI offline, unavailable, unconnected, rate-limited, or declined, AURORA uses local history/library metadata and provider-supported discovery. Core Home, Search, queue, and playback remain functional.

## Safety/privacy

Listening history is sensitive personal data. Keep it local by default, allow deletion/retention controls, minimize sync, and do not send complete history for a single recommendation. Respect source/provider rules and never turn candidates into arbitrary media URLs.

## Tests and evaluation

Test empty/sparse history, repeated tracks, all-skipped candidates, ties, missing features, disabled privacy signals, unavailable providers, AI candidate injection, deterministic ordering, diversity, and explanation truthfulness. Offline and seeded fixtures must make ranking reproducible.
