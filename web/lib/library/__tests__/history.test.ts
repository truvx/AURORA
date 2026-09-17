import "fake-indexeddb/auto";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { DB_NAME } from "../db";
import { HistoryRepository, PlaybackHistoryRecorder } from "../history";

/**
 * Mirrors the Android recorder's rules: a skip is never logged as a listen, unknown duration
 * counts as a skip rather than an invented completion, and resume writes are debounced
 * because position updates arrive several times a second.
 */
describe("history and resume", () => {
  let repository: HistoryRepository;

  afterEach(() => {
    repository?.close();
  });

  beforeEach(async () => {
    await new Promise<void>((resolve, reject) => {
      const request = indexedDB.deleteDatabase(DB_NAME);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
      request.onblocked = () => reject(new Error("deleteDatabase blocked"));
    });
    repository = new HistoryRepository();
  });

  const track = (id: string) => ({ id, provider: "LOCAL" });

  // Takes the duration as an explicit field: a default parameter would swallow an
  // intentional `undefined`, which is exactly the "unknown duration" case under test.
  const state = (
    id: string | undefined,
    status: string,
    elapsedMs = 0,
    position: { durationMs?: number } = { durationMs: 200_000 }
  ) => ({
    status,
    currentTrack: id ? track(id) : undefined,
    position: { elapsedMs, durationMs: position.durationMs },
  });

  function recorder(clock: () => number = () => 0) {
    return new PlaybackHistoryRecorder(repository, "session-1", clock);
  }

  it("records a play when a track starts", async () => {
    await recorder().onState(state("a", "Playing"));

    const events = await repository.getAll();
    expect(events.map((e) => [e.mediaId, e.kind])).toEqual([["a", "PLAY"]]);
  });

  it("records a skip when a track is left early", async () => {
    const r = recorder();
    await r.onState(state("a", "Playing", 0));
    await r.onState(state("a", "Playing", 30_000)); // 15% of 200s
    await r.onState(state("b", "Playing"));

    const events = await repository.getAll();
    expect(events.map((e) => e.kind)).toEqual(["PLAY", "SKIP", "PLAY"]);
  });

  it("records a completion when a track is left past the threshold", async () => {
    const r = recorder();
    await r.onState(state("a", "Playing", 0));
    await r.onState(state("a", "Playing", 190_000)); // 95%
    await r.onState(state("b", "Playing"));

    const events = await repository.getAll();
    expect(events.filter((e) => e.mediaId === "a").map((e) => e.kind)).toEqual([
      "PLAY",
      "COMPLETE",
    ]);
  });

  it("counts unknown duration as a skip rather than inventing a completion", async () => {
    const r = recorder();
    await r.onState(state("a", "Playing", 0, {}));
    await r.onState(state("a", "Playing", 500_000, {}));
    await r.onState(state("b", "Playing", 0, {}));

    const events = await repository.getAll();
    expect(events.find((e) => e.mediaId === "a" && e.kind === "SKIP")).toBeDefined();
  });

  it("records exactly one completion when a track reaches the end", async () => {
    const r = recorder();
    await r.onState(state("a", "Playing"));
    await r.onState(state("a", "Completed", 200_000));
    await r.onState(state("a", "Completed", 200_000));

    const events = await repository.getAll();
    expect(events.filter((e) => e.kind === "COMPLETE")).toHaveLength(1);
  });

  it("resets a finished track to the beginning", async () => {
    const r = recorder();
    await r.onState(state("a", "Playing"));
    await r.onState(state("a", "Completed", 200_000));

    const resume = await repository.getResume("LOCAL", "a");
    expect(resume?.positionMs).toBe(0);
  });

  it("does not write a resume position on every tick", async () => {
    let clock = 1000;
    const r = recorder(() => clock);
    await r.onState(state("a", "Playing"));

    for (let i = 0; i < 10; i += 1) {
      clock += 200;
      await r.onState(state("a", "Playing", 20_000 + i * 200));
    }

    const resume = await repository.getResume("LOCAL", "a");
    // At most one write inside a single debounce window.
    expect(resume?.positionMs).toBe(20_000);
  });

  it("writes again after the debounce window", async () => {
    let clock = 10_000;
    const r = recorder(() => clock);
    await r.onState(state("a", "Playing"));
    await r.onState(state("a", "Playing", 20_000));
    clock += 6000;
    await r.onState(state("a", "Playing", 26_000));

    const resume = await repository.getResume("LOCAL", "a");
    expect(resume?.positionMs).toBe(26_000);
  });

  it("does not resume from a position too early in a track", async () => {
    const r = recorder(() => 100_000);
    await r.onState(state("a", "Playing"));
    await r.onState(state("a", "Playing", 3000));

    expect(await repository.getResume("LOCAL", "a")).toBeUndefined();
  });

  it("keeps resume positions separate per provider", async () => {
    await repository.saveResume({
      provider: "LOCAL",
      mediaId: "a",
      positionMs: 5000,
      updatedAt: 1,
    });
    await repository.saveResume({
      provider: "YOUTUBE",
      mediaId: "a",
      positionMs: 9000,
      updatedAt: 2,
    });

    expect((await repository.getResume("LOCAL", "a"))?.positionMs).toBe(5000);
    expect((await repository.getResume("YOUTUBE", "a"))?.positionMs).toBe(9000);
  });

  it("excludes skipped tracks from recently played", async () => {
    const r = recorder(() => 1);
    await r.onState(state("a", "Playing"));
    await r.onState(state("a", "Playing", 10_000)); // 5%, a skip
    await r.onState(state("b", "Playing"));

    const recent = await repository.getRecentlyPlayed(10);
    expect(recent).toContain("b");
    expect(recent).not.toContain("a");
  });

  it("prunes history older than the cutoff", async () => {
    for (const timestamp of [100, 200, 300, 400]) {
      await repository.record({
        mediaId: "a",
        provider: "LOCAL",
        timestamp,
        sessionId: "s",
        kind: "PLAY",
        progressMs: 0,
      });
    }

    await repository.pruneOlderThan(300);

    const remaining = await repository.getAll();
    expect(remaining.map((e) => e.timestamp)).toEqual([300, 400]);
  });

  it("privacy delete erases history and resume positions", async () => {
    await repository.record({
      mediaId: "a",
      provider: "LOCAL",
      timestamp: 1,
      sessionId: "s",
      kind: "PLAY",
      progressMs: 0,
    });
    await repository.saveResume({
      provider: "LOCAL",
      mediaId: "a",
      positionMs: 5000,
      updatedAt: 1,
    });

    await repository.deleteAllPrivateData();

    expect(await repository.getAll()).toEqual([]);
    expect(await repository.getResume("LOCAL", "a")).toBeUndefined();
  });
});
