"use client";

import { useEffect, useRef } from "react";
import { useArtworkById } from "@/lib/artwork/useArtwork";
import { toCss } from "@/lib/artwork/palette";
import { parseCssColor, requiredAlpha } from "@/lib/artwork/contrast";
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
 * It also carries the contrast scrim. The glass tint is now a light one - these materials
 * lighten what is behind them - so bright artwork can no longer be relied on to composite
 * dark. The scrim is measured against the theme's own text and glass values and raised only
 * as far as legibility needs, so dark artwork spends nothing.
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
    if (!palette) {
      element.style.removeProperty("--aurora-atmosphere");
      element.style.removeProperty("--aurora-artwork-scrim");
      return;
    }

    element.style.setProperty("--aurora-atmosphere", toCss(palette));

    // Read the live theme rather than assuming: these values differ between light and dark,
    // and hard-coding them here is how a palette change silently stops protecting text.
    const root = getComputedStyle(document.documentElement);
    const foreground = parseCssColor(root.getPropertyValue("--aurora-text-primary"));
    const glass = parseCssColor(root.getPropertyValue("--aurora-surface-glass-primary"));
    if (!foreground || !glass) return;

    element.style.setProperty(
      "--aurora-artwork-scrim",
      String(requiredAlpha(foreground, palette, glass))
    );
  }, [palette]);

  return <div ref={layer} className={styles.layer} aria-hidden="true" />;
}
