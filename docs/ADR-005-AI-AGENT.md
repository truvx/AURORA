# ADR-005: Provider-neutral controlled AI agent

## Status

Accepted as foundation; authentication and gateway boundaries are finalized by ADR-012.

## Context

AURORA should support natural-language discovery and queue actions through Gemini and OpenAI without letting an LLM control arbitrary application state, invent media URLs, or receive private data by default.

## Decision

Implement a provider-neutral `AIProvider` and an application-owned allowlist of typed tools. AI responses are untrusted structured proposals. The app validates schema, bounds, authorization, provider capability, source identity, privacy context, and confirmation before execution.

## Alternatives considered

- Direct provider SDK calls from UI — rejected for coupling and secret leakage.
- LLM-generated URLs/commands — rejected for security, policy, and correctness.
- AI-only recommendations — rejected because offline usability and determinism are required.

## Consequences

Tool schemas and validation add design work, but actions are auditable and provider-safe. AI failures require a deterministic fallback.

## Future migration

Additional AI providers can implement the same interface. Tool schemas may be versioned; authorization policy must remain application-owned.
