import { test, expect, type APIRequestContext, type Page } from "@playwright/test";

/**
 * UI-driven QA spec for the admin coupon CRUD flow.
 *
 * What this proves through the actual SPA:
 *   - Click "+ Create coupon" → dialog opens with code/type/value fields
 *   - Submitting an empty code surfaces an inline validation toast
 *   - Submitting a valid FIXED-type coupon round-trips to the BE,
 *     dialog closes, the new code appears in the table
 *
 * Locks in c235b289 (the coupon-service envelope wrap) end-to-end:
 * the create mutation goes through the FE form → BE → list refresh,
 * and any drift on either side fails this test.
 */

const apiURL = process.env.VITE_E2E_API_URL ?? "http://localhost:8080";

interface AuthResult {
  accessToken: string;
}

async function loginAsAdmin(request: APIRequestContext): Promise<AuthResult> {
  const r = await request.post(`${apiURL}/auth/login`, {
    data: { username: "admin1", password: "test" },
  });
  expect(r.ok(), `admin login: ${r.status()}`).toBeTruthy();
  const accessToken = (await r.json())?.data?.accessToken;
  expect(accessToken).toBeTruthy();
  return { accessToken };
}

async function expectNoGlobalError(page: Page): Promise<void> {
  await expect(page.getByText(/Có lỗi xảy ra|Something went wrong/i)).toHaveCount(0);
  await expect(page.getByText(/Invalid input/i)).toHaveCount(0);
}

async function gotoCouponsTab(page: Page): Promise<void> {
  await page.goto("/admin");
  await expect(
    page.getByText(/Admin Dashboard|Tổng quan|Admin Console/i).first(),
  ).toBeVisible({ timeout: 20_000 });
  const tab = page.getByRole("button", { name: /^(Coupons|Coupon)$/i }).first();
  await expect(tab).toBeVisible({ timeout: 10_000 });
  await tab.click();
  await expect(
    page.getByText(/Coupon management|Quản lý coupon/i).first(),
  ).toBeVisible({ timeout: 15_000 });
}

test.describe("admin coupon CRUD UI", () => {
  test("Empty code triggers inline validation toast (no BE round-trip)", async ({ page }) => {
    await loginAsAdmin(page.request);
    await gotoCouponsTab(page);

    // Open the dialog.
    await page.getByRole("button", { name: /Create coupon|\+ Tạo coupon|Tạo coupon/i }).first().click();
    await expect(
      page.getByText(/Create new coupon|Tạo coupon mới/i).first(),
    ).toBeVisible({ timeout: 10_000 });

    // Click the dialog's submit (anchored on the dialog footer label) without
    // typing anything. The FE blocks with a toast.
    await page.getByRole("button", { name: /^(Create coupon|Tạo coupon)$/i }).last().click();

    await expect(
      page.getByText(/Please enter a coupon code|Vui lòng nhập mã coupon/i).first(),
    ).toBeVisible({ timeout: 10_000 });

    await expectNoGlobalError(page);
  });

  test("Creating a FIXED coupon round-trips and the row appears in the table", async ({
    page,
  }) => {
    await loginAsAdmin(page.request);
    await gotoCouponsTab(page);

    // Generate a unique code so re-runs don't collide with previously
    // created coupons (the BE rejects duplicates).
    const code = `UICRUD${Date.now() % 1_000_000}`;

    await page.getByRole("button", { name: /Create coupon|\+ Tạo coupon|Tạo coupon/i }).first().click();
    await expect(
      page.getByText(/Create new coupon|Tạo coupon mới/i).first(),
    ).toBeVisible({ timeout: 10_000 });

    // Fill the code input via its id (the field auto-uppercases).
    await page.locator("#admin-coupon-code").fill(code.toLowerCase());

    // Switch to FIXED so the BE's enum check accepts the value as-is
    // (FE sends "FIXED", BE has DiscountType.FIXED — clean match).
    await page.getByRole("button", { name: /^(Fixed amount \(₫\)|Số tiền cố định)/i }).click();

    // Fixed value of 50000 ₫.
    await page.locator("#admin-coupon-value").fill("50000");

    // Submit.
    await page.getByRole("button", { name: /^(Create coupon|Tạo coupon)$/i }).last().click();

    // Success toast.
    await expect(
      page.getByText(/Coupon created|Đã tạo coupon/i).first(),
    ).toBeVisible({ timeout: 15_000 });

    // The dialog closes and the new code appears in the list.
    await expect(page.getByText(code, { exact: false }).first()).toBeVisible({
      timeout: 10_000,
    });

    await expectNoGlobalError(page);
  });
});
