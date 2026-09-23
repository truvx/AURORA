import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { StoredTrack } from "@/lib/library/db";
import { TrackRow } from "../TrackRow";

const track = {
  id: "local:1",
  title: "Blue Bird",
  artist: "Ikimonogakari",
  album: "Hanabi",
  path: "Blue Bird.mp3",
} as unknown as StoredTrack;

describe("TrackRow", () => {
  it("plays on the row and queues on its own control", async () => {
    const onPlay = vi.fn();
    const onQueue = vi.fn();
    render(<TrackRow track={track} onPlay={onPlay} onQueue={onQueue} />);

    // The row's name comes from its text, and the two spans run together as
    // "Blue BirdIkimonogakari" - matched loosely here rather than pinning that spelling.
    await userEvent.click(screen.getByRole("button", { name: /^Blue Bird/ }));
    expect(onPlay).toHaveBeenCalledOnce();
    expect(onQueue).not.toHaveBeenCalled();

    // Loading a track replaces the queue, so without a separate control the web queue
    // could never hold more than one track - the reason this button exists.
    await userEvent.click(screen.getByRole("button", { name: "Add Blue Bird to the queue" }));
    expect(onQueue).toHaveBeenCalledOnce();
    expect(onPlay).toHaveBeenCalledOnce();
  });

  it("names the queue control after the track, not the icon", () => {
    render(<TrackRow track={track} onPlay={vi.fn()} onQueue={vi.fn()} />);

    // Twenty buttons all called "queue_music" are twenty identical stops in a screen
    // reader's list of controls.
    expect(screen.getByRole("button", { name: "Add Blue Bird to the queue" })).toBeVisible();
  });

  it("says who a track is by when the tags do not", () => {
    render(
      <TrackRow track={{ ...track, artist: undefined } as StoredTrack} onPlay={vi.fn()} onQueue={vi.fn()} />
    );
    expect(screen.getByText("Unknown artist")).toBeVisible();
  });
});
