import { beforeAll, describe, expect, it } from "vitest";
import { SignJWT, generateKeyPair, type JWTVerifyGetKey } from "jose";
import {
  isConfigured,
  looksLikeJwt,
  readConfig,
  verifyGoogleIdToken,
  type IdTokenConfig,
} from "../googleIdToken";

/**
 * Verifying a Google ID token.
 *
 * Signed with a real key pair rather than stubbed, because the properties worth testing
 * are the ones a stub would assume: that a token signed by someone else is refused, that
 * one issued for a different application is refused, and that a perfectly valid token from
 * an account nobody allowed is still refused.
 */
describe("verifyGoogleIdToken", () => {
  const AUDIENCE = "1234.apps.googleusercontent.com";
  const SUBJECT = "109876543210";

  let privateKey: CryptoKey;
  let keys: JWTVerifyGetKey;
  let strangerKey: CryptoKey;
  let config: IdTokenConfig;

  beforeAll(async () => {
    const pair = await generateKeyPair("RS256");
    privateKey = pair.privateKey;
    // A resolver over one known key, standing in for Google's published certificates.
    keys = (async () => pair.publicKey) as unknown as JWTVerifyGetKey;

    strangerKey = (await generateKeyPair("RS256")).privateKey;
    config = { audience: AUDIENCE, allowed: ["listener@example.com"] };
  });

  const token = (
    claims: Record<string, unknown> = {},
    signer: CryptoKey = privateKey,
    issuer = "https://accounts.google.com"
  ) =>
    new SignJWT({ email: "listener@example.com", email_verified: true, ...claims })
      .setProtectedHeader({ alg: "RS256" })
      .setIssuer(issuer)
      .setAudience(AUDIENCE)
      .setSubject(SUBJECT)
      .setIssuedAt()
      .setExpirationTime("1h")
      .sign(signer);

  it("accepts a token Google signed for this application and this account", async () => {
    const result = await verifyGoogleIdToken(await token(), config, keys);

    expect(result).toEqual({ ok: true, subject: SUBJECT, email: "listener@example.com" });
  });

  it("refuses a token signed by someone else", async () => {
    // The whole point of the signature: anyone can mint claims, only Google can sign them.
    const forged = await token({}, strangerKey);

    expect(await verifyGoogleIdToken(forged, config, keys)).toEqual({
      ok: false,
      reason: "untrusted",
    });
  });

  it("refuses a token issued for a different application", async () => {
    const elsewhere = await new SignJWT({ email: "listener@example.com", email_verified: true })
      .setProtectedHeader({ alg: "RS256" })
      .setIssuer("https://accounts.google.com")
      .setAudience("someone-elses-app.apps.googleusercontent.com")
      .setSubject(SUBJECT)
      .setIssuedAt()
      .setExpirationTime("1h")
      .sign(privateKey);

    const result = await verifyGoogleIdToken(elsewhere, config, keys);
    expect(result.ok).toBe(false);
  });

  it("refuses a token from an issuer that is not Google", async () => {
    const impostor = await token({}, privateKey, "https://accounts.example.com");

    const result = await verifyGoogleIdToken(impostor, config, keys);
    expect(result.ok).toBe(false);
  });

  it("refuses an expired token", async () => {
    const stale = await new SignJWT({ email: "listener@example.com", email_verified: true })
      .setProtectedHeader({ alg: "RS256" })
      .setIssuer("https://accounts.google.com")
      .setAudience(AUDIENCE)
      .setSubject(SUBJECT)
      .setIssuedAt(Math.floor(Date.now() / 1000) - 7200)
      .setExpirationTime(Math.floor(Date.now() / 1000) - 3600)
      .sign(privateKey);

    expect(await verifyGoogleIdToken(stale, config, keys)).toEqual({
      ok: false,
      reason: "expired",
    });
  });

  it("refuses a valid token from an account nobody allowed", async () => {
    // Without this, publishing the gateway hands the provider keys, and the bill behind
    // them, to everyone on the internet willing to sign in to Google.
    const stranger = await token({ email: "someone.else@example.com" });

    expect(await verifyGoogleIdToken(stranger, config, keys)).toEqual({
      ok: false,
      reason: "not_allowed",
    });
  });

  it("does not let an unverified address satisfy an allowlist", async () => {
    // Google will issue a token for an address it has not confirmed. Matching on one would
    // let anyone willing to claim the address through.
    const unverified = await token({ email_verified: false });

    expect(await verifyGoogleIdToken(unverified, config, keys)).toEqual({
      ok: false,
      reason: "not_allowed",
    });
  });

  it("allows matching on the subject id, which is always Google's own", async () => {
    const bySubject: IdTokenConfig = { audience: AUDIENCE, allowed: [SUBJECT] };
    const unverified = await token({ email_verified: false });

    expect((await verifyGoogleIdToken(unverified, bySubject, keys)).ok).toBe(true);
  });

  it("is switched off rather than open when half configured", async () => {
    // An audience with no allowlist would accept every Google account there is.
    expect(isConfigured({ audience: AUDIENCE, allowed: [] })).toBe(false);
    expect(isConfigured({ audience: undefined, allowed: ["a@b.c"] })).toBe(false);
    expect(await verifyGoogleIdToken(await token(), { audience: AUDIENCE }, keys)).toEqual({
      ok: false,
      reason: "not_configured",
    });
  });
});

describe("readConfig", () => {
  it("reads the allowlist as a case-insensitive comma-separated list", () => {
    const config = readConfig({
      AURORA_GOOGLE_CLIENT_ID: " 1234.apps.googleusercontent.com ",
      AURORA_ALLOWED_ACCOUNTS: "One@Example.com , two@example.com,  ",
    });

    expect(config.audience).toBe("1234.apps.googleusercontent.com");
    expect(config.allowed).toEqual(["one@example.com", "two@example.com"]);
  });

  it("treats an absent allowlist as empty rather than as everyone", () => {
    expect(readConfig({}).allowed).toEqual([]);
  });
});

describe("looksLikeJwt", () => {
  it("tells a JWT apart from a shared secret so the two never collide", () => {
    expect(looksLikeJwt("aaa.bbb.ccc")).toBe(true);
    expect(looksLikeJwt("a-long-shared-gateway-secret")).toBe(false);
    expect(looksLikeJwt("two.parts")).toBe(false);
  });
});
