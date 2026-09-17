"use client";

import { useRouter } from "next/navigation";
import { useCallback, useMemo, useRef, useState } from "react";
import { GlassSurface } from "@/components/glass/Glass";
import { SPRING, clampWithRubberband, createDragHandlers, projectedSnapTarget, useSpringValue } from "@/lib/motion";
import { haptic } from "@/lib/motion/haptics";
import { usePlayerCommands, usePlayerState } from "@/lib/player/PlayerProvider";
import { PlayerState } from "@/lib/player/types";
import styles from "./MiniPlayer.module.css";

/**
 * How far the player lifts before the gesture counts as "open the full player".
 *
 * Small on purpose: the projection below means a quick flick commits long before the finger
 * has travelled this far, so this is the distance for a slow, deliberate drag.
 */
const EXPAND_TRAVEL = 120;

/** Resting and committed positions, in the same coordinates as the drag offset. */
const SNAP_POINTS = [0, -EXPAND_TRAVEL] as const;

/**
 * Persistent transport controls, and the handle for the full player.
 *
 * Each piece subscribes to the narrowest slice it needs, so the four-times-a-second position
 * updates re-render only the scrubber and its timer - not the artwork, title, or buttons.
 *
 * The surface itself is draggable: pull it up and it follows the finger and opens the full
 * player. That is the same relationship the full player has to this one, in reverse, so the
 * two read as one surface in two states rather than two separate screens.
 */
export function MiniPlayer() {
  const router = useRouter();
  const dispatch = usePlayerCommands();
  const track = usePlayerState(selectTrack);
  const status = usePlayerState(selectStatus);
  const autoplayBlocked = usePlayerState(selectAutoplayBlocked);
  const error = usePlayerState(selectError);

  const surface = useRef<HTMLDivElement>(null);
  const [dragging, setDragging] = useState(false);

  /*
   * Written straight to the element rather than through state. This runs on every frame of
   * the drag and of the spring that follows it; a state update per frame would re-render
   * the player sixty times a second to move it a few pixels.
   *
   * transform and opacity only - both composite without touching layout.
   */
  const apply = useCallback((offset: number) => {
    const element = surface.current;
    if (!element) return;
    // Hint where this is going: as it rises it also grows slightly, so the intermediate
    // frames point at the full player rather than merely interpolating toward it.
    const progress = Math.min(Math.abs(Math.min(offset, 0)) / EXPAND_TRAVEL, 1);
    element.style.transform = `translate3d(0, ${offset}px, 0) scale(${1 + progress * 0.02})`;
  }, []);

  const lift = useSpringValue(0, apply, SPRING.sheet);

  const openFullPlayer = useCallback(() => {
    haptic("commit");
    router.push("/now-playing");
    // The shell keeps this mounted across the route change, so it has to be put back where
    // it started or it stays lifted on the next screen.
    lift.jump(0);
  }, [router, lift]);

  const handlers = useMemo(
    () =>
      createDragHandlers({
        axis: "y",
        onDragStart: () => setDragging(true),
        onDrag: ({ offset }) => {
          // Resistance rather than a hard stop at both ends: pulled down there is nothing
          // below, and pulled past the open position there is nothing further up. A dead
          // stop at either reads as the app having frozen.
          //
          // Scaled to the travel rather than to the surface's own height, so how hard it
          // resists is proportional to how far the gesture was ever going to go.
          lift.track(clampWithRubberband(offset, -EXPAND_TRAVEL, 0, EXPAND_TRAVEL));
        },
        onDragEnd: ({ offset, velocity }) => {
          setDragging(false);
          // Velocity decides, not position. A short flick upward opens the player; a slow
          // drag most of the way that stops dead falls back.
          const target = projectedSnapTarget(offset, velocity, SNAP_POINTS);
          if (target === -EXPAND_TRAVEL) {
            openFullPlayer();
          } else {
            // Continues at the speed the finger left at, so there is no seam between the
            // drag and the animation that follows it.
            lift.to(0, velocity);
          }
        },
        onTap: openFullPlayer,
      }),
    [lift, openFullPlayer]
  );

  /*
   * The transport controls and the scrubber live inside the draggable surface, and a press
   * on one of them is not a grab of the player. Without this, pressing play would also
   * start a drag and, on release, open the full player.
   *
   * The grab handle is the exception: it is a button so that it is reachable by keyboard,
   * but it is also the most natural place to actually take hold of the surface, so a press
   * there has to start the drag rather than be filtered out with the controls.
   */
  const startGesture = useCallback(
    (event: React.PointerEvent) => {
      const interactive = (event.target as Element).closest("button, input, a");
      if (interactive && !interactive.hasAttribute("data-drag-handle")) return;
      handlers.onPointerDown(event);
    },
    [handlers]
  );

  if (!track) return null;

  const isPlaying = status === "Playing" || status === "Buffering";

  return (
    <GlassSurface
      level="elevated"
      className={`${styles.miniPlayer} ${dragging ? styles.dragging : ""}`}
    >
      <div
        ref={surface}
        className={styles.row}
        role="region"
        aria-label={`Now playing: ${track.title}${track.artist ? ` by ${track.artist}` : ""}`}
        onPointerDown={startGesture}
        onPointerMove={handlers.onPointerMove}
        onPointerUp={handlers.onPointerUp}
        onPointerCancel={handlers.onPointerCancel}
      >
        {/* A visible affordance for the drag, and the keyboard and screen reader route to
            the same destination - a gesture must never be the only way to reach something. */}
        <button
          type="button"
          className={styles.expand}
          data-drag-handle
          // Pointer activations are already handled by the gesture above, which knows
          // whether the press was a tap or the end of a drag. A click carrying no pointer
          // detail came from the keyboard, and that is the only case left to answer here -
          // without this check, tapping the handle would navigate twice and a drag that
          // settled back would navigate anyway.
          onClick={(event) => {
            if (event.detail === 0) openFullPlayer();
          }}
          aria-label="Open the full player"
        >
          <span className={styles.grabber} aria-hidden="true" />
        </button>

        <div className={styles.meta}>
          <p className={styles.title}>{track.title}</p>
          <p className={styles.secondary}>
            {/* Failures carry words and a symbol, never colour alone. */}
            {error ? `⚠ ${error}` : (track.artist ?? "Unknown artist")}
          </p>
        </div>

        <Scrubber />

        <div className={styles.controls}>
          <button
            type="button"
            onClick={() => dispatch({ type: "skipPrevious" })}
            aria-label="Previous track"
            className={styles.control}
          >
            <span className="material-symbols-outlined" aria-hidden="true">
              skip_previous
            </span>
          </button>
          <button
            type="button"
            onClick={() => dispatch({ type: isPlaying ? "pause" : "play" })}
            aria-label={isPlaying ? "Pause" : "Play"}
            className={styles.control}
          >
            <span className="material-symbols-outlined" aria-hidden="true">
              {isPlaying ? "pause" : "play_arrow"}
            </span>
          </button>
          <button
            type="button"
            onClick={() => dispatch({ type: "skipNext" })}
            aria-label="Next track"
            className={styles.control}
          >
            <span className="material-symbols-outlined" aria-hidden="true">
              skip_next
            </span>
          </button>
        </div>
      </div>

      {autoplayBlocked && (
        // A browser policy, not a fault. Say what happened and what fixes it.
        <p className={styles.notice} role="status">
          Your browser blocked playback until you interact with the page. Press play to start.
        </p>
      )}
    </GlassSurface>
  );
}

/** Isolated so position ticks do not re-render the rest of the player. */
function Scrubber() {
  const dispatch = usePlayerCommands();
  const position = usePlayerState(selectPosition);

  /*
   * Where the thumb is while a finger is on it.
   *
   * The player keeps reporting its own position four times a second, so binding the thumb
   * straight to that means every tick drags it back out from under the finger. During a
   * scrub the pointer owns the value, and the player is told once, at the end.
   */
  const [scrubbingTo, setScrubbingTo] = useState<number | undefined>();

  const knownDuration = position.durationMs !== undefined && position.durationMs > 0;
  const displayed = scrubbingTo ?? position.elapsedMs;

  const onChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const value = Number(event.target.value);
      if (scrubbingTo === undefined) {
        // No pointer down, so this is the keyboard: arrow keys are discrete commits and
        // should seek straight away rather than waiting for a release that never comes.
        dispatch({ type: "seek", positionMs: value });
        return;
      }
      setScrubbingTo(value);
    },
    [dispatch, scrubbingTo]
  );

  const beginScrub = useCallback(() => {
    if (!knownDuration) return;
    setScrubbingTo(position.elapsedMs);
  }, [knownDuration, position.elapsedMs]);

  const endScrub = useCallback(() => {
    if (scrubbingTo === undefined) return;
    dispatch({ type: "seek", positionMs: scrubbingTo });
    setScrubbingTo(undefined);
  }, [dispatch, scrubbingTo]);

  return (
    <div className={styles.scrubber}>
      <span className={styles.time}>{formatTime(displayed)}</span>
      <input
        type="range"
        min={0}
        max={knownDuration ? position.durationMs : 1}
        value={knownDuration ? Math.min(displayed, position.durationMs!) : 0}
        onChange={onChange}
        onPointerDown={beginScrub}
        onPointerUp={endScrub}
        onPointerCancel={endScrub}
        disabled={!knownDuration}
        className={styles.range}
        aria-label={
          knownDuration
            ? `Playback position, ${spokenTime(displayed)} of ${spokenTime(
                position.durationMs!
              )}`
            : "Playback position. Duration unknown."
        }
      />
      {/* Unknown duration shows as unknown rather than as 0:00. */}
      <span className={styles.time}>
        {knownDuration ? formatTime(position.durationMs!) : "--:--"}
      </span>
    </div>
  );
}

const selectTrack = (state: PlayerState) => state.currentTrack;
const selectStatus = (state: PlayerState) => state.status;
const selectPosition = (state: PlayerState) => state.position;
const selectError = (state: PlayerState) => state.error;
const selectAutoplayBlocked = (state: PlayerState) => state.capabilities.autoplayBlocked;

function formatTime(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/** "2 minutes 5 seconds" reads correctly aloud; "2:05" does not. */
function spokenTime(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  const parts: string[] = [];
  if (minutes > 0) parts.push(`${minutes} minute${minutes === 1 ? "" : "s"}`);
  parts.push(`${seconds} second${seconds === 1 ? "" : "s"}`);
  return parts.join(" ");
}
