import { createRemoteJWKSet, jwtVerify, type JWTPayload, type JWTVerifyGetKey } from "jose";

/**
 * Why an ID token was not accepted, in terms the caller can act on.
 *
 * Typed rather than a message because the responses differ: an expired token wants a
 * refresh, a wrong audience wants the client ID fixed, and an account that is simply not
 * on the list wants a person to add it. A single "unauthorized" hides all three from
 * whoever is trying to deploy this.
 */
export type IdTokenFailure =
  | "not_configured"
  | "malformed"
  | "untrusted"
  | "expired"
  | "wrong_audience"
  | "not_allowed"
  | "keys_unavailable";

export type IdTokenResult =
  | { ok: true; subject: string; email?: string }
  | { ok: false; reason: IdTokenFailure };

/**
 * Google's signing keys, fetched once and cached by `jose` per its own Cache-Control.
 *
 * Module scope on purpose: a set rebuilt per request would fetch Google's certificates on
 * every call, which is both a needless dependency on their availability and a way to get
 * rate limited by the thing you rely on to authenticate.
 */
let cachedKeys: JWTVerifyGetKey | undefined;

function googleKeys(): JWTVerifyGetKey {
  cachedKeys ??= createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
  return cachedKeys;
}

/** Google issues both spellings, and has for years. Both are it; nothing else is. */
const GOOGLE_ISSUERS = ["https://accounts.google.com", "accounts.google.com"];

export interface IdTokenConfig {
  /** The OAuth client ID the token must have been issued for. */
  audience?: string;
  /**
   * Who is allowed through, by email or by Google subject id.
   *
   * There is no "anyone with a Google account" mode, and that is the point. Without this,
   * publishing the gateway would hand the provider keys - and the bill behind them - to
   * every person on the internet willing to sign in. An empty list means ID-token auth is
   * switched off, not that everyone is welcome.
   */
  allowed?: string[];
}

export function readConfig(env: Record<string, string | undefined> = process.env): IdTokenConfig {
  return {
    audience: env.AURORA_GOOGLE_CLIENT_ID?.trim() || undefined,
    allowed: (env.AURORA_ALLOWED_ACCOUNTS ?? "")
      .split(",")
      .map((entry) => entry.trim().toLowerCase())
      .filter(Boolean),
  };
}

/** Whether ID-token auth is usable at all. Both halves are required; one alone is a hole. */
export function isConfigured(config: IdTokenConfig): boolean {
  return Boolean(config.audience) && (config.allowed?.length ?? 0) > 0;
}

/** A JWT has three dot-separated parts. A shared secret does not, so the two never collide. */
export function looksLikeJwt(token: string): boolean {
  return token.split(".").length === 3;
}

/**
 * Verifies a Google ID token and says whose it is.
 *
 * Signature, issuer, audience and expiry are all checked by `jose` against Google's
 * published keys - the algorithm comes from the key that matched, never from the token's
 * own header, which is what stops a token from nominating its own verification. The
 * allowlist is checked afterwards, because a valid token from the wrong account is a
 * different answer from an invalid one.
 */
export async function verifyGoogleIdToken(
  token: string,
  config: IdTokenConfig,
  keys: JWTVerifyGetKey = googleKeys()
): Promise<IdTokenResult> {
  if (!isConfigured(config)) return { ok: false, reason: "not_configured" };

  let payload: JWTPayload;
  try {
    const verified = await jwtVerify(token, keys, {
      issuer: GOOGLE_ISSUERS,
      audience: config.audience,
      algorithms: ["RS256"],
    });
    payload = verified.payload;
  } catch (error) {
    return { ok: false, reason: classify(error) };
  }

  const subject = payload.sub;
  if (!subject) return { ok: false, reason: "malformed" };
  const email = typeof payload.email === "string" ? payload.email : undefined;

  const allowed = config.allowed ?? [];
  const matches =
    allowed.includes(subject.toLowerCase()) ||
    (email !== undefined && allowed.includes(email.toLowerCase()));
  if (!matches) return { ok: false, reason: "not_allowed" };

  /*
   * An unverified email is not an identity.
   *
   * Google will issue a token for an account whose address it has not confirmed, and an
   * allowlist keyed on addresses would then be satisfiable by anyone willing to claim one.
   * The subject id is always safe to match on; an address only counts once Google says it
   * is really theirs.
   */
  if (!allowed.includes(subject.toLowerCase()) && payload.email_verified !== true) {
    return { ok: false, reason: "not_allowed" };
  }

  return { ok: true, subject, email };
}

function classify(error: unknown): IdTokenFailure {
  const code = (error as { code?: string })?.code;
  switch (code) {
    case "ERR_JWT_EXPIRED":
      return "expired";
    case "ERR_JWT_CLAIM_VALIDATION_FAILED":
      return (error as { claim?: string }).claim === "aud" ? "wrong_audience" : "untrusted";
    case "ERR_JWKS_NO_MATCHING_KEY":
    case "ERR_JWS_SIGNATURE_VERIFICATION_FAILED":
      return "untrusted";
    case "ERR_JWKS_TIMEOUT":
    case "ERR_JWKS_MULTIPLE_MATCHING_KEYS":
      return "keys_unavailable";
    case "ERR_JWS_INVALID":
    case "ERR_JWT_INVALID":
      return "malformed";
    default:
      return "untrusted";
  }
}
