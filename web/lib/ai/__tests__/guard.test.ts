import { describe, expect, it } from "vitest";
import { RateLimiter, checkToken, readBoundedBody } from "../guard";

describe("checkToken", () => {
  it("accepts the configured token", () => {
    expect(checkToken("Bearer s3cret", "s3cret")).toBe("ok");
  });

  it("rejects a wrong token", () => {
    expect(checkToken("Bearer nope", "s3cret")).toBe("invalid");
  });

  it("rejects a token of a different length without throwing", () => {
    // timingSafeEqual throws on unequal lengths; hashing first is what prevents that.
    expect(checkToken("Bearer s3cret-but-much-longer", "s3cret")).toBe("invalid");
  });

  it("treats a missing or malformed header as missing", () => {
    expect(checkToken(null, "s3cret")).toBe("missing");
    expect(checkToken("s3cret", "s3cret")).toBe("missing");
    expect(checkToken("Basic s3cret", "s3cret")).toBe("missing");
  });

  it("fails closed when no token is configured", () => {
    // An unset variable must never mean "open to everyone".
    expect(checkToken("Bearer anything", undefined)).toBe("unconfigured");
    expect(checkToken("Bearer ", "")).toBe("unconfigured");
  });
});

describe("RateLimiter", () => {
  it("allows up to the limit, then reports seconds until the window resets", () => {
    let time = 0;
    const limiter = new RateLimiter(3, 60_000, () => time);

    expect([limiter.take("k"), limiter.take("k"), limiter.take("k")]).toEqual([0, 0, 0]);
    time = 15_000;
    expect(limiter.take("k")).toBe(45);
  });

  it("opens a fresh window once the old one has passed", () => {
    let time = 0;
    const limiter = new RateLimiter(1, 60_000, () => time);

    limiter.take("k");
    expect(limiter.take("k")).toBeGreaterThan(0);
    time = 60_000;
    expect(limiter.take("k")).toBe(0);
  });

  it("counts keys independently", () => {
    const limiter = new RateLimiter(1, 60_000, () => 0);

    limiter.take("a");
    expect(limiter.take("b")).toBe(0);
  });
});

describe("readBoundedBody", () => {
  const post = (body: string, headers: Record<string, string> = {}) =>
    new Request("http://x/", { method: "POST", body, headers });

  it("returns the body when it is under the limit", async () => {
    expect(await readBoundedBody(post("hello"), 10)).toBe("hello");
  });

  it("refuses an oversized body by its declared length without reading it", async () => {
    expect(await readBoundedBody(post("x", { "content-length": "999" }), 10)).toBeUndefined();
  });

  it("refuses an oversized body even when the declared length is missing", async () => {
    // Content-Length can be absent or wrong, so the stream itself is what is counted.
    const request = new Request("http://x/", {
      method: "POST",
      body: new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode("x".repeat(8)));
          controller.enqueue(new TextEncoder().encode("x".repeat(8)));
          controller.close();
        },
      }),
      // @ts-expect-error - required by Node for streaming request bodies
      duplex: "half",
    });

    expect(await readBoundedBody(request, 10)).toBeUndefined();
  });
});
