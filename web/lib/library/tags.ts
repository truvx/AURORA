/**
 * Reads title, artist, and album from audio files in the browser.
 *
 * Without this the library shows filenames, which is honest but poor. Tags that are absent
 * stay undefined rather than being inferred from the filename: a guess that looks like
 * metadata is worse than an obvious fallback.
 *
 * Supported: FLAC (Vorbis comments) and ID3v2.3/2.4 (as used by MP3). MP4/M4A atoms and
 * Ogg pages are not parsed yet and report nothing rather than something invented.
 *
 * Only the header region of each file is read - a few hundred kilobytes at most - so
 * scanning a large library does not pull entire albums into memory.
 */

export interface AudioTags {
  title?: string;
  artist?: string;
  album?: string;
}

export interface EmbeddedArtwork {
  readonly bytes: Uint8Array;
  readonly mimeType: string;
}

/** Enough for a FLAC comment block or an ID3 tag with embedded artwork. */
const HEADER_BYTES = 1024 * 1024;
const MAX_BLOCK_BYTES = 1024 * 1024;

export async function readTags(file: File): Promise<AudioTags> {
  try {
    const header = new Uint8Array(
      await file.slice(0, Math.min(HEADER_BYTES, file.size)).arrayBuffer()
    );
    if (startsWith(header, "fLaC")) return readFlac(header);
    if (startsWith(header, "ID3")) return readId3(header);
    return {};
  } catch {
    // An unreadable file must not abandon the scan; it simply has no tags.
    return {};
  }
}

/**
 * Reads the cover art embedded in an audio file, if it has any.
 *
 * Separate from readTags and never called during a scan. Artwork is the one thing in these
 * headers that is measured in hundreds of kilobytes, and pulling it for every file while
 * walking a library would cost far more than the metadata the scan is actually after.
 *
 * Unlike the Android client, which gets album art from MediaStore, the web client only has
 * the file itself - so it has to parse the picture blocks directly.
 */
export async function readEmbeddedArtwork(file: File): Promise<EmbeddedArtwork | undefined> {
  try {
    const header = new Uint8Array(
      await file.slice(0, Math.min(HEADER_BYTES, file.size)).arrayBuffer()
    );
    if (startsWith(header, "fLaC")) return readFlacPicture(header);
    if (startsWith(header, "ID3")) return readId3Picture(header);
    return undefined;
  } catch {
    // Artwork is decoration. An unreadable file shows the placeholder and still plays.
    return undefined;
  }
}

// --- FLAC -----------------------------------------------------------------------------

function readFlac(data: Uint8Array): AudioTags {
  let offset = 4; // past "fLaC"

  while (offset + 4 <= data.length) {
    const isLast = (data[offset] & 0x80) !== 0;
    const type = data[offset] & 0x7f;
    const length = (data[offset + 1] << 16) | (data[offset + 2] << 8) | data[offset + 3];
    offset += 4;

    if (length < 0 || length > MAX_BLOCK_BYTES) return {};
    if (type === 4) {
      return parseVorbisComments(data.subarray(offset, offset + length));
    }
    offset += length;
    if (isLast) return {};
  }
  return {};
}

/**
 * Walks FLAC metadata blocks for a PICTURE block (type 6).
 *
 * Prefers the block whose picture type is "front cover" (3). A file can carry several -
 * back cover, booklet pages, an artist photo - and taking whichever came first would show
 * the back of the sleeve as often as the front.
 */
function readFlacPicture(data: Uint8Array): EmbeddedArtwork | undefined {
  let offset = 4; // past "fLaC"
  let fallback: EmbeddedArtwork | undefined;

  while (offset + 4 <= data.length) {
    const isLast = (data[offset] & 0x80) !== 0;
    const type = data[offset] & 0x7f;
    const length = (data[offset + 1] << 16) | (data[offset + 2] << 8) | data[offset + 3];
    offset += 4;

    if (length < 0 || offset + length > data.length) break;

    if (type === 6) {
      const picture = parseFlacPictureBlock(data.subarray(offset, offset + length));
      if (picture) {
        if (picture.isFrontCover) return picture.artwork;
        fallback ??= picture.artwork;
      }
    }

    offset += length;
    if (isLast) break;
  }
  return fallback;
}

function parseFlacPictureBlock(
  block: Uint8Array
): { artwork: EmbeddedArtwork; isFrontCover: boolean } | undefined {
  // All fields here are big-endian, unlike the Vorbis comment block above.
  let offset = 0;
  const readU32 = (): number | undefined => {
    if (offset + 4 > block.length) return undefined;
    const value = readU32BE(block, offset) >>> 0;
    offset += 4;
    return value;
  };

  const pictureType = readU32();
  if (pictureType === undefined) return undefined;

  const mimeLength = readU32();
  if (mimeLength === undefined || offset + mimeLength > block.length) return undefined;
  const mimeType = new TextDecoder("ascii").decode(block.subarray(offset, offset + mimeLength));
  offset += mimeLength;

  const descriptionLength = readU32();
  if (descriptionLength === undefined || offset + descriptionLength > block.length) {
    return undefined;
  }
  offset += descriptionLength;

  // width, height, depth, indexed colours - not needed, the decoder reports the real size.
  offset += 16;

  const dataLength = readU32();
  if (dataLength === undefined || dataLength <= 0 || offset + dataLength > block.length) {
    return undefined;
  }

  return {
    artwork: { bytes: block.subarray(offset, offset + dataLength), mimeType },
    isFrontCover: pictureType === 3,
  };
}

function parseVorbisComments(block: Uint8Array): AudioTags {
  const view = new DataView(block.buffer, block.byteOffset, block.byteLength);
  let offset = 0;

  const readU32 = (): number | undefined => {
    if (offset + 4 > block.length) return undefined;
    // Vorbis comments are little-endian, unlike the big-endian FLAC block header.
    const value = view.getUint32(offset, true);
    offset += 4;
    return value;
  };

  const vendorLength = readU32();
  if (vendorLength === undefined || offset + vendorLength > block.length) return {};
  offset += vendorLength;

  const count = readU32();
  if (count === undefined || count > 4096) return {};

  const decoder = new TextDecoder("utf-8");
  const tags: AudioTags = {};

  for (let i = 0; i < count; i += 1) {
    const length = readU32();
    if (length === undefined || offset + length > block.length) break;
    const comment = decoder.decode(block.subarray(offset, offset + length));
    offset += length;

    const separator = comment.indexOf("=");
    if (separator <= 0) continue;
    assign(tags, comment.slice(0, separator), comment.slice(separator + 1));
  }
  return tags;
}

// --- ID3v2 ----------------------------------------------------------------------------

/**
 * Walks an ID3v2 tag, handing each frame's id and body to `visit`.
 *
 * Shared by the text and picture readers so the header, extended header and frame-size
 * handling exist once - those are where the version differences live, and two copies would
 * drift.
 *
 * `visit` returning true stops the walk.
 */
function walkId3Frames(
  data: Uint8Array,
  visit: (frameId: string, body: Uint8Array) => boolean | void
): void {
  const major = data[3];
  // 2.2 uses three-character frame ids and a different layout; misreading it would produce
  // wrong values, so it reports nothing instead.
  if (major !== 3 && major !== 4) return;

  const flags = data[5];
  // Unsynchronisation rewrites the byte stream; refuse rather than misread.
  if ((flags & 0x80) !== 0) return;

  const size = synchsafe(data, 6);
  if (size === undefined || size <= 0) return;

  const end = Math.min(10 + size, data.length);
  let offset = 10;

  if ((flags & 0x40) !== 0) {
    // Skip the extended header.
    const extended =
      major === 4 ? synchsafe(data, offset) : readU32BE(data, offset) + 4;
    if (extended === undefined || offset + extended > end) return;
    offset += extended;
  }

  while (offset + 10 <= end) {
    if (data[offset] === 0) break; // padding

    const frameId = String.fromCharCode(
      data[offset], data[offset + 1], data[offset + 2], data[offset + 3]
    );
    const frameSize =
      major === 4 ? synchsafe(data, offset + 4) : readU32BE(data, offset + 4);
    offset += 10;

    if (frameSize === undefined || frameSize < 0 || offset + frameSize > end) break;

    if (visit(frameId, data.subarray(offset, offset + frameSize))) return;
    offset += frameSize;
  }
}

function readId3(data: Uint8Array): AudioTags {
  const tags: AudioTags = {};
  walkId3Frames(data, (frameId, body) => {
    const key = ID3_FRAMES[frameId];
    if (!key) return;
    const value = decodeTextFrame(body);
    if (value) tags[key] = value;
  });
  return tags;
}

/**
 * Finds an APIC frame.
 *
 * Prefers the frame whose picture type is "front cover" (3), for the same reason as FLAC:
 * a file can carry the back cover, a booklet page or an artist photo alongside it.
 */
function readId3Picture(data: Uint8Array): EmbeddedArtwork | undefined {
  let fallback: EmbeddedArtwork | undefined;

  walkId3Frames(data, (frameId, body) => {
    if (frameId !== "APIC") return;
    const picture = parseApicFrame(body);
    if (!picture) return;
    if (picture.isFrontCover) {
      fallback = picture.artwork;
      return true; // found the front cover; nothing better to look for
    }
    fallback ??= picture.artwork;
  });

  return fallback;
}

function parseApicFrame(
  frame: Uint8Array
): { artwork: EmbeddedArtwork; isFrontCover: boolean } | undefined {
  if (frame.length < 4) return undefined;

  const encoding = frame[0];
  let offset = 1;

  // MIME type is always Latin-1 and NUL-terminated, whatever the text encoding byte says.
  const mimeEnd = frame.indexOf(0, offset);
  if (mimeEnd < 0) return undefined;
  const mimeType = new TextDecoder("iso-8859-1").decode(frame.subarray(offset, mimeEnd));
  offset = mimeEnd + 1;

  if (offset >= frame.length) return undefined;
  const pictureType = frame[offset];
  offset += 1;

  // The description uses the frame's encoding, and UTF-16 terminates with two NUL bytes -
  // scanning for a single one lands inside a character and corrupts the offset.
  const wide = encoding === 1 || encoding === 2;
  offset = skipTerminatedString(frame, offset, wide);
  if (offset < 0 || offset >= frame.length) return undefined;

  return {
    artwork: { bytes: frame.subarray(offset), mimeType },
    isFrontCover: pictureType === 3,
  };
}

function skipTerminatedString(frame: Uint8Array, start: number, wide: boolean): number {
  if (!wide) {
    const end = frame.indexOf(0, start);
    return end < 0 ? -1 : end + 1;
  }
  for (let i = start; i + 1 < frame.length; i += 2) {
    if (frame[i] === 0 && frame[i + 1] === 0) return i + 2;
  }
  return -1;
}

const ID3_FRAMES: Record<string, keyof AudioTags> = {
  TIT2: "title",
  TPE1: "artist",
  TALB: "album",
};

function decodeTextFrame(frame: Uint8Array): string | undefined {
  if (frame.length < 2) return undefined;
  const encoding = frame[0];
  const body = frame.subarray(1);

  let text: string;
  switch (encoding) {
    case 0:
      text = new TextDecoder("iso-8859-1").decode(body);
      break;
    case 1:
      text = new TextDecoder("utf-16").decode(body);
      break;
    case 2:
      text = new TextDecoder("utf-16be").decode(body);
      break;
    case 3:
      text = new TextDecoder("utf-8").decode(body);
      break;
    default:
      return undefined;
  }
  // Frames are NUL-terminated and may be NUL-padded.
  return clean(text.replace(/\0.*$/, ""));
}

// --- shared ---------------------------------------------------------------------------

function assign(tags: AudioTags, rawKey: string, rawValue: string): void {
  const value = clean(rawValue);
  if (!value) return;
  switch (rawKey.trim().toUpperCase()) {
    case "TITLE":
      tags.title ??= value;
      break;
    case "ARTIST":
      tags.artist ??= value;
      break;
    case "ALBUM":
      tags.album ??= value;
      break;
  }
}

/** Blank or whitespace-only tags are absent, not empty strings. */
function clean(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function startsWith(data: Uint8Array, marker: string): boolean {
  if (data.length < marker.length) return false;
  for (let i = 0; i < marker.length; i += 1) {
    if (data[i] !== marker.charCodeAt(i)) return false;
  }
  return true;
}

function readU32BE(data: Uint8Array, offset: number): number {
  return (
    (data[offset] << 24) | (data[offset + 1] << 16) | (data[offset + 2] << 8) | data[offset + 3]
  );
}

/** ID3 sizes use seven bits per byte; a set high bit means it is not synchsafe. */
function synchsafe(data: Uint8Array, offset: number): number | undefined {
  if (offset + 4 > data.length) return undefined;
  let result = 0;
  for (let i = 0; i < 4; i += 1) {
    const byte = data[offset + i];
    if ((byte & 0x80) !== 0) return undefined;
    result = (result << 7) | byte;
  }
  return result;
}
