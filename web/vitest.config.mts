import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/*
 * Two projects, because the suite has two kinds of test.
 *
 * Domain and repository tests run in node - they are the majority and they stay fast.
 * Component tests need a DOM, and paying for jsdom on all 200-odd of them would slow the
 * loop down for no reason. The split is by extension: .test.tsx is a component and
 * .dom.test.ts is anything else that needs a window; everything else is node.
 */
export default defineConfig({
  // The "@/..." paths the app imports by come from tsconfig, which Vite does not read.
  resolve: { alias: { "@": fileURLToPath(new URL(".", import.meta.url)) } },
  test: {
    projects: [
      {
        extends: true,
        test: {
          name: "node",
          environment: "node",
          include: ["**/__tests__/**/*.test.ts"],
          exclude: ["**/node_modules/**", "**/.next/**", "**/*.dom.test.ts"],
        },
      },
      {
        extends: true,
        test: {
          name: "dom",
          environment: "jsdom",
          include: ["**/__tests__/**/*.test.tsx", "**/__tests__/**/*.dom.test.ts"],
          exclude: ["**/node_modules/**", "**/.next/**"],
          setupFiles: ["./vitest.setup.ts"],
        },
      },
    ],
  },
});
