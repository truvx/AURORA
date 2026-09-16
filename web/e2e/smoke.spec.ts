import { expect, test } from "@playwright/test";

/**
 * Critical journeys only.
 *
 * These cover what unit tests structurally cannot: real hydration, real routing, real
 * keyboard reachability, and whether controls resolve to accessible names in a browser.
 * Every one of these has already caught a bug in this project.
 */

/** Console errors are a failure signal: a hydration mismatch only ever shows up here. */
function failOnConsoleErrors(page: import("@playwright/test").Page): string[] {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

test("every primary route renders without console errors", async ({ page }) => {
  const errors = failOnConsoleErrors(page);

  for (const path of ["/", "/search", "/library", "/now-playing", "/ai", "/settings"]) {
    await page.goto(path);
    await expect(page.locator("body")).toBeVisible();
  }

  // A hydration failure shipped undetected here once; it is only visible at runtime.
  expect(errors, `console errors: ${errors.join(" | ")}`).toEqual([]);
});

test("the library exposes its views and they switch", async ({ page }) => {
  await page.goto("/library");

  // The view switch was once gated on having tracks, so a new user could never reach
  // playlists at all.
  for (const name of ["Tracks", "Albums", "Artists", "Playlists"]) {
    await expect(page.getByRole("button", { name, exact: true })).toBeVisible();
  }

  await page.getByRole("button", { name: "Playlists", exact: true }).click();
  await expect(page.getByPlaceholder("New playlist name")).toBeVisible();
});

test("a playlist can be created and survives a reload", async ({ page }) => {
  await page.goto("/library");
  await page.getByRole("button", { name: "Playlists", exact: true }).click();

  const name = `Smoke ${Date.now()}`;
  await page.getByPlaceholder("New playlist name").fill(name);
  await page.getByRole("button", { name: "Create", exact: true }).click();

  await expect(page.getByRole("button", { name: `Open playlist ${name}` })).toBeVisible();

  // Persistence is the point: this proves IndexedDB actually retained it, which no unit
  // test against a fake can show.
  await page.reload();
  await page.getByRole("button", { name: "Playlists", exact: true }).click();
  await expect(page.getByRole("button", { name: `Open playlist ${name}` })).toBeVisible();
});

test("playlist controls have accessible names", async ({ page }) => {
  await page.goto("/library");
  await page.getByRole("button", { name: "Playlists", exact: true }).click();

  const name = `Named ${Date.now()}`;
  await page.getByPlaceholder("New playlist name").fill(name);
  await page.getByRole("button", { name: "Create", exact: true }).click();

  // The open button once computed no name from its content, leaving an unnamed button in
  // the tree - unusable with a screen reader and invisible to this kind of query.
  await expect(page.getByRole("button", { name: `Open playlist ${name}` })).toBeVisible();
  await expect(page.getByRole("button", { name: `Delete playlist ${name}` })).toBeVisible();
});

test("the library is navigable by keyboard alone", async ({ page }) => {
  await page.goto("/library");

  // Tab until a known control takes focus. Every action must be keyboard reachable, and a
  // control that can only be clicked would never be found here.
  let reached = false;
  for (let i = 0; i < 25 && !reached; i += 1) {
    await page.keyboard.press("Tab");
    reached = await page.evaluate(
      () => document.activeElement?.textContent?.trim() === "Playlists"
    );
  }

  expect(reached, "the Playlists control was not reachable by keyboard").toBe(true);
  await page.keyboard.press("Enter");
  await expect(page.getByPlaceholder("New playlist name")).toBeVisible();
});

test("the now playing route states plainly that nothing is playing", async ({ page }) => {
  await page.goto("/now-playing");

  // An empty state must say so rather than render a player with blank fields.
  await expect(page.getByText("Nothing is playing")).toBeVisible();
});

test("primary navigation is labelled and links to every route", async ({ page }) => {
  await page.goto("/");

  const nav = page.getByRole("navigation", { name: "Primary navigation" });
  await expect(nav).toBeVisible();

  for (const label of ["Home", "Search", "Library", "Playing", "AI", "Settings"]) {
    await expect(nav.getByRole("link", { name: label })).toBeVisible();
  }
});
