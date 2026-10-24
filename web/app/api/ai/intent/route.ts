import { NextResponse } from "next/server";
import { z } from "zod";
import { handleAiIntent } from "../../../../lib/ai/gateway";
import { GatewayError, GatewayErrorCode, STATUS } from "../../../../lib/ai/errors";
import { RateLimiter, checkToken, readBoundedBody } from "../../../../lib/ai/guard";
import {
  isConfigured as googleConfigured,
  looksLikeJwt,
  readConfig as readGoogleConfig,
  verifyGoogleIdToken,
} from "../../../../lib/ai/googleIdToken";

/*
 * Context is a small map of short strings - which is exactly what the Android client sends
 * (`Map<String, String>`). It used to be `z.any()`: unbounded, unvalidated, and pasted into
 * the system prompt, so a caller could both run up the model bill and write instructions.
 */
const RequestSchema = z.object({
  query: z.string().trim().min(1).max(1000),
  context: z
    .record(z.string().max(64), z.string().max(500))
    .refine((value) => Object.keys(value).length <= 20, "At most 20 context entries.")
    .optional(),
  providerPreference: z.enum(["gemini", "openai"]).optional(),
});

const limiter = new RateLimiter();

function failure(code: GatewayErrorCode, message: string, headers?: HeadersInit) {
  return NextResponse.json({ error: { code, message } }, { status: STATUS[code], headers });
}

/*
 * One bucket for the whole gateway, not one per client address.
 *
 * Only holders of the single gateway token reach the limiter, so the thing worth bounding
 * is total spend on the provider keys. Keying on X-Forwarded-For would also be bypassable:
 * without a trusted proxy in front, that header is whatever the caller chooses to send.
 */
const GATEWAY_BUCKET = "gateway";

/**
 * Two ways in, and they never compete for the same string.
 *
 * A Google ID token is a JWT - three dot-separated parts - and a shared gateway token is
 * not, so the shape decides which check runs rather than trying both and hoping. Either
 * alone is enough to be configured: a deployment can drop the shared secret once everyone
 * who uses it has signed in, or never turn sign-in on at all.
 */
async function authenticate(req: Request): Promise<
  { ok: true } | { ok: false; code: GatewayErrorCode; message: string }
> {
  const header = req.headers.get("authorization");
  const google = readGoogleConfig();
  const sharedToken = process.env.AURORA_GATEWAY_TOKEN;

  if (!sharedToken && !googleConfigured(google)) {
    return {
      ok: false,
      code: "gateway_not_configured",
      message: "The AI gateway has neither an access token nor a Google client configured.",
    };
  }

  const presented = /^Bearer (.+)$/.exec(header ?? "")?.[1];
  if (presented && googleConfigured(google) && looksLikeJwt(presented)) {
    const verified = await verifyGoogleIdToken(presented, google);
    if (verified.ok) return { ok: true };
    /*
     * The reason is not sent back.
     *
     * "Not on the allowlist" tells an attacker their token was otherwise perfectly valid,
     * which turns the gateway into an oracle for which accounts exist. It is logged here,
     * where the person running this can read it, and the caller is told only no.
     */
    console.warn(`[aurora] ID token rejected: ${verified.reason}`);
    return { ok: false, code: "unauthorized", message: "This account is not authorized." };
  }

  const auth = checkToken(header, sharedToken);
  if (auth === "ok") return { ok: true };
  if (auth === "unconfigured") {
    return { ok: false, code: "unauthorized", message: "Sign in with Google to use the AI." };
  }
  return { ok: false, code: "unauthorized", message: "A valid gateway token is required." };
}

export async function POST(req: Request) {
  // Authentication first: nothing below - not even the body - is read for a stranger.
  const auth = await authenticate(req);
  if (!auth.ok) {
    return failure(
      auth.code,
      auth.message,
      auth.code === "unauthorized" ? { "WWW-Authenticate": "Bearer" } : undefined
    );
  }

  const retryAfter = limiter.take(GATEWAY_BUCKET);
  if (retryAfter > 0) {
    return failure("rate_limited", "Too many requests. Try again shortly.", {
      "Retry-After": String(retryAfter),
    });
  }

  const raw = await readBoundedBody(req);
  if (raw === undefined) return failure("payload_too_large", "The request is too large.");

  let body: unknown;
  try {
    body = JSON.parse(raw);
  } catch {
    return failure("invalid_request", "The request body is not valid JSON.");
  }

  const parsed = RequestSchema.safeParse(body);
  if (!parsed.success) {
    return failure("invalid_request", parsed.error.issues[0]?.message ?? "Invalid request.");
  }

  try {
    const result = await handleAiIntent({
      query: parsed.data.query,
      context: parsed.data.context ?? {},
      providerPreference: parsed.data.providerPreference,
    });
    return NextResponse.json(result);
  } catch (error: unknown) {
    if (error instanceof GatewayError) return failure(error.code, error.message);
    // Anything else is a bug here, not a provider condition. Log it server-side; the
    // client gets a fixed message, because provider errors can carry request context.
    console.error("AI gateway route error:", error);
    return failure("provider_unavailable", "The AI request failed.");
  }
}
