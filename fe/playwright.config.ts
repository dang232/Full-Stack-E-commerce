import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright config for VNShop frontend smoke + buyer happy-path E2E.
 *
 * Prereqs (see TODO.md "Auth-flow blocked" section):
 *  - Backend stack up (`docker-compose up`) with Keycloak realm `vnshop` seeded
 *    and the `vnshop-web` public client present (BE-6 — done).
 *  - Vite dev server reachable at VITE_E2E_BASE_URL (default http://localhost:5173).
 *  - Test user credentials supplied via env vars (E2E_USER_EMAIL / E2E_USER_PASSWORD).
 *    Skip-marked tests fall through gracefully when these aren't set so CI doesn't
 *    fail when the realm hasn't been seeded.
 */

const baseURL = process.env.VITE_E2E_BASE_URL ?? "http://localhost:5173";

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: process.env.E2E_SKIP_WEBSERVER
    ? undefined
    : {
        command: "npm run dev",
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
