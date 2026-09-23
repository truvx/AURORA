import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Artwork } from "../Artwork";

describe("Artwork", () => {
  it("names the record in the alt text", () => {
    render(<Artwork url="blob:cover" title="Blue Bird" size="row" />);

    // "album art" down a list of twenty rows tells a screen reader user nothing about
    // which row they are on, which is why the title is in the alt text.
    const image = screen.getByRole("img");
    expect(image).toHaveAttribute("alt", "Blue Bird album artwork");
    expect(image).toHaveAttribute("src", "blob:cover");
  });

  it("draws a placeholder instead of an empty square when there is no cover", () => {
    const { container } = render(<Artwork title="Blue Bird" size="row" />);

    // Nothing to announce: the row already says the title, and a second reading of it
    // under "image" is noise.
    expect(screen.queryByRole("img")).toBeNull();
    const placeholder = container.firstElementChild;
    expect(placeholder).toHaveAttribute("aria-hidden", "true");
    expect(placeholder?.textContent).toBe("album");
  });

  it("loads lazily, since a library page renders hundreds of these", () => {
    render(<Artwork url="blob:cover" title="Blue Bird" size="row" />);
    expect(screen.getByRole("img")).toHaveAttribute("loading", "lazy");
  });

  it("keeps the row and player sizes distinguishable", () => {
    const { container: row } = render(<Artwork url="b:1" title="A" size="row" />);
    const rowClass = row.firstElementChild?.className;
    const { container: player } = render(<Artwork url="b:1" title="A" size="player" />);
    expect(player.firstElementChild?.className).not.toBe(rowClass);
  });
});
