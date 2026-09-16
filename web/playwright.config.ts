import { defineConfig, devices } from "@playwright/test";

/**
 * A small smoke set, not a second unit suite.
 *
 * docs/TEST_ARCHITECTURE.md asks for critical journeys only: the value here is catching what
 * unit tests structurally cannot - hydration mismatches, real routing, keyboard reachability,
 * and whether controls resolve to accessible names in a real browser.
 */
export default defineConfig({
  testDir: "./e2e",
  // Failures must be real. A retry would hide a genuine hydration race.
  retries: 0,
  fullyParallel: true,
  reporter: process.env.CI ? "line" : "list",
  use: {
    baseURL: "http://localhost:3000",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    // Built output, not dev: dev-only overlays and HMR are not what ships.
    command: "npm run build && npm run start",
    url: "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
