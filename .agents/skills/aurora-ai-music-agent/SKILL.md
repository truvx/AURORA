---
name: aurora-ai-music-agent
description: Design or review AURORA's Gemini/OpenAI music agent, structured intents, controlled tools, account connections, and secure token handling.
---

# AURORA AI music agent

Use for Gemini/OpenAI integration, natural-language music intent, tool schemas, account UI, prompt boundaries, or AI failures. Read `docs/AI_ARCHITECTURE.md` and `docs/SECURITY.md`.

## Provider-neutral AI boundary

Expose an `AIProvider` interface for Gemini and OpenAI. Providers return validated structured output, never arbitrary application commands or media URLs. The application authorizes and executes tools through a typed allowlist:

`searchMusic`, `searchArtist`, `searchAlbum`, `searchRelatedMusic`, `getTrackMetadata`, `findSimilarMusic`, `recommendMusic`, `createQueue`, `playTrack`, `playAlbum`, `playPlaylist`, `addToQueue`, and `createPlaylist`.

Validate tool name, JSON schema, argument limits, user intent, provider capability, and confirmation requirements before execution. The LLM cannot invent a playable URL, invoke arbitrary code, read local secrets, or bypass provider policy.

## Intent example

```json
{
  "intent": "discover_music",
  "reference": "Blinding Lights",
  "mood": "dark",
  "energy": "low",
  "similarity": "high"
}
```

Resolve this intent through provider search and deterministic ranking. Show provenance and uncertainty when useful.

## Accounts

The UI presents `AI Accounts` with `Gemini / Connect Google` and `ChatGPT / Connect ChatGPT`, plus `Connected / Not Connected`. Use supported Google authorization and supported OpenAI/ChatGPT sign-in or API configuration paths as applicable. Identity login is distinct from API billing/API-key access. Never inspect another app's storage, cookies, tokens, or private credentials. Never require secrets in source code.

## Reliability

AI is optional. Offline or unavailable AI must leave search, playback, library, and deterministic recommendations usable. Bound prompt size, tool-call count, retries, and latency. Redact private listening history unless the user has enabled the relevant feature and the provider contract permits it.
