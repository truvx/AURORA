import { describe, expect, it } from "vitest";
import {
  MAX_CHROMA,
  MAX_LIGHTNESS,
  MIN_LIGHTNESS,
  clampForAtmosphere,
  dominantColor,
  fromHsl,
  toCss,
  toHsl,
} from "../palette";

/**
 * Album art is arbitrary, so these assert properties that must hold for any cover rather
 * than pinning the output for particular images.
 *
 * Mirrors the Android client's ArtworkPaletteTest case for case. The same album should
 * produce the same backdrop on both clients, and the cheapest way to keep that true is for
 * both to be held to the same assertions.
 */
describe("dominantColor", () => {
  const rgba = (r: number, g: number, b: number, a = 255) => [r, g, b, a];
  const fill = (count: number, pixel: number[]) =>
    new Uint8ClampedArray(Array.from({ length: count }, () => pixel).flat());
  const concat = (...parts: Uint8ClampedArray[]) => {
    const total = parts.reduce((sum, p) => sum + p.length, 0);
    const out = new Uint8ClampedArray(total);
    let offset = 0;
    for (const part of parts) {
      out.set(part, offset);
      offset += part.length;
    }
    return out;
  };

  it("resolves a solid colour to that hue", () => {
    const result = dominantColor(fill(64, rgba(200, 40, 40)));

    expect(result).toBeDefined();
    const [hue] = toHsl(result!);
    expect(hue < 20 || hue > 340).toBe(true);
  });

  it("finds a colour surrounded by black", () => {
    // Most covers are mostly dark. Counting black would resolve nearly all of them to the
    // same backdrop and the atmosphere would stop meaning anything.
    const result = dominantColor(
      concat(fill(900, rgba(0, 0, 0)), fill(100, rgba(30, 90, 210)))
    );

    expect(result).toBeDefined();
    const [hue] = toHsl(result!);
    expect(hue).toBeGreaterThan(190);
    expect(hue).toBeLessThan(250);
  });

  it("finds a colour surrounded by white", () => {
    const result = dominantColor(
      concat(fill(900, rgba(255, 255, 255)), fill(100, rgba(20, 160, 80)))
    );

    expect(result).toBeDefined();
    const [hue] = toHsl(result!);
    expect(hue).toBeGreaterThan(100);
    expect(hue).toBeLessThan(180);
  });

  it("lets a vivid accent beat a larger dull region", () => {
    // A cover that is mostly muted grey with one strong accent takes its identity from the
    // accent - that is what someone looking at it would call the record's colour.
    const result = dominantColor(
      concat(fill(600, rgba(122, 120, 124)), fill(400, rgba(230, 60, 160)))
    );

    expect(result).toBeDefined();
    const [hue, saturation] = toHsl(result!);
    expect(saturation).toBeGreaterThan(0.2);
    expect(hue).toBeGreaterThan(290);
    expect(hue).toBeLessThan(350);
  });

  it("does not let a tiny vivid speck overturn overwhelming dominance", () => {
    const result = dominantColor(
      concat(fill(5000, rgba(40, 90, 200)), fill(5, rgba(255, 0, 255)))
    );

    expect(result).toBeDefined();
    const [hue] = toHsl(result!);
    expect(hue).toBeGreaterThan(190);
    expect(hue).toBeLessThan(250);
  });

  it("resolves shading across one region to a single colour", () => {
    // Quantisation exists for this: a gradient over one area is one colour to the eye, and
    // should not split into buckets that each lose to a flat region elsewhere.
    const shaded = new Uint8ClampedArray(
      Array.from({ length: 300 }, (_, i) => rgba(180 + (i % 8), 60 + (i % 8), 60 + (i % 8))).flat()
    );
    const result = dominantColor(concat(shaded, fill(200, rgba(70, 70, 72))));

    expect(result).toBeDefined();
    const [hue] = toHsl(result!);
    expect(hue < 20 || hue > 340).toBe(true);
  });

  it("returns nothing for an empty image", () => {
    expect(dominantColor(new Uint8ClampedArray(0))).toBeUndefined();
  });

  it("returns nothing for a fully transparent image", () => {
    // Decoding can hand back an empty bitmap; inventing a colour from it would put a wash
    // on screen that belongs to no artwork at all.
    expect(dominantColor(fill(100, rgba(200, 100, 50, 0)))).toBeUndefined();
  });

  it("returns nothing for pure black and white artwork", () => {
    expect(
      dominantColor(concat(fill(500, rgba(0, 0, 0)), fill(500, rgba(255, 255, 255))))
    ).toBeUndefined();
  });

  it("still resolves mid grey artwork", () => {
    // Greyscale is a legitimate cover, and should produce a neutral backdrop rather than
    // being discarded.
    const result = dominantColor(fill(200, rgba(128, 128, 128)));

    expect(result).toBeDefined();
    expect(toHsl(result!)[1]).toBeLessThan(0.1);
  });

  it("clamps saturated artwork rather than washing the screen in neon", () => {
    const result = dominantColor(fill(100, rgba(255, 0, 0)));

    expect(result).toBeDefined();
    expect(toHsl(result!)[1]).toBeLessThanOrEqual(MAX_CHROMA + 0.005);
  });

  it("keeps every plausible colour inside the lightness band", () => {
    // The band is what leaves room for content above the backdrop. It has to hold for
    // arbitrary artwork, not just the samples above.
    for (let r = 0; r <= 255; r += 51) {
      for (let g = 0; g <= 255; g += 51) {
        for (let b = 0; b <= 255; b += 51) {
          const result = dominantColor(fill(40, rgba(r, g, b)));
          if (!result) continue;
          const [, , lightness] = toHsl(result);
          expect(lightness).toBeGreaterThanOrEqual(MIN_LIGHTNESS - 0.005);
          expect(lightness).toBeLessThanOrEqual(MAX_LIGHTNESS + 0.005);
        }
      }
    }
  });
});

describe("clampForAtmosphere", () => {
  it("preserves hue", () => {
    // Saturation and lightness are negotiable; hue carries the artwork's identity, and
    // changing it would make the backdrop belong to nothing.
    const vivid = { r: 1, g: 0.85, b: 0 };
    const [before] = toHsl(vivid);

    const [after] = toHsl(clampForAtmosphere(vivid));

    expect(after).toBeCloseTo(before, 0);
  });

  it("leaves a colour already in range alone", () => {
    const inRange = fromHsl(210, 0.3, 0.4);

    const clamped = clampForAtmosphere(inRange);

    expect(clamped.r).toBeCloseTo(inRange.r, 2);
    expect(clamped.g).toBeCloseTo(inRange.g, 2);
    expect(clamped.b).toBeCloseTo(inRange.b, 2);
  });
});

describe("hsl round trip", () => {
  it("survives conversion in both directions", () => {
    for (let hue = 0; hue < 360; hue += 30) {
      const [h, s, l] = toHsl(fromHsl(hue, 0.6, 0.45));

      expect(h).toBeCloseTo(hue, 0);
      expect(s).toBeCloseTo(0.6, 2);
      expect(l).toBeCloseTo(0.45, 2);
    }
  });

  it("reports no saturation for a fully desaturated colour", () => {
    const [, saturation, lightness] = toHsl({ r: 0.5, g: 0.5, b: 0.5 });

    expect(saturation).toBeCloseTo(0, 3);
    expect(lightness).toBeCloseTo(0.5, 3);
  });
});

describe("toCss", () => {
  it("renders an rgb() string", () => {
    expect(toCss({ r: 1, g: 0, b: 0.5 })).toBe("rgb(255, 0, 128)");
  });
});
