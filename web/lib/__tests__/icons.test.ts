import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Every icon the source draws must be in the subsetted font.
 *
 * The shipped font contains only the glyphs listed in app/fonts/icons.txt, and a missing
 * one fails silently in the worst way: the ligature does not resolve, so the icon renders
 * as its own name in words. "queue_music" appeared across the player's transport bar
 * exactly this way.
 */

const ROOT = join(__dirname, "..", "..");

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    if (entry === "node_modules" || entry === ".next") return [];
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return path.endsWith(".tsx") ? [path] : [];
  });
}

/**
 * Icon names the source draws.
 *
 * Two shapes, because the app writes them two ways: inline inside a material-symbols span
 * (including both branches of a ternary), and as `icon: "name"` in the navigation config.
 * Missing the second shape is what made six genuinely used glyphs look unused.
 */
function iconsIn(source: string): string[] {
  const names: string[] = [];

  const spans = source.matchAll(
    /className="material-symbols-outlined"[^>]*>([\s\S]*?)<\/span>/g
  );
  for (const [, body] of spans) {
    for (const [, name] of body.matchAll(/"([a-z][a-z_]*)"/g)) names.push(name);
    const bare = body.replace(/\{[\s\S]*?\}/g, "").trim();
    if (/^[a-z][a-z_]*$/.test(bare)) names.push(bare);
  }

  for (const [, name] of source.matchAll(/\bicon:\s*"([a-z][a-z_]*)"/g)) names.push(name);

  return names;
}

describe("icon font coverage", () => {
  const listed = new Set(
    readFileSync(join(ROOT, "app/fonts/icons.txt"), "utf8")
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith("#"))
  );

  const used = new Map<string, string>();
  for (const file of [...sourceFiles(join(ROOT, "app")), ...sourceFiles(join(ROOT, "components"))]) {
    for (const name of iconsIn(readFileSync(file, "utf8"))) {
      used.set(name, file.replace(ROOT + "/", ""));
    }
  }

  it("finds the icons actually used", () => {
    // A guard on the guard: if the scan matched nothing, the test below would pass forever.
    expect(used.size).toBeGreaterThan(5);
  });

  it("ships a glyph for every icon the source draws", () => {
    const missing = [...used.entries()]
      .filter(([name]) => !listed.has(name))
      .map(([name, file]) => `${name} (${file})`);

    expect(missing, "add these to app/fonts/icons.txt and regenerate the font").toEqual([]);
  });

  it("does not ship glyphs nothing uses", () => {
    // Not a failure, but worth knowing: every unused glyph is bytes for nothing.
    const unused = [...listed].filter((name) => !used.has(name));
    expect(unused, "remove these from app/fonts/icons.txt and regenerate").toEqual([]);
  });
});
