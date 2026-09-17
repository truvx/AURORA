"use client";

import { usePlayerState } from "@/lib/player/PlayerProvider";
import { PlayerState } from "@/lib/player/types";
import styles from "./YouTubeSurface.module.css";

/** The element the IFrame API replaces with the official player. */
export const YOUTUBE_PLAYER_ELEMENT_ID = "aurora-youtube-player";

const selectProvider = (state: PlayerState) => state.currentTrack?.provider;

/**
 * Host for the official YouTube player.
 *
 * The player must stay visible and recognisable while a YouTube track is playing
 * (docs/YOUTUBE_CAPABILITY_MATRIX.md): its controls, branding, and ads are its own, and
 * nothing here overlays or hides them. The surface is present but collapsed when a local
 * track is playing, so the element the IFrame API mounted into is never destroyed.
 */
export function YouTubeSurface() {
  const provider = usePlayerState(selectProvider);
  const isYouTube = provider === "YOUTUBE";

  return (
    <section
      className={isYouTube ? styles.visible : styles.hidden}
      aria-label="YouTube player"
      // Hidden from assistive tech only when no YouTube track is loaded, so the player is
      // never announced as present while it is collapsed.
      aria-hidden={!isYouTube}
    >
      <div id={YOUTUBE_PLAYER_ELEMENT_ID} className={styles.player} />
      {isYouTube && (
        <p className={styles.attribution}>
          Playing on YouTube. Quality and availability are determined by YouTube.
        </p>
      )}
    </section>
  );
}
