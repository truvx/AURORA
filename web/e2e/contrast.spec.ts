import { expect, test } from "@playwright/test";

/**
 * Every visible piece of text, on every route, in both themes, against the colour actually
 * painted behind it.
 *
 * A browser is the only place this can be measured honestly: the app composites text over
 * translucent glass, and what a token says is not what the eye receives. Two failures were
 * found this way - the active nav label at 4.15:1, and the light theme's "Choose folder"
 * button at 3.23:1 - and both are fixed.
 *
 * Note the explicit colorScheme. Playwright defaults to light, and an earlier pass
 * measured the light theme twice while believing one run was dark.
 */

const ROUTES = ["/", "/search", "/library", "/now-playing", "/ai", "/settings"];

type Rgb = [number, number, number];

function luminance([r, g, b]: Rgb): number {
  const channel = (value: number) => {
    const v = value / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

function ratio(a: Rgb, b: Rgb): number {
  const [light, dark] = luminance(a) > luminance(b) ? [a, b] : [b, a];
  return (luminance(light) + 0.05) / (luminance(dark) + 0.05);
}


for (const colorScheme of ["dark", "light"] as const) {
  test.describe(`${colorScheme} theme`, () => {
    test.use({ colorScheme });

    for (const route of ROUTES) {
      test(`text on ${route} meets WCAG AA`, async ({ page }) => {
        await page.goto(route, { waitUntil: "networkidle" });

    const samples = await page.evaluate(() => {
          const parse = (value: string) => {
            const match = value.match(/rgba?\(([\d.]+),\s*([\d.]+),\s*([\d.]+)(?:,\s*([\d.]+))?\)/);
            if (!match) return null;
            return {
              rgb: [Number(match[1]), Number(match[2]), Number(match[3])],
              alpha: match[4] === undefined ? 1 : Number(match[4]),
            };
          };

          type Parsed = { rgb: number[]; alpha: number };
          const over = (top: Parsed, bottom: number[]) =>
            top.rgb.map((c: number, i: number) =>
              Math.round(c * top.alpha + bottom[i] * (1 - top.alpha))
            );

          const backdrop = (element: Element) => {
            let node: Element | null = element;
            const stack: Parsed[] = [];
            while (node) {
              const parsed = parse(getComputedStyle(node).backgroundColor);
              if (parsed && parsed.alpha > 0) stack.push(parsed);
              if (parsed && parsed.alpha === 1) break;
              node = node.parentElement;
            }
            let base = [255, 255, 255];
            for (const layer of stack.reverse()) base = over(layer, base);
            return base;
          };

          const results: {
            text: string;
            selector: string;
            foreground: number[];
            behind: number[];
            fontSize: number;
            fontWeight: number;
          }[] = [];
          for (const element of document.querySelectorAll("body *")) {
            const text = [...element.childNodes]
              .filter((n) => n.nodeType === Node.TEXT_NODE)
              .map((n) => n.textContent?.trim() ?? "")
              .join(" ")
              .trim();
            if (!text) continue;

            const style = getComputedStyle(element);
            if (style.visibility === "hidden" || style.display === "none") continue;
            if (Number(style.opacity) === 0) continue;
            const box = element.getBoundingClientRect();
            if (box.width === 0 || box.height === 0) continue;

            const colour = parse(style.color);
            if (!colour) continue;
            const behind = backdrop(element);
            const foreground = over(colour, behind);

            results.push({
              text: text.slice(0, 40),
              selector: element.tagName.toLowerCase() + (element.className && typeof element.className === "string" ? "." + element.className.split(" ")[0] : ""),
              foreground,
              behind,
              fontSize: parseFloat(style.fontSize),
              fontWeight: Number(style.fontWeight) || 400,
            });
          }
          return results;
        });


        expect(samples.length, "nothing was measured, so this would pass forever").toBeGreaterThan(0);

        const failures = samples
          .map((sample) => {
            const large =
              sample.fontSize >= 24 || (sample.fontSize >= 18.66 && sample.fontWeight >= 700);
            const minimum = large ? 3 : 4.5;
            const measured = ratio(sample.foreground as Rgb, sample.behind as Rgb);
            return { ...sample, measured, minimum };
          })
          .filter((sample) => sample.measured < sample.minimum)
          .map(
            (sample) =>
              `${sample.selector} "${sample.text}" ${sample.measured.toFixed(2)}:1 ` +
              `(needs ${sample.minimum}) fg=rgb(${sample.foreground}) bg=rgb(${sample.behind})`
          );

        expect(failures).toEqual([]);
      });
    }
  });
}
