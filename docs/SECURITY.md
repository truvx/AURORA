# AURORA security and privacy

Status: final v1 security boundary. AI credential details are finalized in `ADR-012-AI-AUTHENTICATION.md`; this document remains the cross-cutting security contract.

## Threat model and trust zones

AURORA crosses several trust boundaries: user input and local files, provider/network responses, AI model output, platform authentication, ordinary persistence, secure token storage, and platform playback. Every boundary validates data before it is used, and no UI visibility rule is treated as authorization.

## Secrets and auth

Never hardcode, commit, log, or bundle secrets, tokens, cookies, OAuth credentials, authorization headers, or private URLs. Use the supported Google OAuth/API boundary and OpenAI API credential boundary documented in ADR-012; do not assume consumer Gemini or ChatGPT sessions grant API access. Do not inspect another app’s storage or extract its credentials. Store tokens in Android secure storage, Web secure session handling, or server secret management as appropriate, with expiry, refresh, revocation, logout, and failure handling.

Authentication design distinguishes user identity from provider/API access. Redirect/state/PKCE or equivalent protections, exact redirect allowlists, scoped permissions, token audience/issuer checks, refresh rotation where supported, revocation, and account disconnect follow the selected provider documentation during implementation. No secret is pasted into source, ordinary preferences, web bundles, logs, test fixtures, or screenshots.

## Trust boundaries

Validate provider responses, file metadata, URLs, AI output, and user input at typed boundaries. The AI tool allowlist is the only application control surface and checks schema, capability, authorization, confirmation, and resolved provider identity. Never turn an arbitrary URL into trusted media.

Provider credentials are isolated per adapter and never exposed to presentation. The AURORA AI gateway is the production boundary for Gemini/OpenAI secrets; clients receive only scoped, minimum-necessary results. Apply input size/rate limits, safe error mapping, bounded retries, and timeout/cancellation behavior to network and AI operations. Never disable TLS/certificate validation.

## Local data

Respect URI permissions and ownership. Minimize cloud sharing of local-library metadata/history. Redact telemetry. Keep sensitive processing local by default and make optional sharing explicit.

Listening history, playlists, local paths/URIs, technical metadata, conversations, and account state are sensitive. Define retention, deletion/export, backup, and opt-in sharing behavior before implementing sync or AI context. Do not put raw local paths or full history into analytics. Local imports validate type/size/ownership and avoid unsafe path construction; original media is not rewritten by normalization.

## Logging and observability

Allowlist operational fields such as event category, stable non-secret correlation ID, elapsed time, retryability, and coarse failure reason. Redact authorization headers, tokens, cookies, raw provider/media URLs, private prompts, file paths, personal metadata, and full listening history. Test redaction and inspect crash reports before enabling telemetry.

## AI data and tool authorization

AI context is opt-in, minimized, bounded, and filtered by provider contract. The application validates tool names, typed arguments, resolved provider/source IDs, capabilities, user confirmation, and durable-write permissions before execution. Model output never directly mutates the player, database, account store, or filesystem.

## Platform/provider compliance

Do not disable TLS or certificate validation, bypass DRM, scrape cookies, download/extract YouTube audio, or evade provider policies. Audit dependencies and scripts before adoption. Security review must trace identity/input through storage, async work, logs, and output.

## Verification checklist

- [ ] Supported auth flows and redirect/state protections are verified against current provider documentation.
- [ ] Tokens have secure storage, expiry/refresh, revoke/logout, and failure handling.
- [ ] AI context and listening-history retention are explicit and user-controlled.
- [ ] Tool calls reject arbitrary URLs, unknown tools, malformed arguments, and unauthorized durable writes.
- [ ] Local files/URIs are validated and scoped to user ownership.
- [ ] Logs/crash reports redact secrets and sensitive music data.
- [ ] TLS/security validation remains enabled and dependency risk is reviewed.
