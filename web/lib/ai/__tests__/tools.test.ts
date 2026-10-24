import { describe, expect, it } from "vitest";
import { TOOLS } from "../tools";

/**
 * The tool list is the contract between the model and the Android executor. Drift between
 * them is not a type error in either project: findSimilarMusic was declared here and
 * unhandled on the client, so every "find something similar" request silently returned
 * nothing with no error anywhere.
 */
describe("AI tool allowlist", () => {
  const EXECUTOR_HANDLES = [
    "addToQueue",
    "findSimilarMusic",
    "playTrack",
    "searchMusic",
  ];

  it("declares exactly the tools the Android executor handles", () => {
    expect(TOOLS.map((tool) => tool.name).sort()).toEqual([...EXECUTOR_HANDLES].sort());
  });

  it("gives every tool a description and an object parameter schema", () => {
    for (const tool of TOOLS) {
      expect(tool.description, `${tool.name} needs a description`).toBeTruthy();
      expect(tool.parameters.type, `${tool.name} parameters`).toBe("object");
    }
  });

  it("requires an identifier on tools that act on a specific track", () => {
    // Without a required id the model can emit an action with nothing to resolve, and the
    // executor's anti-fabrication check has nothing to check.
    const playTrack = TOOLS.find((tool) => tool.name === "playTrack");
    expect(playTrack?.parameters.required).toContain("trackId");

    const similar = TOOLS.find((tool) => tool.name === "findSimilarMusic");
    expect(similar?.parameters.required).toContain("referenceTrackId");
  });

  it("does not declare any tool that mutates state beyond the queue", () => {
    // The spec's allowlist is search, metadata, recommendation, queue, and playback
    // proposals. Anything else reaching the model is a policy change, not a code change.
    const allowed = new Set(EXECUTOR_HANDLES);
    for (const tool of TOOLS) {
      expect(allowed.has(tool.name), `${tool.name} is not on the documented allowlist`).toBe(true);
    }
  });
});
