/**
 * Picks the colour the artwork atmosphere takes on behind the glass.
 *
 * Glass only reads as glass when there is something behind it to refract. The backdrop is
 * that something: a slow wash of the current artwork's dominant colour, which is what makes
 * the translucent surfaces above it look like material rather than flat tinted panels.
 *
 * docs/AURORA_MOTION_SPEC.md asks for the ambient palette to "interpolate slowly and clamp
 * chroma". Clamping is not a detail - an unclamped dominant colour from a saturated cover
 * produces a neon full-screen wash that fights every control on top of it.
 *
 * Deliberately a line-for-line counterpart of the Android client's ArtworkPalette, constants
 * included: the same album should produce the same backdrop on both, and the two drifting
 * apart would be invisible until someone compared them side by side.
 */

export interface Rgb {
  /** 0..1 */
  readonly r: number;
  readonly g: number;
  readonly b: number;
}

/** Saturation ceiling for a full-screen wash. Above this it stops being a backdrop. */
export const MAX_CHROMA = 0.52;

/** Lightness band. Too dark is invisible; too light leaves nothing for text to sit on. */
export const MIN_LIGHTNESS = 0.16;
export const MAX_LIGHTNESS = 0.58;

/** Pixels at least this opaque count; the rest carry no reliable colour. */
const MIN_ALPHA = 128;

/**
 * Near-black and near-white are excluded before counting.
 *
 * They are the most common pixels in a large share of covers - borders, backgrounds, blown
 * highlights - and describe nothing about the record. Counting them means most artwork
 * resolves to the same two backdrops.
 */
const MIN_USEFUL_LIGHTNESS = 0.07;
const MAX_USEFUL_LIGHTNESS = 0.93;

interface Bucket {
  count: number;
  red: number;
  green: number;
  blue: number;
  saturation: number;
}

/**
 * The dominant colour of `pixels`, already clamped for use as an atmosphere.
 *
 * @param pixels RGBA bytes, as returned by `CanvasRenderingContext2D.getImageData`
 * @returns undefined when nothing usable was found - a transparent image, an empty one, or
 *          one made entirely of black and white. The caller keeps its neutral backdrop
 *          rather than being handed a meaningless colour.
 */
export function dominantColor(pixels: Uint8ClampedArray | Uint8Array): Rgb | undefined {
  if (pixels.length < 4) return undefined;

  const buckets = new Map<number, Bucket>();

  for (let i = 0; i + 3 < pixels.length; i += 4) {
    if (pixels[i + 3] < MIN_ALPHA) continue;

    const r = pixels[i];
    const g = pixels[i + 1];
    const b = pixels[i + 2];

    const highest = Math.max(r, g, b);
    const lowest = Math.min(r, g, b);
    const lightness = (highest + lowest) / 510;
    if (lightness < MIN_USEFUL_LIGHTNESS || lightness > MAX_USEFUL_LIGHTNESS) continue;

    const saturation = highest === 0 ? 0 : (highest - lowest) / highest;

    // Quantised to 4 bits per channel: fine enough to keep distinct colours apart, coarse
    // enough that shading across one region still lands in one bucket.
    const key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
    let bucket = buckets.get(key);
    if (!bucket) {
      bucket = { count: 0, red: 0, green: 0, blue: 0, saturation: 0 };
      buckets.set(key, bucket);
    }
    bucket.count += 1;
    bucket.red += r;
    bucket.green += g;
    bucket.blue += b;
    bucket.saturation += saturation;
  }

  if (buckets.size === 0) return undefined;

  // Weighted by how colourful the bucket is as well as how large. A cover that is mostly
  // grey with one strong accent should take its identity from the accent, but sheer
  // dominance still counts - this tips ties, it does not override them.
  let winner: Bucket | undefined;
  let best = -Infinity;
  for (const bucket of buckets.values()) {
    const weight = bucket.count * (0.5 + bucket.saturation / bucket.count);
    if (weight > best) {
      best = weight;
      winner = bucket;
    }
  }
  if (!winner) return undefined;

  return clampForAtmosphere({
    r: winner.red / winner.count / 255,
    g: winner.green / winner.count / 255,
    b: winner.blue / winner.count / 255,
  });
}

/**
 * Brings a colour into the range a full-screen backdrop can occupy.
 *
 * Hue is preserved - that is the part carrying the artwork's identity - while saturation and
 * lightness are pulled into a band that leaves room for content above it.
 */
export function clampForAtmosphere(color: Rgb): Rgb {
  const [hue, saturation, lightness] = toHsl(color);
  return fromHsl(
    hue,
    Math.min(saturation, MAX_CHROMA),
    Math.min(Math.max(lightness, MIN_LIGHTNESS), MAX_LIGHTNESS)
  );
}

/** Hue in degrees, saturation and lightness in 0..1. */
export function toHsl(color: Rgb): [number, number, number] {
  const { r, g, b } = color;
  const highest = Math.max(r, g, b);
  const lowest = Math.min(r, g, b);
  const delta = highest - lowest;
  const lightness = (highest + lowest) / 2;

  if (delta < 1e-6) return [0, 0, lightness];

  const saturation = delta / (1 - Math.abs(2 * lightness - 1));
  let hue: number;
  if (highest === r) hue = 60 * (((g - b) / delta) % 6);
  else if (highest === g) hue = 60 * ((b - r) / delta + 2);
  else hue = 60 * ((r - g) / delta + 4);

  return [hue < 0 ? hue + 360 : hue, Math.min(Math.max(saturation, 0), 1), lightness];
}

export function fromHsl(hue: number, saturation: number, lightness: number): Rgb {
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
  const hue60 = ((((hue % 360) + 360) % 360) / 60);
  const second = chroma * (1 - Math.abs((hue60 % 2) - 1));

  let r = 0;
  let g = 0;
  let b = 0;
  switch (Math.floor(hue60)) {
    case 0: [r, g, b] = [chroma, second, 0]; break;
    case 1: [r, g, b] = [second, chroma, 0]; break;
    case 2: [r, g, b] = [0, chroma, second]; break;
    case 3: [r, g, b] = [0, second, chroma]; break;
    case 4: [r, g, b] = [second, 0, chroma]; break;
    default: [r, g, b] = [chroma, 0, second]; break;
  }

  const offset = lightness - chroma / 2;
  const clamp = (value: number) => Math.min(Math.max(value + offset, 0), 1);
  return { r: clamp(r), g: clamp(g), b: clamp(b) };
}

/** CSS `rgb()` for a colour, for writing into a custom property. */
export function toCss({ r, g, b }: Rgb): string {
  const channel = (value: number) => Math.round(value * 255);
  return `rgb(${channel(r)}, ${channel(g)}, ${channel(b)})`;
}
