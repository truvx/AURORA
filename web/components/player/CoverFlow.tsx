"use client";

import { ViewTransition, useEffect, useRef } from "react";
import { Artwork } from "@/components/artwork/Artwork";
import { useArtworkById } from "@/lib/artwork/useArtwork";
import { MediaItem } from "@/lib/player/types";
import styles from "./CoverFlow.module.css";

/** How many covers are drawn either side of the current one. */
const NEIGHBOURS = 3;

interface CoverFlowProps {
  readonly items: readonly MediaItem[];
  readonly currentIndex: number;
  readonly onSelect: (item: MediaItem) => void;
}

/**
 * The queue as a row of covers receding into the distance, the current one face-on.
 *
 * It is a listbox, not decoration: each cover is an option, arrow keys move between them,
 * and the selected one is announced. A carousel that can only be driven by dragging would
 * put the queue out of reach of a keyboard entirely.
 */
export function CoverFlow({ items, currentIndex, onSelect }: CoverFlowProps) {
  const listRef = useRef<HTMLDivElement>(null);

  // Only the covers near the current one are rendered: a long queue would otherwise put
  // hundreds of transformed layers on the compositor for covers nobody can see.
  const visible = items
    .map((item, index) => ({ item, index }))
    .filter(({ index }) => Math.abs(index - currentIndex) <= NEIGHBOURS);

  useEffect(() => {
    // Keep the focused option in view when the queue advances on its own.
    listRef.current?.querySelector<HTMLElement>('[aria-selected="true"]')?.scrollIntoView({
      block: "nearest",
      inline: "center",
    });
  }, [currentIndex]);

  function onKeyDown(event: React.KeyboardEvent) {
    const step = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (step === 0) return;
    const next = items[currentIndex + step];
    if (!next) return;
    event.preventDefault();
    onSelect(next);
  }

  return (
    <div
      ref={listRef}
      className={styles.flow}
      role="listbox"
      aria-label="Queue"
      tabIndex={0}
      onKeyDown={onKeyDown}
    >
      {visible.map(({ item, index }) => (
        <Cover
          key={item.id}
          item={item}
          offset={index - currentIndex}
          selected={index === currentIndex}
          onSelect={() => onSelect(item)}
        />
      ))}
    </div>
  );
}

function Cover({
  item,
  offset,
  selected,
  onSelect,
}: {
  item: MediaItem;
  offset: number;
  selected: boolean;
  onSelect: () => void;
}) {
  const artwork = useArtworkById(item.id);
  const direction = Math.sign(offset);
  const distance = Math.abs(offset);

  return (
    <div
      className={styles.cover}
      role="option"
      aria-selected={selected}
      tabIndex={selected ? 0 : -1}
      onClick={onSelect}
      style={{
        // Position is derived from distance, so the covers fan out evenly however many
        // there are, rather than each needing its own rule.
        "--offset": offset,
        "--distance": distance,
        "--direction": direction,
        zIndex: NEIGHBOURS - distance,
      } as React.CSSProperties}
    >
      {/* The other half of the mini-player's morph; see MiniPlayer. */}
      <ViewTransition name={`cover-${item.id}`} share="morph" default="none">
        <Artwork url={artwork.url} title={item.title} size="player" />
      </ViewTransition>
      <div className={styles.meta}>
        <p className={styles.title}>{item.title}</p>
        <p className={styles.artist}>{item.artist ?? "Unknown artist"}</p>
      </div>
    </div>
  );
}
