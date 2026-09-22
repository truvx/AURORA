import { createHash, timingSafeEqual } from "node:crypto";

/**
 * Admission control for the AI gateway: who may call it, how often, and how much.
 *
 * The gateway spends real money on every request - it holds the Gemini and OpenAI keys - so
 * an unauthenticated endpoint is an open proxy onto those accounts. docs/SECURITY.md makes
 * the gateway responsible for "input size/rate limits"; this is where they live.
 */

/** Largest request body accepted, in bytes. A query plus a small context map is ~2 KB. */
export const MAX_BODY_BYTES = 16 * 1024;

/** Requests allowed per window across the whole gateway. Generous for a person, useless for abuse. */
export const RATE_LIMIT = 20;
export const RATE_WINDOW_MS = 60_000;

export type AuthResult = "ok" | "missing" | "invalid" | "unconfigured";

/**
 * Checks the bearer token against AURORA_GATEWAY_TOKEN.
 *
 * Fails closed: with no token configured every request is refused. An unset variable must
 * never mean "open to everyone", which is what a missing guard would silently become.
 */
export function checkToken(authorization: string | null, expected: string | undefined): AuthResult {
  if (!expected) return "unconfigured";
  const match = /^Bearer (.+)$/.exec(authorization ?? "");
  if (!match) return "missing";

  // Hashed before comparing so both sides are the same length - timingSafeEqual throws on
  // a length mismatch, and branching on length would leak it.
  const given = createHash("sha256").update(match[1]).digest();
  const wanted = createHash("sha256").update(expected).digest();
  return timingSafeEqual(given, wanted) ? "ok" : "invalid";
}

/**
 * Fixed-window counter per client key.
 *
 * In memory, so it bounds one server process. That is the deployment this project has
 * today; a multi-instance deployment would need a shared store, and each instance would
 * otherwise allow RATE_LIMIT on its own.
 */
export class RateLimiter {
  private readonly windows = new Map<string, { start: number; count: number }>();

  constructor(
    private readonly limit = RATE_LIMIT,
    private readonly windowMs = RATE_WINDOW_MS,
    private readonly now: () => number = Date.now
  ) {}

  /** Returns 0 when allowed, otherwise the seconds until the window resets. */
  take(key: string): number {
    const time = this.now();
    const current = this.windows.get(key);

    if (!current || time - current.start >= this.windowMs) {
      this.windows.set(key, { start: time, count: 1 });
      this.evictExpired(time);
      return 0;
    }
    if (current.count < this.limit) {
      current.count += 1;
      return 0;
    }
    return Math.ceil((current.start + this.windowMs - time) / 1000);
  }

  /** Keeps the map from growing with every client ever seen. */
  private evictExpired(time: number): void {
    if (this.windows.size < 1024) return;
    for (const [key, window] of this.windows) {
      if (time - window.start >= this.windowMs) this.windows.delete(key);
    }
  }
}

/**
 * Reads the body as text, refusing anything over `limit` bytes.
 *
 * Content-Length alone is not enough - it can be absent or wrong - so the stream is counted
 * as it arrives and abandoned the moment it passes the limit, rather than buffered whole.
 */
export async function readBoundedBody(request: Request, limit = MAX_BODY_BYTES): Promise<string | undefined> {
  const declared = Number(request.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > limit) return undefined;
  if (!request.body) return "";

  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let received = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    received += value.byteLength;
    if (received > limit) {
      await reader.cancel();
      return undefined;
    }
    chunks.push(value);
  }
  return new TextDecoder().decode(Buffer.concat(chunks));
}
