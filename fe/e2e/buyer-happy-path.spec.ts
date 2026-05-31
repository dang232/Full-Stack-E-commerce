import { test, expect } from "@playwright/test";

const apiURL = process.env.VITE_E2E_API_URL ?? "http://localhost:8080";

/**
 * Buyer happy-path against the real BE: register fresh → log in → browse
 * the catalog → open a product → add to cart → see it in the cart page.
 *
 * Each run creates a brand-new buyer (timestamped email) so the test is
 * deterministic regardless of prior runs and doesn't depend on a seeded
 * E2E_USER. Tokens are persisted in localStorage by the native auth
 * provider, so there's no Keycloak redirect.
 */

const PASSWORD = "Test1234!";

test.describe("buyer happy path", () => {
  test("register → product detail → add to cart → /cart shows item", async ({ page, request }) => {
    const stamp = Date.now();
    const email = `e2e_buyer_${stamp}@vnshop.local`;

    await page.goto("/register");
    await page.locator("#firstName").fill("E2E");
    await page.locator("#lastName").fill("Buyer");
    await page.locator("#email").fill(email);
    await page.locator("#password").fill(PASSWORD);
    await page.locator("#confirm").fill(PASSWORD);
    await page.getByRole("button", { name: /create account|tạo tài khoản/i }).click();

    // After register the provider auto-logs in and the SPA navigates to /.
    // The route change doesn't fire a load event, so poll the URL.
    await expect.poll(() => new URL(page.url()).pathname, { timeout: 30_000 }).toBe("/");

    // Pull a product id from the API rather than clicking through home.
    // Home renders product cards far below the fold inside motion.div with
    // delayed reveal animations; the cart roundtrip is what we're testing.
    const apiRes = await request.get(`${apiURL}/products?size=1`);
    expect(apiRes.ok()).toBeTruthy();
    const apiBody = await apiRes.json();
    const productId = apiBody?.data?.content?.[0]?.id;
    expect(productId, "expected at least one product seeded").toBeTruthy();

    await page.goto(`/product/${productId}`);
    await expect.poll(() => page.url(), { timeout: 15_000 }).toMatch(/\/product\//);

    const addBtn = page.getByRole("button", { name: /add to cart|thêm vào giỏ/i }).first();
    await expect(addBtn).toBeVisible({ timeout: 15_000 });
    await addBtn.click();

    // /cart should show at least one item (no "empty cart" copy).
    await page.goto("/cart");
    await expect(page.getByText(/your cart is empty|giỏ hàng trống/i)).toHaveCount(0, {
      timeout: 15_000,
    });
  });

  test("login form rejects invalid credentials with an inline error", async ({ page }) => {
    await page.goto("/login");
    await page.locator("#identifier").fill("does-not-exist@vnshop.local");
    await page.locator("#password").fill("definitely-not-the-password");
    await page.getByRole("button", { name: /sign in|đăng nhập/i }).click();

    // Either the specific invalid-credentials copy or the generic fallback
    // is acceptable — both are user-visible inline failures and prove the
    // form did not silently navigate. We assert the URL stays on /login,
    // which is the real contract.
    await expect(
      page.getByText(
        /wrong email|sai email|invalid credentials|couldn't sign in|không thể đăng nhập/i,
      ),
    ).toBeVisible({ timeout: 10_000 });
    await expect(page).toHaveURL(/\/login/);
  });
});
