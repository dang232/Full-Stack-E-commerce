import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright config for VNShop frontend smoke + buyer happy-path E2E.
 *
 * Prereqs:
 *  - Backend stack up (`docker compose --profile apps up -d`) with Keycloak
 *    realm `vnshop` seeded and the `vnshop-admin-api` client configured
 *    (`bash infra/scripts/setup-keycloak-admin-client.sh`).
 *  - Frontend reachable at VITE_E2E_BASE_URL — defaults to the dockerised FE
 *    at http://localhost:3000 so the suite exercises the production bundle.
 *    Set VITE_E2E_BASE_URL=http://localhost:5173 to run against `npm run dev`
 *    (and also unset E2E_SKIP_WEBSERVER so Playwright boots vite for you).
 */

const baseURL = process.env.VITE_E2E_BASE_URL ?? "http://localhost:3000";
// The dockerised FE is already up; only auto-start a webServer when explicitly
// pointed at the dev port. Default behaviour: assume the stack is running.
const skipWebServer =
  process.env.E2E_SKIP_WEBSERVER !== undefined || baseURL.includes(":3000");

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
  webServer: skipWebServer
    ? undefined
    : {
        command: "npm run dev",
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
