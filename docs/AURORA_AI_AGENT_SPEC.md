# AURORA AI agent specification

Status: final v1 contract. Production Gemini and OpenAI calls go through the AURORA AI gateway described in `ADR-012-AI-AUTHENTICATION.md`; this document defines provider-neutral behavior only.

## Contracts

```text
AIProvider
AIRequest
AIResponse
AIToolCall
AIContext
AIError
```

`AIProvider` accepts bounded, privacy-filtered context and returns validated response envelopes. It is implemented by Gemini and OpenAI adapters; the UI knows only provider-neutral states.

## Request and response rules

`AIRequest` includes user text, optional explicitly enabled context, provider preference, locale, and correlation ID. `AIResponse` contains user-facing text, zero or more structured intents/tool calls, provenance/uncertainty, and safe error state. Raw vendor payloads stay inside the adapter.

Tool calls are parsed as untrusted data. Unknown tools, malformed arguments, out-of-range values, duplicate calls, invalid source IDs, and arbitrary URLs are rejected or require clarification. A successful model response does not imply successful application execution.

## Allowed tools

| Tool | Input intent | Result | Confirmation |
|---|---|---|---|
| `searchMusic` | query, filters, source scope | provider-neutral candidates | no |
| `searchArtist` | artist query | artist candidates | no |
| `searchAlbum` | album query | album candidates | no |
| `searchRelatedMusic` | resolved item + constraints | candidates | no |
| `getTrackMetadata` | resolved item ID | factual metadata | no |
| `findSimilarMusic` | resolved item + similarity/mood | candidates | no |
| `recommendMusic` | context/constraints | ranked candidates | no |
| `createQueue` | validated item IDs/order | queue proposal | before playback if requested |
| `playTrack` | resolved item ID | player command proposal | user action/explicit command |
| `playAlbum` | resolved album ID | queue proposal | explicit user request |
| `playPlaylist` | resolved playlist ID | queue proposal | explicit user request |
| `addToQueue` | resolved item IDs | queue mutation proposal | explicit user request |
| `createPlaylist` | name + resolved item IDs | playlist proposal | confirm before durable write |

No tool accepts a raw media URL as an authoritative source. The provider layer resolves item identity and capability.

## Structured intent example

```json
{
  "intent": "discover_music",
  "reference": "Blinding Lights",
  "mood": "dark",
  "energy": "low",
  "similarity": "high"
}
```

Natural-language requests such as romantic Malayalam songs from the 2010s become typed filters (`language`, `era`, `mood`, `genre`/semantic constraint) and are resolved by search/recommendation services. Ambiguous references are presented for disambiguation.

## One-click play workflow

```text
user request
  → AI intent extraction
  → schema + safety validation
  → provider candidate generation
  → capability/availability validation
  → deterministic ranking and diversity
  → recommendation results with provenance
  → user selects Play All
  → queue creation through PlayerCoordinator
  → confirmed engine playback
```

The AI cannot skip validation or start playback from an invented URL.

## Context and privacy

Context is opt-in by category: current track, library facts, recent history, preferences, and session context. Minimize, summarize, redact, and bound it. Local-only behavior remains available when the user declines sharing. Conversation/history retention is configurable.

## Provider accounts

Gemini uses gateway-managed Gemini API access and supported Google OAuth only when explicit Google Cloud authorization is needed. OpenAI uses gateway-managed OpenAI API access; no generic consumer ChatGPT sign-in is assumed. Identity authentication and API billing/key access are separate. UI states are `Not Connected`, `Connecting`, `Connected`, `Expired`, `Revoked`, and `Unavailable`.

## Failure handling

Typed errors include not connected, consent denied, expired auth, rate limited, network unavailable, provider unavailable, invalid response, unsafe tool call, context unavailable, and execution failure. AI failure falls back to deterministic search/recommendations and does not disrupt existing playback.

## Testing

Test prompt-to-intent fixtures, schema rejection, tool authorization, provider capability mismatch, ambiguous entity resolution, confirmation requirements, offline fallback, privacy-disabled context, retries/cancellation, and one-click queue handoff.
