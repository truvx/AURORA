import { StoredTrack } from "../library/db";
import { LibraryRepository } from "../library/repository";
import { readEmbeddedArtwork } from "../library/tags";
import { Rgb, dominantColor } from "./palette";

/**
 * Loads album artwork out of the audio files themselves, and the atmosphere colour from it.
 *
 * The Android client gets both from MediaStore, which has already decoded and cached every
 * cover. The web client has only the file the user pointed at, so it parses the picture
 * block, decodes it, and samples it here.
 *
 * Results are cached by track id, and so are misses: a library is mostly files whose art has
 * already been looked at, and re-opening and re-decoding them on every scroll would put an
 * image decode on the frame path.
 */

export interface TrackArtwork {
  /** Object URL for display, or undefined when the file carries no picture. */
  readonly url?: string;
  /** Clamped atmosphere colour, or undefined when nothing usable was found. */
  readonly palette?: Rgb;
}

/** The palette only needs enough pixels to be representative. */
const SAMPLE_SIZE = 96;

/** Bounded so a long session cannot grow this without limit. */
const CACHE_LIMIT = 64;

export class ArtworkLoader {
  private readonly cache = new Map<string, TrackArtwork>();
  /** In-flight loads, so twenty rows asking for one album decode it once. */
  private readonly pending = new Map<string, Promise<TrackArtwork>>();

  constructor(
    private readonly root: FileSystemDirectoryHandle | undefined,
    private readonly repository: LibraryRepository | undefined
  ) {}

  async forTrack(track: StoredTrack): Promise<TrackArtwork> {
    return this.resolve(track.id, () => this.load(track));
  }

  /**
   * For callers holding only a player MediaItem, whose id is the stored track's id.
   *
   * The library rows already have the whole record, so they use [forTrack] and avoid the
   * lookup entirely - one IndexedDB read per visible row would be pure waste.
   */
  async forTrackId(id: string | undefined): Promise<TrackArtwork> {
    if (!id) return {};
    return this.resolve(id, async () => {
      const stored = await this.repository?.get(id);
      return stored ? this.load(stored) : {};
    });
  }

  private async resolve(
    id: string,
    load: () => Promise<TrackArtwork>
  ): Promise<TrackArtwork> {
    const cached = this.cache.get(id);
    if (cached) return cached;

    // Twenty rows showing one album must decode it once, not twenty times.
    const inFlight = this.pending.get(id);
    if (inFlight) return inFlight;

    const started = load().then((artwork) => {
      this.pending.delete(id);
      this.remember(id, artwork);
      return artwork;
    });
    this.pending.set(id, started);
    return started;
  }

  /** Releases every object URL. Called when the loader is replaced or the app unmounts. */
  release(): void {
    for (const entry of this.cache.values()) {
      if (entry.url) URL.revokeObjectURL(entry.url);
    }
    this.cache.clear();
  }

  private remember(id: string, artwork: TrackArtwork): void {
    this.cache.set(id, artwork);
    while (this.cache.size > CACHE_LIMIT) {
      const oldest = this.cache.keys().next();
      if (oldest.done) break;
      const evicted = this.cache.get(oldest.value);
      // An object URL keeps its blob alive until it is revoked, so dropping the reference
      // without revoking would leak the decoded image for the life of the page.
      if (evicted?.url) URL.revokeObjectURL(evicted.url);
      this.cache.delete(oldest.value);
    }
  }

  private async load(track: StoredTrack): Promise<TrackArtwork> {
    if (!this.root) return {};

    try {
      let directory = this.root;
      for (const segment of track.path) {
        directory = await directory.getDirectoryHandle(segment);
      }
      const file = await (await directory.getFileHandle(track.fileName)).getFile();

      const embedded = await readEmbeddedArtwork(file);
      if (!embedded) return {};

      const blob = new Blob([embedded.bytes as BlobPart], {
        type: embedded.mimeType || "image/jpeg",
      });
      const palette = await samplePalette(blob);
      return { url: URL.createObjectURL(blob), palette };
    } catch {
      // Moved, renamed, unreadable, or not a picture we can decode. The caller falls back
      // to the placeholder and nothing about playback is affected.
      return {};
    }
  }
}

/**
 * Decodes `blob` small and returns its dominant colour.
 *
 * Downsampled on the way in rather than after: `createImageBitmap` can resize during decode,
 * so a 3000px cover never becomes a full-size bitmap in memory just to be averaged.
 */
async function samplePalette(blob: Blob): Promise<Rgb | undefined> {
  if (typeof createImageBitmap !== "function") return undefined;

  let bitmap: ImageBitmap | undefined;
  try {
    bitmap = await createImageBitmap(blob, {
      resizeWidth: SAMPLE_SIZE,
      resizeHeight: SAMPLE_SIZE,
      resizeQuality: "medium",
    });

    const canvas = createCanvas(bitmap.width, bitmap.height);
    const context = canvas?.getContext("2d", { willReadFrequently: true });
    if (!context) return undefined;

    context.drawImage(bitmap, 0, 0);
    return dominantColor(context.getImageData(0, 0, bitmap.width, bitmap.height).data);
  } catch {
    return undefined;
  } finally {
    bitmap?.close();
  }
}

type SamplingCanvas = OffscreenCanvas | HTMLCanvasElement;

function createCanvas(width: number, height: number): SamplingCanvas | undefined {
  if (typeof OffscreenCanvas === "function") return new OffscreenCanvas(width, height);
  if (typeof document === "undefined") return undefined;
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  return canvas;
}
