# AURORA recommendation engine

Status: final v1 specification. Candidate generation and ranking remain separate; AI is optional.

## Hybrid model

Deterministic candidate generation/ranking is the reliable baseline. Signals may include completion, skips, likes, repeats, playlists, artists, genres, language, tempo, mood, similarity, recency, session context, and optional time of day. Each signal needs an opt-in/privacy rule, freshness policy, and testable transform.

AI handles natural-language understanding, semantic candidate generation, explanation, and conversational discovery. It is never the only recommender and never supplies untrusted media URLs.

## Ranking

Prefer a pure, versioned ranking function over normalized inputs. Apply availability, explicit exclusions, diversity, provenance, and deterministic tie-breaking. Record reason codes and aggregate metrics, not raw prompts or complete listening histories.

## Fallbacks and tests

Without AI, use local history/library metadata/provider-supported recommendations. Test empty/sparse history, ties, repeats, skips, unavailable metadata, privacy-disabled signals, provider limits, AI failures, and stable ordering.
