import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it } from "vitest";
import { StoredQueueItem, StoredQueueSnapshot } from "../../library/db";
import {
  QueueSnapshotRepository,
  fromStored,
  queueSignature,
  restorableQueue,
} from "../queueSnapshot";
import { MediaItem, PlayerState, initialPlayerState } from "../types";

function track(id: string, provider: MediaItem["provider"] = "LOCAL"): MediaItem {
  return { id, provider, title: `Track ${id}`, isAvailable: true };
}

function stateWith(items: MediaItem[], currentIndex = 0): PlayerState {
  return {
    ...initialPlayerState,
    queue: { ...initialPlayerState.queue, items, currentIndex },
  };
}

describe("queue snapshots", () => {
  beforeEach(async () => {
    await new QueueSnapshotRepository().clear();
  });

  it("comes back after a reload", async () => {
    const repository = new QueueSnapshotRepository();
    await repository.save({
      items: [track("local:1"), track("yt:a", "YOUTUBE")],
      currentIndex: 1,
      repeatMode: "All",
      shuffleMode: "Off",
      shuffledOrder: [],
    });

    const snapshot = await repository.load();

    expect(snapshot?.items.map((item) => item.id)).toEqual(["local:1", "yt:a"]);
    expect(snapshot?.currentIndex).toBe(1);
    expect(snapshot?.repeatMode).toBe("All");
  });

  it("keeps one queue, not a pile of them", async () => {
    const repository = new QueueSnapshotRepository();
    await repository.save({
      items: [track("local:1")],
      currentIndex: 0,
      repeatMode: "Off",
      shuffleMode: "Off",
      shuffledOrder: [],
    });
    await repository.save({
      items: [track("local:2")],
      currentIndex: 0,
      repeatMode: "Off",
      shuffleMode: "Off",
      shuffledOrder: [],
    });

    expect((await repository.load())?.items.map((item) => item.id)).toEqual(["local:2"]);
  });

  it("reports nothing when there has never been a queue", async () => {
    expect(await new QueueSnapshotRepository().load()).toBeUndefined();
  });
});

describe("restoring a snapshot", () => {
  const snapshot = (items: StoredQueueItem[], currentIndex: number): StoredQueueSnapshot => ({
    key: "current",
    items,
    currentIndex,
    repeatMode: "Off",
    shuffleMode: "Off",
    updatedAt: 0,
  });

  const entry = (id: string, provider = "LOCAL"): StoredQueueItem => ({
    id,
    provider,
    title: `Track ${id}`,
  });

  it("drops a local file that has left the library rather than queueing a dud", () => {
    const { items, currentIndex } = restorableQueue(
      snapshot([entry("local:1"), entry("local:gone"), entry("local:3")], 0),
      (item) => item.id !== "local:gone"
    );

    expect(items.map((item) => item.id)).toEqual(["local:1", "local:3"]);
    expect(currentIndex).toBe(0);
  });

  it("follows the current track to its new index", () => {
    const { currentIndex } = restorableQueue(
      snapshot([entry("local:gone"), entry("local:2"), entry("local:3")], 2),
      (item) => item.id !== "local:gone"
    );

    // The third track is now the second; pointing at index 2 would be off the end or at
    // the wrong track.
    expect(currentIndex).toBe(1);
  });

  it("points at the start when the track that was playing is the one that went missing", () => {
    const { items, currentIndex } = restorableQueue(
      snapshot([entry("local:1"), entry("local:gone")], 1),
      (item) => item.id !== "local:gone"
    );

    expect(items.map((item) => item.id)).toEqual(["local:1"]);
    expect(currentIndex).toBe(0);
  });

  it("restores nothing from an empty or absent snapshot", () => {
    expect(restorableQueue(undefined, () => true)).toEqual({ items: [], currentIndex: -1 });
    expect(restorableQueue(snapshot([], -1), () => true)).toEqual({
      items: [],
      currentIndex: -1,
    });
  });

  it("refuses rows a different build could have written", () => {
    // Read back off disk, so none of it is trustworthy.
    expect(fromStored({ id: "", provider: "LOCAL", title: "A" } as StoredQueueItem)).toBeUndefined();
    expect(fromStored({ id: "local:1", provider: "LOCAL" } as StoredQueueItem)).toBeUndefined();
    expect(
      fromStored({ id: "local:1", provider: "SPOTIFY", title: "A" } as StoredQueueItem)
    ).toBeUndefined();
    expect(fromStored(undefined as unknown as StoredQueueItem)).toBeUndefined();
  });

  it("keeps a YouTube entry, which has no local file to lose", () => {
    const { items } = restorableQueue(
      snapshot([entry("yt:a", "YOUTUBE")], 0),
      (item) => item.provider !== "LOCAL"
    );
    expect(items).toHaveLength(1);
  });
});

describe("queue signature", () => {
  it("ignores everything that is not the queue", () => {
    const base = stateWith([track("local:1")]);
    const playing: PlayerState = {
      ...base,
      status: "Playing",
      position: { elapsedMs: 42_000 },
      volume: 0.3,
    };

    // Position ticks arrive four times a second; each one must not cause a write.
    expect(queueSignature(playing)).toBe(queueSignature(base));
  });

  it("changes when the queue does", () => {
    const one = stateWith([track("local:1")]);
    expect(queueSignature(stateWith([track("local:1"), track("local:2")]))).not.toBe(
      queueSignature(one)
    );
    expect(queueSignature(stateWith([track("local:1")], 1))).not.toBe(queueSignature(one));
    expect(
      queueSignature({ ...one, queue: { ...one.queue, repeatMode: "All" } })
    ).not.toBe(queueSignature(one));
    expect(
      queueSignature({ ...one, queue: { ...one.queue, shuffleMode: "On", shuffledOrder: [] } })
    ).not.toBe(queueSignature(one));
  });
});
