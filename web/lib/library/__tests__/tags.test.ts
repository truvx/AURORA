import { describe, expect, it } from "vitest";
import { readTags } from "../tags";

/**
 * Built from synthetic byte layouts rather than fixture files, so the tests document the
 * formats exactly and run without binary assets in the repo.
 */

function fileFrom(bytes: Uint8Array): File {
  return new File([bytes as unknown as BlobPart], "test-audio");
}

function concat(...parts: Uint8Array[]): Uint8Array {
  const total = parts.reduce((sum, part) => sum + part.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.length;
  }
  return out;
}

function ascii(text: string): Uint8Array {
  return new Uint8Array([...text].map((c) => c.charCodeAt(0)));
}

function u32le(value: number): Uint8Array {
  const out = new Uint8Array(4);
  new DataView(out.buffer).setUint32(0, value, true);
  return out;
}

/** A FLAC file whose first metadata block is a Vorbis comment block. */
function flacWithComments(comments: string[]): Uint8Array {
  const vendor = ascii("aurora-test");
  const body = concat(
    u32le(vendor.length),
    vendor,
    u32le(comments.length),
    ...comments.flatMap((comment) => {
      const encoded = new TextEncoder().encode(comment);
      return [u32le(encoded.length), encoded];
    })
  );
  // Block header: last-block flag set, type 4, 24-bit big-endian length.
  const header = new Uint8Array([
    0x80 | 4,
    (body.length >> 16) & 0xff,
    (body.length >> 8) & 0xff,
    body.length & 0xff,
  ]);
  return concat(ascii("fLaC"), header, body);
}

function synchsafe(value: number): Uint8Array {
  return new Uint8Array([
    (value >> 21) & 0x7f,
    (value >> 14) & 0x7f,
    (value >> 7) & 0x7f,
    value & 0x7f,
  ]);
}

/** An ID3v2.4 tag containing UTF-8 text frames. */
function id3WithFrames(frames: Array<[string, string]>): Uint8Array {
  const encoded = frames.map(([id, value]) => {
    const text = new TextEncoder().encode(value);
    // Encoding byte 3 = UTF-8, then the text.
    const body = concat(new Uint8Array([3]), text);
    return concat(ascii(id), synchsafe(body.length), new Uint8Array([0, 0]), body);
  });
  const body = concat(...encoded);
  return concat(
    ascii("ID3"),
    new Uint8Array([4, 0, 0]), // v2.4, no flags
    synchsafe(body.length),
    body
  );
}

describe("readTags", () => {
  it("reads title, artist and album from FLAC comments", async () => {
    const file = fileFrom(
      flacWithComments(["TITLE=Real Title", "ARTIST=Real Artist", "ALBUM=Real Album"])
    );

    await expect(readTags(file)).resolves.toEqual({
      title: "Real Title",
      artist: "Real Artist",
      album: "Real Album",
    });
  });

  it("matches FLAC comment keys regardless of case", async () => {
    const file = fileFrom(flacWithComments(["title=lowercase", "Artist=Mixed"]));

    await expect(readTags(file)).resolves.toEqual({
      title: "lowercase",
      artist: "Mixed",
    });
  });

  it("leaves absent FLAC tags undefined rather than guessing", async () => {
    const file = fileFrom(flacWithComments(["TITLE=Only A Title"]));

    const tags = await readTags(file);
    expect(tags.title).toBe("Only A Title");
    // No artist tag means unknown; inventing one from the filename would be a lie.
    expect(tags.artist).toBeUndefined();
    expect(tags.album).toBeUndefined();
  });

  it("treats a whitespace-only tag as absent", async () => {
    const file = fileFrom(flacWithComments(["TITLE=Kept", "ARTIST=   "]));

    const tags = await readTags(file);
    expect(tags.title).toBe("Kept");
    expect(tags.artist).toBeUndefined();
  });

  it("reads UTF-8 text frames from an ID3v2.4 tag", async () => {
    const file = fileFrom(
      id3WithFrames([
        ["TIT2", "Tagged Title"],
        ["TPE1", "Tagged Artist"],
        ["TALB", "Tagged Album"],
      ])
    );

    await expect(readTags(file)).resolves.toEqual({
      title: "Tagged Title",
      artist: "Tagged Artist",
      album: "Tagged Album",
    });
  });

  it("preserves non-ASCII text", async () => {
    const file = fileFrom(id3WithFrames([["TPE1", "Sigur Rós"]]));

    await expect(readTags(file)).resolves.toMatchObject({ artist: "Sigur Rós" });
  });

  it("ignores frames it does not understand", async () => {
    const file = fileFrom(
      id3WithFrames([
        ["TCON", "Ambient"],
        ["TIT2", "Understood"],
      ])
    );

    await expect(readTags(file)).resolves.toEqual({ title: "Understood" });
  });

  it("returns nothing for a container it cannot parse", async () => {
    // MP4/M4A atoms are not implemented; reporting nothing is correct.
    const file = fileFrom(concat(new Uint8Array(4), ascii("ftypM4A ")));

    await expect(readTags(file)).resolves.toEqual({});
  });

  it("returns nothing rather than throwing on truncated data", async () => {
    const truncated = flacWithComments(["TITLE=Cut Short"]).slice(0, 9);

    await expect(readTags(fileFrom(truncated))).resolves.toEqual({});
  });

  it("returns nothing for an empty file", async () => {
    await expect(readTags(fileFrom(new Uint8Array(0)))).resolves.toEqual({});
  });
});
