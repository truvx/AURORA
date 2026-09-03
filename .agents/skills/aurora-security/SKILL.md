---
name: aurora-security
description: Review or design AURORA security, privacy, provider authorization, token storage, AI tool authorization, local files, and logging.
---

# AURORA security

Use for authentication, account connections, secrets, local files, provider integrations, AI tools, permissions, network clients, logs, or dependency risk. Read `docs/SECURITY.md` and use the installed security/review skills for changed code.

## Absolute rules

- Never hardcode or commit API keys, OAuth secrets, tokens, cookies, or private URLs.
- Never read another app's private storage, extract credentials, scrape cookies, or fake authentication.
- Use supported Google and OpenAI/ChatGPT authorization flows; distinguish identity from API-key access.
- Store tokens in platform secure storage; define expiry, refresh, revocation, logout, and failure behavior.
- Keep backend secrets out of web bundles. Do not log credentials, authorization headers, raw media URLs, private prompts, or full listening history.
- Treat provider responses, file metadata, AI output, and URLs as untrusted until validated at a typed boundary.
- Do not disable TLS/certificate validation or bypass provider/DRM restrictions.

## AI authorization

Validate structured tool calls against an allowlist, schema, provider capabilities, user intent, and confirmation policy. A tool may only operate on items resolved by AURORA's provider layer. The LLM never receives arbitrary control over application internals.

## Local files

Validate URI permissions, file type/size where importing, path construction, ownership, retention, and metadata parsing. Avoid copying large media unnecessarily. Keep private library data local unless the user explicitly enables an allowed sync/AI feature.
