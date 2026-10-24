import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { MediaItem } from "@/lib/player/types";
import { CoverFlow } from "../CoverFlow";

const queue: MediaItem[] = Array.from({ length: 20 }, (_, index) => ({
  id: `local:${index}`,
  provider: "LOCAL",
  title: `Track ${index}`,
  artist: "Someone",
  isAvailable: true,
}));

beforeAll(() => {
  // jsdom has no layout, so it has no scrollIntoView. The component calls it to keep the
  // current cover in view when the queue advances on its own.
  Element.prototype.scrollIntoView = vi.fn();
});

describe("CoverFlow", () => {
  it("is a listbox whose current cover is the selected option", () => {
    render(<CoverFlow items={queue} currentIndex={5} onSelect={vi.fn()} />);

    expect(screen.getByRole("listbox", { name: "Queue" })).toBeVisible();
    const selected = screen.getAllByRole("option").filter(
      (option) => option.getAttribute("aria-selected") === "true"
    );
    expect(selected).toHaveLength(1);
    expect(selected[0]).toHaveTextContent("Track 5");
  });

  it("draws only the covers near the current one", () => {
    render(<CoverFlow items={queue} currentIndex={10} onSelect={vi.fn()} />);

    // Three either side plus the current one. A twenty-track queue must not put twenty
    // transformed layers on the compositor for covers nobody can see.
    expect(screen.getAllByRole("option")).toHaveLength(7);
    expect(screen.getByText("Track 7")).toBeVisible();
    expect(screen.queryByText("Track 6")).toBeNull();
  });

  it("moves through the queue with the arrow keys", async () => {
    const onSelect = vi.fn();
    render(<CoverFlow items={queue} currentIndex={5} onSelect={onSelect} />);

    const listbox = screen.getByRole("listbox", { name: "Queue" });
    listbox.focus();

    await userEvent.keyboard("{ArrowRight}");
    expect(onSelect).toHaveBeenLastCalledWith(queue[6]);

    await userEvent.keyboard("{ArrowLeft}");
    expect(onSelect).toHaveBeenLastCalledWith(queue[4]);
  });

  it("does nothing at the ends of the queue", async () => {
    const onSelect = vi.fn();
    const { rerender } = render(
      <CoverFlow items={queue} currentIndex={0} onSelect={onSelect} />
    );

    screen.getByRole("listbox").focus();
    await userEvent.keyboard("{ArrowLeft}");
    expect(onSelect).not.toHaveBeenCalled();

    rerender(<CoverFlow items={queue} currentIndex={19} onSelect={onSelect} />);
    screen.getByRole("listbox").focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(onSelect).not.toHaveBeenCalled();
  });

  it("selects the cover that was clicked", async () => {
    const onSelect = vi.fn();
    render(<CoverFlow items={queue} currentIndex={5} onSelect={onSelect} />);

    await userEvent.click(screen.getByText("Track 7"));
    expect(onSelect).toHaveBeenCalledWith(queue[7]);
  });

  it("is reachable by keyboard as one stop, not one per cover", () => {
    render(<CoverFlow items={queue} currentIndex={5} onSelect={vi.fn()} />);

    // A dozen tab stops through a carousel is how a queue becomes unusable by keyboard.
    expect(screen.getByRole("listbox")).toHaveAttribute("tabindex", "0");
    const reachable = screen
      .getAllByRole("option")
      .filter((option) => option.getAttribute("tabindex") === "0");
    expect(reachable).toHaveLength(1);
  });

  it("says who a track is by when the tags do not", () => {
    render(
      <CoverFlow
        items={[{ id: "local:1", provider: "LOCAL", title: "Untitled", isAvailable: true }]}
        currentIndex={0}
        onSelect={vi.fn()}
      />
    );
    expect(screen.getByText("Unknown artist")).toBeVisible();
  });
});
