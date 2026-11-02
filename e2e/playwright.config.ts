import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "tests",
  globalSetup: "./wait-for-app.ts",
  reporter: "list",
  use: {
    baseURL: process.env.BASE_URL ?? "http://localhost:8000",
    screenshot: "only-on-failure",
  },
});
