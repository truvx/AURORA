# AI Authentication Matrix

Status: final v1 contract, 2026-09-03.

| Provider | Authentication mechanism | Identity access | Model/API access | Subscription relationship | Billing | Token handling | Android | Web | Backend required? | Offline behavior | Failure behavior |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Gemini | Gateway-managed Gemini API key for project access; Google OAuth only when explicit Google Cloud user/project authorization is needed | Optional Google identity is separate from model access; consumer Gemini app session is not read or reused | Gemini API through the gateway and its configured project/model permissions | Consumer Gemini app access is not assumed to grant API access | Google Cloud/AI Studio project billing and quota owned by the configured gateway project | Server secret manager; OAuth access/refresh lifecycle server-side; never bundle keys or log tokens | Calls AURORA gateway over authenticated TLS | Calls AURORA gateway over authenticated TLS; no client key | Yes for production | No network AI; use deterministic search/recommendation and previously persisted safe results | Surface typed offline, quota, auth, timeout, safety, and provider-unavailable states; never silently fabricate results |
| OpenAI | Gateway-managed OpenAI API project API key using bearer authorization | AURORA identity is separate; no generic Sign in with ChatGPT contract selected for v1 | OpenAI API through the gateway and its API project | ChatGPT consumer app/plan is not treated as API authorization or API credit | OpenAI API project billing and limits | Server secret manager; rotate/revoke; never ship or log the key | Calls AURORA gateway over authenticated TLS | Calls AURORA gateway over authenticated TLS; no client key | Yes for production | Same deterministic fallback; no local model is promised | Surface typed offline, quota, auth, timeout, safety, and provider-unavailable states |

## Scope boundary

The `AIProvider` interface is provider-neutral. `GeminiProvider` and `OpenAIProvider` receive validated provider-neutral requests and return validated provider-neutral responses. They may not return arbitrary playable URLs or execute application mutations. The gateway is an infrastructure boundary, not a replacement for domain authorization.

## Official references

- [Gemini API key authentication](https://ai.google.dev/gemini-api/docs/api-key)
- [Gemini API OAuth](https://ai.google.dev/gemini-api/docs/oauth)
- [Gemini API billing](https://ai.google.dev/gemini-api/docs/billing)
- [OpenAI API overview](https://developers.openai.com/api/docs/overview)
- [OpenAI Apps SDK authentication](https://developers.openai.com/apps-sdk/build/auth/)
