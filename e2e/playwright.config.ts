import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "tests",
  globalSetup: "./wait-for-app.ts",
  reporter: "list",
  // The tests share one database, and later ones assert on what earlier ones left behind.
  workers: 1,
  use: {
    baseURL: process.env.BASE_URL ?? "http://localhost:8000",
    screenshot: "only-on-failure",
  },
});
