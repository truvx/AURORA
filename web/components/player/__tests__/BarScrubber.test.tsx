import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BarScrubber } from "../BarScrubber";

describe("BarScrubber", () => {
  it("announces a time rather than a number of milliseconds", () => {
    render(<BarScrubber elapsedMs={65_000} durationMs={185_000} onSeek={vi.fn()} />);

    const slider = screen.getByRole("slider", { name: "Playback position" });
    expect(slider).toHaveAttribute("aria-valuetext", "01:05 of 03:05");
    expect(slider).toHaveAttribute("aria-valuenow", "65000");
    expect(slider).toHaveAttribute("aria-valuemax", "185000");
  });

  it("can be moved from the keyboard", async () => {
    const onSeek = vi.fn();
    render(<BarScrubber elapsedMs={65_000} durationMs={185_000} onSeek={onSeek} />);

    const slider = screen.getByRole("slider");
    slider.focus();

    // A scrubber that only answers to a dragged pointer is not one everybody has.
    await userEvent.keyboard("{ArrowRight}");
    expect(onSeek).toHaveBeenLastCalledWith(70_000);

    await userEvent.keyboard("{ArrowLeft}");
    expect(onSeek).toHaveBeenLastCalledWith(60_000);

    await userEvent.keyboard("{PageUp}");
    expect(onSeek).toHaveBeenLastCalledWith(95_000);

    await userEvent.keyboard("{Home}");
    expect(onSeek).toHaveBeenLastCalledWith(0);

    await userEvent.keyboard("{End}");
    expect(onSeek).toHaveBeenLastCalledWith(185_000);
  });

  it("does not seek past either end of the track", async () => {
    const onSeek = vi.fn();
    const { rerender } = render(
      <BarScrubber elapsedMs={2_000} durationMs={185_000} onSeek={onSeek} />
    );

    screen.getByRole("slider").focus();
    await userEvent.keyboard("{ArrowLeft}");
    expect(onSeek).toHaveBeenLastCalledWith(0);

    rerender(<BarScrubber elapsedMs={184_000} durationMs={185_000} onSeek={onSeek} />);
    screen.getByRole("slider").focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(onSeek).toHaveBeenLastCalledWith(185_000);
  });

  it("says so when the duration is not known, and refuses to seek", async () => {
    const onSeek = vi.fn();
    render(<BarScrubber elapsedMs={0} onSeek={onSeek} />);

    const slider = screen.getByRole("slider");
    // Unknown stays unknown; it is not reported as a zero-length track.
    expect(slider).toHaveAttribute("aria-valuetext", "Duration unknown");
    expect(slider).toHaveAttribute("aria-disabled", "true");

    slider.focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(onSeek).not.toHaveBeenCalled();
  });

  it("ignores keys that are not scrubbing keys", async () => {
    const onSeek = vi.fn();
    render(<BarScrubber elapsedMs={1_000} durationMs={10_000} onSeek={onSeek} />);

    screen.getByRole("slider").focus();
    await userEvent.keyboard("{ArrowUp}a ");
    expect(onSeek).not.toHaveBeenCalled();
  });
});
