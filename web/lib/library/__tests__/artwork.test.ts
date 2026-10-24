import { describe, expect, it } from "vitest";
import { readEmbeddedArtwork, readTags } from "../tags";

/**
 * Cover art extraction, built from synthetic files.
 *
 * Real fixtures would be megabytes of binary in the repository and would only cover whatever
 * encoder produced them; assembling the bytes here means each test states exactly which
 * structural detail it is about. The awkward cases are real ones: a file carrying several
 * pictures, and a UTF-16 description whose terminator is two bytes rather than one.
 */

const ascii = (text: string) => Array.from(text, (c) => c.charCodeAt(0));

function file(bytes: number[]): File {
  return new File([new Uint8Array(bytes)], "track.bin");
}

// --- FLAC ------------------------------------------------------------------------------

function flacPictureBlock(options: {
  pictureType: number;
  mimeType: string;
  description?: string;
  data: number[];
}): number[] {
  const mime = ascii(options.mimeType);
  const description = ascii(options.description ?? "");
  const u32 = (n: number) => [(n >>> 24) & 0xff, (n >>> 16) & 0xff, (n >>> 8) & 0xff, n & 0xff];

  return [
    ...u32(options.pictureType),
    ...u32(mime.length), ...mime,
    ...u32(description.length), ...description,
    ...u32(0), ...u32(0), ...u32(0), ...u32(0), // width, height, depth, colours
    ...u32(options.data.length), ...options.data,
  ];
}

function flacFile(blocks: { type: number; body: number[] }[]): File {
  const bytes = [...ascii("fLaC")];
  blocks.forEach((block, index) => {
    const isLast = index === blocks.length - 1;
    bytes.push((isLast ? 0x80 : 0) | block.type);
    bytes.push(
      (block.body.length >>> 16) & 0xff,
      (block.body.length >>> 8) & 0xff,
      block.body.length & 0xff
    );
    bytes.push(...block.body);
  });
  return file(bytes);
}

describe("FLAC artwork", () => {
  it("reads a picture block", async () => {
    const artwork = await readEmbeddedArtwork(
      flacFile([
        { type: 6, body: flacPictureBlock({ pictureType: 3, mimeType: "image/jpeg", data: [1, 2, 3, 4] }) },
      ])
    );

    expect(artwork?.mimeType).toBe("image/jpeg");
    expect(Array.from(artwork!.bytes)).toEqual([1, 2, 3, 4]);
  });

  it("prefers the front cover over other pictures", async () => {
    // Files routinely carry the back cover and booklet pages too. Taking whichever came
    // first would show the back of the sleeve about as often as the front.
    const artwork = await readEmbeddedArtwork(
      flacFile([
        { type: 6, body: flacPictureBlock({ pictureType: 4, mimeType: "image/png", data: [9, 9] }) },
        { type: 6, body: flacPictureBlock({ pictureType: 3, mimeType: "image/jpeg", data: [7, 7] }) },
      ])
    );

    expect(Array.from(artwork!.bytes)).toEqual([7, 7]);
  });

  it("falls back to another picture when there is no front cover", async () => {
    const artwork = await readEmbeddedArtwork(
      flacFile([
        { type: 6, body: flacPictureBlock({ pictureType: 4, mimeType: "image/png", data: [5] }) },
      ])
    );

    expect(Array.from(artwork!.bytes)).toEqual([5]);
  });

  it("skips past other metadata blocks to find the picture", async () => {
    const artwork = await readEmbeddedArtwork(
      flacFile([
        { type: 0, body: [0, 0, 0, 0] }, // STREAMINFO
        { type: 4, body: [0, 0, 0, 0, 0, 0, 0, 0] }, // VORBIS_COMMENT
        { type: 6, body: flacPictureBlock({ pictureType: 3, mimeType: "image/jpeg", data: [42] }) },
      ])
    );

    expect(Array.from(artwork!.bytes)).toEqual([42]);
  });

  it("reports nothing when the file carries no picture", async () => {
    const artwork = await readEmbeddedArtwork(flacFile([{ type: 0, body: [0, 0, 0, 0] }]));

    expect(artwork).toBeUndefined();
  });

  it("does not read past the end of a truncated block", async () => {
    // A block header claiming more bytes than the file holds must not produce artwork built
    // from whatever happened to follow in memory.
    const bytes = [...ascii("fLaC"), 0x86, 0xff, 0xff, 0xff, 1, 2, 3];

    expect(await readEmbeddedArtwork(file(bytes))).toBeUndefined();
  });
});

// --- ID3v2 -----------------------------------------------------------------------------

function id3File(frames: { id: string; body: number[] }[], major = 3): File {
  const frameBytes: number[] = [];
  for (const frame of frames) {
    frameBytes.push(...ascii(frame.id));
    const size = frame.body.length;
    if (major === 4) {
      frameBytes.push((size >>> 21) & 0x7f, (size >>> 14) & 0x7f, (size >>> 7) & 0x7f, size & 0x7f);
    } else {
      frameBytes.push((size >>> 24) & 0xff, (size >>> 16) & 0xff, (size >>> 8) & 0xff, size & 0xff);
    }
    frameBytes.push(0, 0); // frame flags
    frameBytes.push(...frame.body);
  }

  const total = frameBytes.length;
  return file([
    ...ascii("ID3"), major, 0, 0,
    (total >>> 21) & 0x7f, (total >>> 14) & 0x7f, (total >>> 7) & 0x7f, total & 0x7f,
    ...frameBytes,
  ]);
}

function apic(options: {
  encoding: number;
  mimeType: string;
  pictureType: number;
  description: number[];
  data: number[];
}): number[] {
  return [
    options.encoding,
    ...ascii(options.mimeType), 0,
    options.pictureType,
    ...options.description,
    ...options.data,
  ];
}

describe("ID3 artwork", () => {
  it("reads an APIC frame", async () => {
    const artwork = await readEmbeddedArtwork(
      id3File([
        {
          id: "APIC",
          body: apic({
            encoding: 0, mimeType: "image/jpeg", pictureType: 3,
            description: [0], data: [1, 2, 3],
          }),
        },
      ])
    );

    expect(artwork?.mimeType).toBe("image/jpeg");
    expect(Array.from(artwork!.bytes)).toEqual([1, 2, 3]);
  });

  it("handles a UTF-16 description terminated by two NUL bytes", async () => {
    // Scanning for a single NUL lands inside a UTF-16 character and shifts every following
    // offset, which yields picture data that starts a byte or two early.
    const artwork = await readEmbeddedArtwork(
      id3File([
        {
          id: "APIC",
          body: apic({
            encoding: 1, mimeType: "image/png", pictureType: 3,
            // "A" in UTF-16LE with a BOM, then the double-NUL terminator.
            description: [0xff, 0xfe, 0x41, 0x00, 0x00, 0x00],
            data: [8, 9],
          }),
        },
      ])
    );

    expect(Array.from(artwork!.bytes)).toEqual([8, 9]);
  });

  it("prefers the front cover", async () => {
    const artwork = await readEmbeddedArtwork(
      id3File([
        {
          id: "APIC",
          body: apic({
            encoding: 0, mimeType: "image/png", pictureType: 4,
            description: [0], data: [9],
          }),
        },
        {
          id: "APIC",
          body: apic({
            encoding: 0, mimeType: "image/jpeg", pictureType: 3,
            description: [0], data: [7],
          }),
        },
      ])
    );

    expect(Array.from(artwork!.bytes)).toEqual([7]);
  });

  it("finds the picture past other frames", async () => {
    const artwork = await readEmbeddedArtwork(
      id3File([
        { id: "TIT2", body: [0, ...ascii("A title")] },
        {
          id: "APIC",
          body: apic({
            encoding: 0, mimeType: "image/jpeg", pictureType: 3,
            description: [0], data: [4, 5],
          }),
        },
      ])
    );

    expect(Array.from(artwork!.bytes)).toEqual([4, 5]);
  });

  it("works in ID3v2.4, whose frame sizes are synchsafe", async () => {
    const artwork = await readEmbeddedArtwork(
      id3File(
        [
          {
            id: "APIC",
            body: apic({
              encoding: 3, mimeType: "image/jpeg", pictureType: 3,
              description: [0], data: [6],
            }),
          },
        ],
        4
      )
    );

    expect(Array.from(artwork!.bytes)).toEqual([6]);
  });

  it("reports nothing when there is no APIC frame", async () => {
    const artwork = await readEmbeddedArtwork(
      id3File([{ id: "TIT2", body: [0, ...ascii("A title")] }])
    );

    expect(artwork).toBeUndefined();
  });

  it("refuses unsynchronised tags rather than misreading them", async () => {
    // Unsynchronisation rewrites the byte stream, so offsets computed from it are wrong.
    const bytes = [...ascii("ID3"), 3, 0, 0x80, 0, 0, 0, 10, ...new Array(10).fill(0)];

    expect(await readEmbeddedArtwork(file(bytes))).toBeUndefined();
  });
});

describe("artwork extraction and tag reading stay independent", () => {
  it("still reads tags from a file that also carries artwork", async () => {
    // The two share a frame walker now; this is what catches one breaking the other.
    const source = id3File([
      { id: "TIT2", body: [0, ...ascii("Nightfall")] },
      {
        id: "APIC",
        body: apic({
          encoding: 0, mimeType: "image/jpeg", pictureType: 3,
          description: [0], data: [1],
        }),
      },
      { id: "TPE1", body: [0, ...ascii("Aoi Mori")] },
    ]);

    expect(await readTags(source)).toEqual({ title: "Nightfall", artist: "Aoi Mori" });
    expect(Array.from((await readEmbeddedArtwork(source))!.bytes)).toEqual([1]);
  });

  it("reports nothing for a file that is neither FLAC nor ID3", async () => {
    expect(await readEmbeddedArtwork(file(ascii("RIFF....WAVE")))).toBeUndefined();
  });
});
