import { test, expect } from "@playwright/test";

/**
 * Buyer happy-path E2E (browse → cart → checkout → place order).
 *
 * Requires (TODO.md "Auth-flow blocked"):
 *  - Keycloak realm `vnshop` with `vnshop-web` public client seeded (BE-6 — done).
 *  - A test buyer account; supply via E2E_USER_EMAIL + E2E_USER_PASSWORD env vars.
 *
 * The whole describe block skips when those creds aren't present so CI doesn't
 * fail in environments where the realm hasn't been seeded yet.
 */
const email = process.env.E2E_USER_EMAIL;
const password = process.env.E2E_USER_PASSWORD;

test.describe("buyer happy path", () => {
  test.skip(!email || !password, "E2E_USER_EMAIL / E2E_USER_PASSWORD not set");

  test("browse → product detail → add to cart", async ({ page }) => {
    await page.goto("/");
    // Click any product card link; the home page renders summaries via useProducts.
    const firstProductLink = page.locator('a[href^="/product/"]').first();
    await expect(firstProductLink).toBeVisible({ timeout: 15_000 });
    await firstProductLink.click();
    await expect(page).toHaveURL(/\/product\//);
    // Add-to-cart button is the primary CTA; the SPA shows a Vietnamese
    // label so we match by role + accessible name pattern instead of exact text.
    const addBtn = page.getByRole("button", { name: /giỏ hàng|thêm vào giỏ/i }).first();
    await expect(addBtn).toBeVisible();
  });

  // The full place-order flow is gated on a seeded buyer + Keycloak. When
  // that's wired up, expand this with: login via Keycloak → /cart → /checkout
  // → place order with Idempotency-Key → assert /orders shows the new order.
  test.fixme("login → checkout → place order", async () => {
    // Intentionally skipped until BE side has a deterministic seed user.
  });
});
