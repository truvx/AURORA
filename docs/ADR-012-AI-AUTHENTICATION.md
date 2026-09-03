# ADR-012: Gemini and OpenAI Authentication

- Status: Accepted
- Date: 2026-09-03

## Decision

Production Android and Web clients call an AURORA AI gateway. The gateway owns provider credentials, provider selection, request budgets, redaction, validation, and audit-safe telemetry. Gemini and OpenAI remain implementations behind the provider-neutral `AIProvider` interface.

The clients never ship a Gemini or OpenAI secret, never read another app's session, and never treat an installed consumer app or consumer login as API authorization. AURORA account identity, Google identity, ChatGPT identity, and model/API billing are separate concerns.

### GeminiProvider

The gateway may use a Gemini API key associated with a Google Cloud project, or an explicitly configured Google OAuth credential where user/project authorization is required. OAuth is not the consumer Gemini app session. Cloud project ownership, quota, billing, token refresh, and revocation are gateway responsibilities. The Google AI Studio/Gemini API documentation explicitly warns against exposing keys in mobile or browser clients.

### OpenAIProvider

The gateway uses an OpenAI API project credential, sent as a bearer API key to the OpenAI API. A ChatGPT subscription or the ChatGPT app is not treated as an OpenAI API credential or API billing grant. The current official API documentation consulted does not establish a general standalone-app "Sign in with ChatGPT" flow that grants AURORA model access, so AURORA does not select one for v1. The ChatGPT Apps SDK OAuth flow is for ChatGPT-hosted apps/MCP servers and is not repurposed as external AURORA API authentication.

## Client and gateway contract

1. Android/Web authenticate to AURORA using the app's supported account/session mechanism; that mechanism is separate from provider credentials and is not implemented by this ADR.
2. The client sends bounded user intent, permitted context, and a provider selection request to the gateway.
3. The gateway calls `GeminiProvider` or `OpenAIProvider`, validates the response against the AI agent schema, and returns structured candidates or tool calls.
4. The client/domain layer authorizes every tool call against current provider capabilities and app state before execution.
5. Provider failures, quota exhaustion, expired authorization, safety refusal, timeout, and offline state are explicit typed failures. The deterministic music-search/recommendation baseline remains available.

## Alternatives considered

| Option | Decision | Reason |
| --- | --- | --- |
| Client-bundled Gemini/OpenAI keys | Rejected | Mobile and browser bundles cannot protect long-lived provider credentials or control abuse/billing. |
| Reuse the consumer Gemini or ChatGPT app session | Rejected | Cross-app private storage/cookies/tokens are inaccessible and must never be scraped; consumer identity is not API authorization. |
| Direct client calls with user-supplied API keys | Rejected for production | It exposes the user's credential to the app/runtime and makes policy, quota, redaction, and revocation harder to control. |
| AURORA AI gateway | Chosen | Keeps secrets and policy enforcement server-side while preserving a provider-neutral client contract. |

Assumptions: AURORA can operate a small authenticated gateway for production AI access; single-user local development can configure provider credentials out-of-band on that gateway. Confidence: high for the secret boundary and provider/API distinction; medium for future identity products because no generic external Sign in with ChatGPT contract was verified in the current official sources.

## Token and privacy rules

- Store gateway/provider secrets only in server secret management or platform secure storage when a user credential is explicitly required.
- Never log API keys, OAuth codes, refresh tokens, authorization headers, raw prompts containing private history, or full provider responses when they contain sensitive data.
- Use OAuth `state`, PKCE where applicable, exact redirect validation, short-lived access tokens, secure refresh handling, revocation, and least-privilege scopes.
- Do not use consumer app cookies, private databases, shared storage, accessibility scraping, or exported credentials.

## Sources consulted

- [Gemini API key authentication](https://ai.google.dev/gemini-api/docs/api-key)
- [Gemini API OAuth](https://ai.google.dev/gemini-api/docs/oauth)
- [Gemini API billing](https://ai.google.dev/gemini-api/docs/billing)
- [Vertex AI authentication and client-side key warning](https://cloud.google.com/vertex-ai/generative-ai/docs/start/api-keys)
- [OpenAI API overview](https://developers.openai.com/api/docs/overview)
- [OpenAI developer quickstart](https://developers.openai.com/api/docs/quickstart)
- [OpenAI Apps SDK authentication](https://developers.openai.com/apps-sdk/build/auth/)
