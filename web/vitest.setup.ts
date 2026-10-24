import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// jsdom keeps one document for the whole file, so without this each test renders into the
// leftovers of the last one and getByRole starts finding two of everything.
afterEach(cleanup);
