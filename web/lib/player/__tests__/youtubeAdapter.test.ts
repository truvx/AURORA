import { describe, expect, it } from "vitest";
import { extractVideoId } from "../youtubeAdapter";

/**
 * Id extraction is the part that runs without a browser. The player itself needs a real
 * IFrame and is exercised in the browser rather than here.
 */
describe("extractVideoId", () => {
  it("reads the provider-prefixed id AURORA uses internally", () => {
    expect(extractVideoId("youtube:dQw4w9WgXcQ")).toBe("dQw4w9WgXcQ");
  });

  it("reads a standard watch URL", () => {
    expect(extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).toBe("dQw4w9WgXcQ");
  });

  it("reads a short youtu.be URL", () => {
    expect(extractVideoId("https://youtu.be/dQw4w9WgXcQ")).toBe("dQw4w9WgXcQ");
  });

  it("accepts a bare eleven-character id", () => {
    expect(extractVideoId("dQw4w9WgXcQ")).toBe("dQw4w9WgXcQ");
  });

  it("rejects a local track id rather than treating it as a video", () => {
    // Local ids are file paths; mistaking one for a video id would send a nonsense request.
    expect(extractVideoId("Album/01 Track.flac")).toBeUndefined();
  });

  it("rejects an empty provider prefix", () => {
    expect(extractVideoId("youtube:")).toBeUndefined();
  });

  it("rejects an unrelated URL", () => {
    expect(extractVideoId("https://example.com/watch?v=dQw4w9WgXcQ")).toBeUndefined();
  });

  it("rejects an empty string", () => {
    expect(extractVideoId("")).toBeUndefined();
  });
});
