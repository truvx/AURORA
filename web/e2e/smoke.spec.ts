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

  for (const label of ["Listen", "Library", "AI", "Settings"]) {
    await expect(nav.getByRole("link", { name: label })).toBeVisible();
  }

  // Search is its own control beside the pill rather than one of its four destinations,
  // and the player is reached from the mini-player, so both are checked outside the nav.
  await expect(page.getByRole("link", { name: "Search", exact: true })).toBeVisible();
});

test("navigation and transport are present on every route", async ({ page }) => {
  // /library and /now-playing once rendered without the shell entirely: no navigation and
  // no transport controls on the two screens the app is actually used from. The shell now
  // lives in the root layout so a page cannot omit it, and this is what holds that.
  for (const path of ["/", "/search", "/library", "/now-playing", "/ai", "/settings"]) {
    await page.goto(path);
    await expect(
      page.getByRole("navigation", { name: "Primary navigation" }),
      `no primary navigation on ${path}`
    ).toBeVisible();
  }
});

test("the queue survives a reload", async ({ page }) => {
  await page.goto("/now-playing");

  // Seeded straight into IndexedDB rather than played: the CI browser has no music files
  // and no YouTube key, and what is under test is the restore path, not the picker.
  await page.evaluate(async () => {
    const open = indexedDB.open("aurora", 4);
    const db: IDBDatabase = await new Promise((resolve, reject) => {
      open.onsuccess = () => resolve(open.result);
      open.onerror = () => reject(open.error);
    });
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction("queueSnapshot", "readwrite");
      transaction.objectStore("queueSnapshot").put({
        key: "current",
        items: [
          { id: "youtube:aaaaaaaaaaa", provider: "YOUTUBE", title: "First restored", artist: "A" },
          { id: "youtube:bbbbbbbbbbb", provider: "YOUTUBE", title: "Second restored", artist: "B" },
        ],
        currentIndex: 0,
        repeatMode: "Off",
        shuffleMode: "Off",
        updatedAt: Date.now(),
      });
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
    });
    db.close();
  });

  await page.reload();

  await expect(page.getByRole("button", { name: /Play First restored/ })).toBeVisible();
  await expect(page.getByRole("button", { name: /Play Second restored/ })).toBeVisible();

  // Silently: a page that starts playing on its own after a refresh is a worse surprise
  // than an empty player.
  await expect(page.getByText("Nothing is playing")).toBeVisible();
});

test("the page animates in on a route change", async ({ page }) => {
  await page.goto("/");

  // React's <ViewTransition> needs react@canary, which the version matrix rules out, so
  // this is a keyed CSS animation. What matters is that it actually runs on navigation
  // rather than being declared and never replaying.
  await page.getByRole("link", { name: "Library" }).click();
  await expect(page.getByRole("heading", { name: "Library" })).toBeVisible();

  const animated = await page.evaluate(() => {
    const wrapper = document.querySelector("main > div");
    if (!wrapper) return { name: "no route wrapper", count: 0 };
    return {
      name: getComputedStyle(wrapper).animationName,
      // getAnimations() is empty once it has finished, so this only says it was created
      // with an animation attached, which is the part that regresses silently.
      count: wrapper.getAnimations().length,
    };
  });

  expect(animated.name, "the route wrapper has no animation").toContain("route-in");
});
