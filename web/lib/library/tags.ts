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

function readId3(data: Uint8Array): AudioTags {
  const major = data[3];
  // 2.2 uses three-character frame ids and a different layout; misreading it would produce
  // wrong values, so it reports nothing instead.
  if (major !== 3 && major !== 4) return {};

  const flags = data[5];
  // Unsynchronisation rewrites the byte stream; refuse rather than misread.
  if ((flags & 0x80) !== 0) return {};

  const size = synchsafe(data, 6);
  if (size === undefined || size <= 0) return {};

  const end = Math.min(10 + size, data.length);
  let offset = 10;

  if ((flags & 0x40) !== 0) {
    // Skip the extended header.
    const extended =
      major === 4 ? synchsafe(data, offset) : readU32BE(data, offset) + 4;
    if (extended === undefined || offset + extended > end) return {};
    offset += extended;
  }

  const tags: AudioTags = {};

  while (offset + 10 <= end) {
    if (data[offset] === 0) break; // padding

    const frameId = String.fromCharCode(
      data[offset], data[offset + 1], data[offset + 2], data[offset + 3]
    );
    const frameSize =
      major === 4 ? synchsafe(data, offset + 4) : readU32BE(data, offset + 4);
    offset += 10;

    if (frameSize === undefined || frameSize < 0 || offset + frameSize > end) break;

    const key = ID3_FRAMES[frameId];
    if (key) {
      const value = decodeTextFrame(data.subarray(offset, offset + frameSize));
      if (value) tags[key] = value;
    }
    offset += frameSize;
  }
  return tags;
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
