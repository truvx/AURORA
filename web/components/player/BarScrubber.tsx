"use client";

import { KeyboardEvent, PointerEvent, useRef, useState } from "react";
import styles from "./BarScrubber.module.css";

interface BarScrubberProps {
  readonly elapsedMs: number;
  readonly durationMs?: number;
  readonly onSeek: (positionMs: number) => void;
  /** For the caller's layout: the transport lays this out differently per width. */
  readonly className?: string;
}

const STEP_MS = 5_000;
const PAGE_MS = 30_000;

/**
 * Where playback has reached, and a way to change it.
 *
 * A slider, not a progressbar: the web client reported the position but would not accept
 * a new one, so the only way to move through a track was to start it again. Keyboard
 * stepping is part of that - a scrubber that only answers to a dragged pointer is not one
 * everybody has.
 */
export function BarScrubber({ elapsedMs, durationMs, onSeek, className }: BarScrubberProps) {
  const track = useRef<HTMLDivElement>(null);
  const [dragging, setDragging] = useState(false);
  const [dragMs, setDragMs] = useState(0);

  const seekable = typeof durationMs === "number" && durationMs > 0;
  const position = dragging ? dragMs : elapsedMs;
  const fraction = seekable ? Math.min(Math.max(position / durationMs, 0), 1) : 0;

  function positionFrom(event: PointerEvent<HTMLDivElement>): number {
    const box = track.current?.getBoundingClientRect();
    if (!box || !seekable) return 0;
    const ratio = Math.min(Math.max((event.clientX - box.left) / box.width, 0), 1);
    return Math.round(ratio * durationMs);
  }

  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    if (!seekable) return;
    // Captured so a drag that leaves the element still belongs to it, rather than the
    // scrub stopping the moment the pointer crosses the edge.
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);
    setDragMs(positionFrom(event));
  }

  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    if (!dragging) return;
    setDragMs(positionFrom(event));
  }

  function onPointerUp(event: PointerEvent<HTMLDivElement>) {
    if (!dragging) return;
    setDragging(false);
    onSeek(positionFrom(event));
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (!seekable) return;
    const next = {
      ArrowLeft: position - STEP_MS,
      ArrowRight: position + STEP_MS,
      PageDown: position - PAGE_MS,
      PageUp: position + PAGE_MS,
      Home: 0,
      End: durationMs,
    }[event.key];
    if (next === undefined) return;
    event.preventDefault();
    onSeek(Math.min(Math.max(next, 0), durationMs));
  }

  return (
    <div className={className}>
      <div
        ref={track}
        className={styles.scrubber}
        role="slider"
        aria-label="Playback position"
        aria-valuemin={0}
        aria-valuemax={seekable ? durationMs : 0}
        aria-valuenow={position}
        // A time, never the raw milliseconds a bare slider would announce.
        aria-valuetext={
          seekable ? `${formatTime(position)} of ${formatTime(durationMs)}` : "Duration unknown"
        }
        aria-disabled={!seekable}
        tabIndex={0}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onKeyDown={onKeyDown}
      >
        <div className={styles.bars} />
        <div className={styles.played} style={{ width: `${fraction * 100}%` }}>
          <div className={styles.bars} style={{ color: "var(--aurora-player-progress)", opacity: 1 }} />
        </div>
        {seekable && <span className={styles.playhead} style={{ left: `${fraction * 100}%` }} />}
      </div>
      <div className={styles.times}>
        <span>{formatTime(position)}</span>
        <span>{seekable ? formatTime(durationMs) : "--:--"}</span>
      </div>
    </div>
  );
}

function formatTime(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}
