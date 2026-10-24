import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * The AI endpoint spends real money on every call it lets through. These drive the actual
 * route handler with the provider layer mocked out, so no request here reaches Gemini or
 * OpenAI - which also means none of them need keys to run.
 */

const handleAiIntent = vi.fn();
vi.mock("../gateway", () => ({ handleAiIntent: (...args: unknown[]) => handleAiIntent(...args) }));

const TOKEN = "test-gateway-token";

/** A fresh module per test, so the route's rate-limit window starts empty each time. */
async function route() {
  vi.resetModules();
  return (await import("../../../app/api/ai/intent/route")).POST;
}

function request(body: unknown, init: { token?: string | null; raw?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  const token = init.token === undefined ? TOKEN : init.token;
  if (token !== null) headers.authorization = `Bearer ${token}`;
  return new Request("http://localhost/api/ai/intent", {
    method: "POST",
    headers,
    body: init.raw ?? JSON.stringify(body),
  });
}

async function send(req: Request) {
  const POST = await route();
  const response = await POST(req);
  return { status: response.status, headers: response.headers, body: await response.json() };
}

beforeEach(() => {
  process.env.AURORA_GATEWAY_TOKEN = TOKEN;
  handleAiIntent.mockReset();
  handleAiIntent.mockResolvedValue({ text: "ok", toolCalls: [] });
});

afterEach(() => {
  delete process.env.AURORA_GATEWAY_TOKEN;
});

describe("authentication", () => {
  it("refuses a request without a token, before doing any work", async () => {
    const res = await send(request({ query: "hi" }, { token: null }));

    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe("unauthorized");
    expect(res.headers.get("www-authenticate")).toBe("Bearer");
    expect(handleAiIntent).not.toHaveBeenCalled();
  });

  it("refuses a wrong token", async () => {
    const res = await send(request({ query: "hi" }, { token: "guess" }));

    expect(res.status).toBe(401);
    expect(handleAiIntent).not.toHaveBeenCalled();
  });

  it("refuses everything when the server has no token configured", async () => {
    // Fails closed: forgetting the variable must not reopen the endpoint.
    delete process.env.AURORA_GATEWAY_TOKEN;

    const res = await send(request({ query: "hi" }));

    expect(res.status).toBe(503);
    expect(res.body.error.code).toBe("gateway_not_configured");
    expect(handleAiIntent).not.toHaveBeenCalled();
  });

  it("serves an authenticated, valid request", async () => {
    const res = await send(request({ query: "late night driving music" }));

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ text: "ok", toolCalls: [] });
  });
});

describe("rate limiting", () => {
  it("returns 429 with Retry-After once the gateway's budget is spent", async () => {
    const POST = await route();
    const statuses: number[] = [];
    let last: Response | undefined;
    for (let i = 0; i < 21; i += 1) {
      last = await POST(request({ query: "hi" }));
      statuses.push(last.status);
    }

    expect(statuses.slice(0, 20).every((status) => status === 200)).toBe(true);
    expect(statuses[20]).toBe(429);
    expect(Number(last!.headers.get("retry-after"))).toBeGreaterThan(0);
    expect(handleAiIntent).toHaveBeenCalledTimes(20);
  });
});

describe("input validation", () => {
  it("rejects an oversized body", async () => {
    const res = await send(request(null, { raw: JSON.stringify({ query: "x".repeat(20_000) }) }));

    expect(res.status).toBe(413);
    expect(res.body.error.code).toBe("payload_too_large");
  });

  it("rejects malformed JSON", async () => {
    const res = await send(request(null, { raw: "{not json" }));

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe("invalid_request");
  });

  it("rejects an empty or whitespace-only query", async () => {
    expect((await send(request({ query: "   " }))).status).toBe(400);
  });

  it("rejects context that is not a map of strings", async () => {
    // It used to be z.any() and was pasted into the system prompt.
    const res = await send(request({ query: "hi", context: { nested: { deep: "object" } } }));

    expect(res.status).toBe(400);
    expect(handleAiIntent).not.toHaveBeenCalled();
  });

  it("rejects context with too many entries", async () => {
    const context = Object.fromEntries(Array.from({ length: 21 }, (_, i) => [`k${i}`, "v"]));

    expect((await send(request({ query: "hi", context }))).status).toBe(400);
  });

  it("rejects an overlong context value", async () => {
    expect((await send(request({ query: "hi", context: { mood: "x".repeat(501) } }))).status).toBe(400);
  });

  it("passes a valid context through", async () => {
    await send(request({ query: "hi", context: { mood: "calm", nowPlaying: "Static Bloom" } }));

    expect(handleAiIntent).toHaveBeenCalledWith(
      expect.objectContaining({ context: { mood: "calm", nowPlaying: "Static Bloom" } })
    );
  });
});

describe("error mapping", () => {
  it("reports an unconfigured provider as its own code, not a generic 500", async () => {
    // Taken from the same module registry the route loads: resetModules gives the route a
    // fresh errors module, and its `instanceof` check would not recognise a class from the
    // registry this file imported at the top.
    const POST = await route();
    const { GatewayError } = await import("../errors");
    handleAiIntent.mockRejectedValue(new GatewayError("ai_not_configured", "Gemini is not configured."));

    const response = await POST(request({ query: "hi" }));
    const res = { status: response.status, body: await response.json() };

    expect(res.status).toBe(503);
    expect(res.body.error.code).toBe("ai_not_configured");
  });

  it("hides the detail of an unexpected error", async () => {
    handleAiIntent.mockRejectedValue(new Error("secret internal detail: key=abc"));

    const res = await send(request({ query: "hi" }));

    expect(res.status).toBe(502);
    expect(JSON.stringify(res.body)).not.toContain("secret internal detail");
  });
});
