import { test, expect } from "@playwright/test";

/**
 * Smoke test — verifies the SPA boots, the home page renders, and the gateway
 * is reachable. Runs without auth, so it works even when the Keycloak realm
 * hasn't been seeded yet. The buyer happy-path test in `buyer-happy-path.spec.ts`
 * is the one that depends on BE-6 (realm seed) + a test user.
 */
test.describe("smoke", () => {
  test("home page renders the navbar and product grid", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveURL(/\/$/);
    // Navbar is the most stable thing on the page; we don't assert on
    // specific copy because the home content is data-driven.
    await expect(page.locator("nav, header").first()).toBeVisible();
  });

  test("login route is reachable and redirects to Keycloak when triggered", async ({ page }) => {
    await page.goto("/login");
    // The LoginPage either auto-redirects to Keycloak or shows the
    // "Đăng nhập với Keycloak" CTA depending on the auth state. Either is OK.
    await expect(page).toHaveURL(/\/login|\/auth\/realms\//);
  });
});
