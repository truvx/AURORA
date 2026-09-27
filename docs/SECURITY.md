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

As implemented (`web/app/api/ai/intent/route.ts`, `web/lib/ai/guard.ts`, `web/lib/ai/googleIdToken.ts`): every request needs `Authorization: Bearer <credential>`, and that credential is either the shared `AURORA_GATEWAY_TOKEN` or a Google ID token. The two never compete for the same string - a JWT has three dot-separated parts and a shared secret does not, so the shape decides which check runs. An ID token is verified against Google's published keys for signature, issuer, audience and expiry, and then against `AURORA_ALLOWED_ACCOUNTS`; both `AURORA_GOOGLE_CLIENT_ID` and a non-empty allowlist are required, because a client ID alone would accept an ID token from every Google account there is. An email address only satisfies the allowlist when Google reports it verified; a subject id always does. The reason a token was refused is logged for whoever runs the gateway and never returned, so it cannot be used to discover which accounts exist. With neither credential configured the gateway refuses everything rather than running open. Requests are capped at 16 KB and 20 per minute across the gateway (in memory, so per server process). Client `context` is a map of at most 20 short strings and travels in the user turn marked as data, never in the system instruction. Failures return a stable `error.code`. The Android client stores the token encrypted with an Android Keystore key, entered in Settings; it is never compiled into the app.

As implemented (`android/.../data/security/SecretStore.kt`): both client-held secrets - the gateway token and the YouTube Data API key - are entered in Settings and stored as AES-GCM ciphertext under a per-secret Android Keystore alias. Neither is a `buildConfigField`. A key compiled into `BuildConfig` is a plaintext string in `classes.dex`, readable from the APK with `grep`, and no amount of restricting it in the Cloud console changes the fact that it shipped. Where a feature needs a key that is not set, it fails with a typed reason (`DiscoveryUnavailable.Reason.NO_API_KEY`) rather than silently returning nothing. The key is read per call, not captured at construction, so entering one in Settings takes effect without a restart.

## Local data

Respect URI permissions and ownership. Minimize cloud sharing of local-library metadata/history. Redact telemetry. Keep sensitive processing local by default and make optional sharing explicit.

Listening history, playlists, local paths/URIs, technical metadata, conversations, and account state are sensitive. Define retention, deletion/export, backup, and opt-in sharing behavior before implementing sync or AI context. Do not put raw local paths or full history into analytics. Local imports validate type/size/ownership and avoid unsafe path construction; original media is not rewritten by normalization.

As implemented (`android/app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`): the app allows backup, so settings survive a reinstall, but `aurora-database` and its write-ahead log are excluded from cloud backup on every API level. Listening history, completions and skips do not reach a Google account. Device transfer is treated as a separate decision and keeps the library, because it is a direct person-initiated move to their next phone with nothing left on a server. The encrypted secrets are excluded from both: the Keystore key that decrypts them does not travel, so the ciphertext would restore useless. Measured with the local backup transport: 592,896 bytes leave the device without these rules, 219,136 bytes with them.

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

## Signing in to Google

AURORA can authorize YouTube Data API calls with an OAuth access token instead of a pasted
API key. What that buys is narrow, and worth stating because the obvious reading is wrong:
a Google account is an identity, not an entitlement. The quota spent still belongs to the
Google Cloud project the client ID came from, and a consumer account brings none of its
own. Signing in replaces the typing, not the project.

The flow is AppAuth's authorization code flow with PKCE and state, against Google's
published endpoints, with a redirect derived from the client ID so the two cannot disagree.
Scopes are the least that will do the job: `openid` and `email` so a server can be told who
is asking, and `youtube.readonly` because nothing here uploads, rates, subscribes or
deletes.

The OAuth client ID is not a secret - installed-app clients have none by design, which is
what PKCE stands in for - so it ships in the APK, as it must. It is read from
`local.properties` rather than written into the build file only so that one person's Cloud
project identifier is not committed to a shared repository. With none configured, the
sign-in option is not offered and the API key remains the way in.

The session - access token, refresh token and ID token - is held in the Keystore-backed
`SecretStore`, not in the ordinary preferences AppAuth would otherwise use: the refresh
token is long-lived and is what lets anyone holding it mint access tokens. Signing out
revokes the grant with Google as well as clearing it locally, and clears locally even if
the revocation cannot be delivered, because someone who signed out must not still be
signed in.

A signed-in session takes precedence over a stored key, and suppresses it: Google rejects a
request carrying both an API key and a bearer token. A session that has expired and cannot
refresh falls back to the key rather than going dark.
