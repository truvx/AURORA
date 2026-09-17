/**
 * Works out how much scrim a backdrop needs before text on glass stays legible.
 *
 * The design system's stack is: canvas, artwork atmosphere, contrast-protecting scrim, glass
 * surface, content. The scrim is the layer that makes translucency safe - without it a
 * bright album cover composites through the glass and near-white text lands around 1.35:1,
 * which is illegible.
 *
 * docs/AURORA_MASTER_DESIGN_SYSTEM.md requires the scrim to be raised "until all required
 * content passes contrast", so this measures the real composite rather than applying a fixed
 * dimming value that would be too weak for white art and needlessly heavy for black art.
 *
 * The counterpart of the Android client's ContrastScrim. Both clients now choose their
 * backdrop from the user's own artwork, so both need the same guarantee.
 */

import { Rgb } from "./palette";

/** WCAG 2.1 AA for body text. */
export const BODY_TEXT_TARGET = 4.5;

/** WCAG 2.1 AA for large text and meaningful non-text elements. */
export const LARGE_TEXT_TARGET = 3.0;

export interface Rgba extends Rgb {
  /** 0..1 */
  readonly a: number;
}

const BLACK: Rgb = { r: 0, g: 0, b: 0 };

/**
 * The smallest scrim alpha that brings `foreground` to `targetRatio` over the composite of
 * `glass` on scrim on `backdrop`.
 *
 * Returns 1 when even a full scrim cannot reach the target, which is the caller's signal to
 * drop translucency entirely rather than ship unreadable text.
 */
export function requiredAlpha(
  foreground: Rgb,
  backdrop: Rgb,
  glass: Rgba,
  targetRatio: number = BODY_TEXT_TARGET,
  scrim: Rgb = BLACK
): number {
  if (contrastFor(0, foreground, backdrop, glass, scrim) >= targetRatio) return 0;

  // Contrast is monotonic in scrim alpha for a scrim that moves the backdrop away from the
  // foreground, so a bisection converges without scanning every value.
  let low = 0;
  let high = 1;
  for (let i = 0; i < 20; i += 1) {
    const mid = (low + high) / 2;
    if (contrastFor(mid, foreground, backdrop, glass, scrim) >= targetRatio) high = mid;
    else low = mid;
  }

  return contrastFor(high, foreground, backdrop, glass, scrim) >= targetRatio ? high : 1;
}

/**
 * True when even a full scrim leaves the text below target, so the surface must fall back to
 * opaque rather than staying translucent.
 */
export function requiresOpaqueFallback(
  foreground: Rgb,
  backdrop: Rgb,
  glass: Rgba,
  targetRatio: number = BODY_TEXT_TARGET,
  scrim: Rgb = BLACK
): boolean {
  return contrastFor(1, foreground, backdrop, glass, scrim) < targetRatio;
}

/** Contrast of `foreground` over the finished stack at a given scrim alpha. */
export function contrastFor(
  scrimAlpha: number,
  foreground: Rgb,
  backdrop: Rgb,
  glass: Rgba,
  scrim: Rgb = BLACK
): number {
  const scrimmed = composite({ ...scrim, a: scrimAlpha }, backdrop);
  const surface = composite(glass, scrimmed);
  return contrastRatio(foreground, surface);
}

/** WCAG 2.1 contrast ratio. Both colours are treated as opaque. */
export function contrastRatio(foreground: Rgb, background: Rgb): number {
  const a = relativeLuminance(foreground);
  const b = relativeLuminance(background);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

/** Source-over composite of `top` onto an opaque `bottom`. */
export function composite(top: Rgba, bottom: Rgb): Rgb {
  return {
    r: top.r * top.a + bottom.r * (1 - top.a),
    g: top.g * top.a + bottom.g * (1 - top.a),
    b: top.b * top.a + bottom.b * (1 - top.a),
  };
}

export function relativeLuminance({ r, g, b }: Rgb): number {
  const channel = (value: number) =>
    value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

/** Parses the `rgb()` / `rgba()` / `#rrggbb` forms the theme tokens use. */
export function parseCssColor(value: string): Rgba | undefined {
  const trimmed = value.trim();

  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(trimmed);
  if (hex) {
    const digits = hex[1];
    const expand = digits.length === 3
      ? digits.split("").map((d) => d + d)
      : [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 6)];
    const [r, g, b] = expand.map((pair) => parseInt(pair, 16) / 255);
    return { r, g, b, a: 1 };
  }

  const fn = /^rgba?\(([^)]+)\)$/i.exec(trimmed);
  if (fn) {
    const parts = fn[1].split(/[\s,/]+/).filter(Boolean).map(Number);
    if (parts.length < 3 || parts.some(Number.isNaN)) return undefined;
    return {
      r: parts[0] / 255,
      g: parts[1] / 255,
      b: parts[2] / 255,
      a: parts.length > 3 ? parts[3] : 1,
    };
  }

  return undefined;
}
