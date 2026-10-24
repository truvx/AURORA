import next from "eslint-config-next";

// ESLint 9 flat config. `next lint` was removed in Next 16, so linting runs
// through eslint directly (see the "lint" script in package.json).
const config = [
  {
    ignores: [
      ".next/**",
      "node_modules/**",
      "next-env.d.ts",
      "*.tsbuildinfo",
    ],
  },
  ...next,
];

export default config;
