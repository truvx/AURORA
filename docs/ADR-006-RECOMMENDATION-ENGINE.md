# ADR-006: Hybrid deterministic and AI-assisted recommendations

## Status

Accepted as foundation; deterministic fallback remains mandatory.

## Context

AI improves natural-language and semantic discovery, but an LLM-only recommender is non-deterministic, privacy-sensitive, and unavailable offline.

## Decision

Separate candidate generation from ranking. Use local/provider sources and optional AI semantic candidates, then apply deterministic eligibility, normalized features, diversity, recency, and stable tie-breaking. Keep provenance and reason codes.

## Alternatives considered

- LLM-only ranking — rejected for reproducibility, offline behavior, latency, and privacy.
- Pure history-only ranking — rejected because it misses semantic discovery and cold-start opportunities.
- Unversioned heuristics — rejected because regression testing and tuning require explicit versions.

## Consequences

Signal/privacy policy and evaluation fixtures are required. AI can enrich discovery without becoming a critical dependency.

## Future migration

More sophisticated ranking models may replace the deterministic function behind a versioned contract, with offline evaluation and a deterministic fallback retained.
