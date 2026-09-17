import { describe, expect, it } from "vitest";
import {
  BODY_TEXT_TARGET,
  contrastFor,
  contrastRatio,
  parseCssColor,
  requiredAlpha,
  requiresOpaqueFallback,
} from "../contrast";
import { MAX_CHROMA, MAX_LIGHTNESS, MIN_LIGHTNESS, fromHsl } from "../palette";

/**
 * The scrim is what makes translucency safe over artwork the app does not control. Now that
 * the web client takes its backdrop from the user's own music too, it needs the same
 * guarantee the Android client has.
 */

/** The dark theme's values from styles/tokens.css. */
const TEXT_PRIMARY = { r: 0xf0 / 255, g: 0xf2 / 255, b: 0xf6 / 255 };
const TEXT_SECONDARY = { r: 0xa7 / 255, g: 0xaf / 255, b: 0xbd / 255 };
const GLASS_PRIMARY = { r: 27 / 255, g: 30 / 255, b: 37 / 255, a: 0.72 };

/** Every colour the palette is allowed to produce. */
function atmosphereColors() {
  const colors = [];
  for (let hue = 0; hue < 360; hue += 15) {
    for (let l = MIN_LIGHTNESS; l <= MAX_LIGHTNESS + 1e-4; l += 0.05) {
      for (const s of [0, 0.25, MAX_CHROMA]) colors.push(fromHsl(hue, s, l));
    }
  }
  return colors;
}

describe("the artwork atmosphere stays legible", () => {
  it("brings primary text to AA over any artwork", () => {
    for (const backdrop of atmosphereColors()) {
      const alpha = requiredAlpha(TEXT_PRIMARY, backdrop, GLASS_PRIMARY);
      const ratio = contrastFor(alpha, TEXT_PRIMARY, backdrop, GLASS_PRIMARY);

      expect(ratio, `backdrop ${JSON.stringify(backdrop)} with scrim ${alpha}`)
        .toBeGreaterThanOrEqual(BODY_TEXT_TARGET);
    }
  });

  it("brings secondary text to AA over any artwork", () => {
    // The dimmer of the two, and the one that fails first.
    for (const backdrop of atmosphereColors()) {
      const alpha = requiredAlpha(TEXT_SECONDARY, backdrop, GLASS_PRIMARY);
      const ratio = contrastFor(alpha, TEXT_SECONDARY, backdrop, GLASS_PRIMARY);

      expect(ratio, `backdrop ${JSON.stringify(backdrop)} with scrim ${alpha}`)
        .toBeGreaterThanOrEqual(BODY_TEXT_TARGET);
    }
  });

  it("never has to abandon translucency", () => {
    // If a full scrim still could not reach the target, the surface would have to go opaque
    // and the glass identity would vanish for that album.
    for (const backdrop of atmosphereColors()) {
      expect(
        requiresOpaqueFallback(TEXT_PRIMARY, backdrop, GLASS_PRIMARY),
        `backdrop ${JSON.stringify(backdrop)}`
      ).toBe(false);
    }
  });

  it("spends no scrim on a dark atmosphere", () => {
    // The common case. Spending scrim on it would darken the design for nothing and wash
    // out the artwork the glass exists to show.
    const dark = fromHsl(210, 0.4, MIN_LIGHTNESS);

    expect(requiredAlpha(TEXT_PRIMARY, dark, GLASS_PRIMARY)).toBe(0);
  });

  it("keeps the artwork visible even at the brightest allowed atmosphere", () => {
    // A scrim strong enough to hide the atmosphere entirely would be technically legible
    // and visually pointless.
    const brightest = fromHsl(50, MAX_CHROMA, MAX_LIGHTNESS);

    expect(requiredAlpha(TEXT_PRIMARY, brightest, GLASS_PRIMARY)).toBeLessThan(0.85);
  });
});

describe("contrastRatio", () => {
  it("is one to one for a colour with itself", () => {
    expect(contrastRatio({ r: 1, g: 1, b: 1 }, { r: 1, g: 1, b: 1 })).toBeCloseTo(1, 3);
  });

  it("is 21 for black on white", () => {
    expect(contrastRatio({ r: 0, g: 0, b: 0 }, { r: 1, g: 1, b: 1 })).toBeCloseTo(21, 1);
  });

  it("is symmetric", () => {
    const a = { r: 0.2, g: 0.4, b: 0.6 };
    const b = { r: 0.9, g: 0.9, b: 0.9 };

    expect(contrastRatio(a, b)).toBeCloseTo(contrastRatio(b, a), 6);
  });
});

describe("requiredAlpha", () => {
  it("returns the smallest scrim that works", () => {
    // Measured against a thin glass, because the web's own primary glass is 72% opaque and
    // never needs a scrim for this palette. The bisection still has to find the minimum -
    // an over-strong scrim washes out the artwork the design system wants to keep - and it
    // has to keep doing so if the opacity tokens are ever lightened.
    const thinGlass = { r: 27 / 255, g: 30 / 255, b: 37 / 255, a: 0.2 };
    const white = { r: 1, g: 1, b: 1 };
    const alpha = requiredAlpha(TEXT_PRIMARY, white, thinGlass);

    expect(alpha).toBeGreaterThan(0);
    expect(
      contrastFor(Math.max(alpha - 0.05, 0), TEXT_PRIMARY, white, thinGlass)
    ).toBeLessThan(BODY_TEXT_TARGET);
  });

  it("needs no scrim at all through the web's own glass", () => {
    // Worth recording rather than assuming: the web tokens are opaque enough that even
    // white artwork behind the primary surface clears AA unaided, which is why nothing in
    // the UI has to dim the atmosphere. The Android client's 20% glass does need one.
    const white = { r: 1, g: 1, b: 1 };

    expect(requiredAlpha(TEXT_PRIMARY, white, GLASS_PRIMARY)).toBe(0);
    expect(contrastFor(0, TEXT_PRIMARY, white, GLASS_PRIMARY))
      .toBeGreaterThanOrEqual(BODY_TEXT_TARGET);
  });

  it("demands the opaque fallback when the scrim colour matches the text", () => {
    // Black text over a black scrim cannot be rescued by more scrim.
    expect(
      requiresOpaqueFallback({ r: 0, g: 0, b: 0 }, { r: 1, g: 1, b: 1 }, GLASS_PRIMARY)
    ).toBe(true);
  });
});

describe("parseCssColor", () => {
  it("parses the token formats the theme actually uses", () => {
    expect(parseCssColor("#ffffff")).toEqual({ r: 1, g: 1, b: 1, a: 1 });
    expect(parseCssColor("rgb(255, 0, 0)")).toEqual({ r: 1, g: 0, b: 0, a: 1 });

    const translucent = parseCssColor("rgba(27, 30, 37, 0.72)");
    expect(translucent?.a).toBeCloseTo(0.72, 3);
  });

  it("expands shorthand hex", () => {
    expect(parseCssColor("#fff")).toEqual({ r: 1, g: 1, b: 1, a: 1 });
  });

  it("returns undefined for anything it cannot read", () => {
    // Custom properties can hold keywords or var() chains; guessing at those would produce
    // a contrast measurement of something that is not on screen.
    expect(parseCssColor("transparent")).toBeUndefined();
    expect(parseCssColor("")).toBeUndefined();
  });
});
