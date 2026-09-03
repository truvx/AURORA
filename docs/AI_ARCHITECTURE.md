# AURORA AI architecture

Status: final v1 boundary. Gemini and OpenAI calls are gateway-mediated; consumer app sessions are not reused.

## Boundary

`AIProvider` has Gemini and OpenAI implementations. They return validated structured intents/tool calls, not arbitrary application commands, URLs, or code. The application owns authorization, provider resolution, and execution.

## Tool allowlist

`searchMusic`, `searchArtist`, `searchAlbum`, `searchRelatedMusic`, `getTrackMetadata`, `findSimilarMusic`, `recommendMusic`, `createQueue`, `playTrack`, `playAlbum`, `playPlaylist`, `addToQueue`, and `createPlaylist`. Each tool has a strict schema, input bounds, provider-capability check, confirmation policy, and typed result.

For “Play something like Blinding Lights but darker and slower”, a valid intent can be:

```json
{"intent":"discover_music","reference":"Blinding Lights","mood":"dark","energy":"low","similarity":"high"}
```

The app resolves tracks; the model never invents a media URL.

## Accounts

Show AI Accounts with provider connection state and a clear distinction between AURORA identity, Google Cloud/Gemini API access, and OpenAI API access. Do not expose a consumer ChatGPT sign-in assumption. Use supported authorization paths. Identity login and API billing/key access are separate concepts. Never read another app’s private storage, cookies, credentials, or tokens. Store tokens securely and define refresh/revoke/logout/error states.

## Reliability and privacy

AI is optional. Offline/unavailable/rate-limited AI falls back to normal search and deterministic recommendations. Bound prompt size, tool count, retries, and latency. Send listening history only when enabled, necessary, and permitted by the provider contract; redact private data from logs.
