"use client";

import { useEffect, useRef } from "react";
import { useArtworkById } from "@/lib/artwork/useArtwork";
import { toCss } from "@/lib/artwork/palette";
import { usePlayerState } from "@/lib/player/PlayerProvider";
import { PlayerState } from "@/lib/player/types";
import styles from "./AmbientArtworkLayer.module.css";

const selectTrackId = (state: PlayerState) => state.currentTrack?.id;

/**
 * The environmental backdrop everything else floats above.
 *
 * Glass only reads as glass when there is something behind it to refract, and on a flat
 * canvas there is nothing - the translucent surfaces just look like tinted panels. This is
 * that something: a slow wash of the current artwork's colour, sitting under the whole app.
 *
 * The colour is applied as a custom property rather than as React state so the transition
 * is a CSS one, and reduced motion is handled in the stylesheet: this fills the viewport,
 * and a full-screen colour change landing in one frame is exactly the abrupt brightness
 * jump reduced-motion guidance warns about.
 *
 * Contrast needs no scrim here, unlike Android: the web glass tokens are opaque enough that
 * even the brightest colour the palette can produce clears AA through them unaided. That is
 * asserted in lib/artwork/__tests__/contrast.test.ts rather than assumed.
 */
export function AmbientArtworkLayer() {
  const trackId = usePlayerState(selectTrackId);
  const { palette } = useArtworkById(trackId);
  const layer = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const element = layer.current;
    if (!element) return;
    // Removed rather than set to a neutral when there is no artwork, so the stylesheet's own
    // fallback applies and the two definitions of "no atmosphere" cannot disagree.
    if (palette) element.style.setProperty("--aurora-atmosphere", toCss(palette));
    else element.style.removeProperty("--aurora-atmosphere");
  }, [palette]);

  return <div ref={layer} className={styles.layer} aria-hidden="true" />;
}
