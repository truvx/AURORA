"use client";

import styles from "./Artwork.module.css";

interface ArtworkProps {
  /** Object URL from the artwork loader, or undefined while loading and when absent. */
  readonly url?: string;
  /**
   * What this artwork is of. Used for the alt text, so it has to name the record rather
   * than say "album art" - a screen reader reading "album art" down a list says nothing.
   */
  readonly title: string;
  readonly size: "row" | "player";
}

/**
 * Album artwork with a consistent shape and a placeholder when there is none.
 *
 * A missing cover is drawn, not left blank: an empty square reads as something that failed
 * to load, where a placeholder reads as a record without artwork.
 */
export function Artwork({ url, title, size }: ArtworkProps) {
  const className = `${styles.artwork} ${size === "player" ? styles.player : styles.row}`;

  if (!url) {
    return (
      <div className={`${className} ${styles.placeholder}`} aria-hidden="true">
        <span className="material-symbols-outlined">album</span>
      </div>
    );
  }

  return (
    // Plain img rather than next/image: the source is a blob URL created in the browser at
    // runtime, so there is nothing for the image optimiser to fetch, resize or cache.
    // eslint-disable-next-line @next/next/no-img-element
    <img className={className} src={url} alt={`${title} album artwork`} loading="lazy" />
  );
}
