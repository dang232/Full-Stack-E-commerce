import { test, expect, type APIRequestContext, type Page } from "@playwright/test";

/**
 * UI-driven QA spec for the Profile → Addresses tab.
 *
 * What this proves through the actual SPA:
 *   - Click "Add address" → form fields render
 *   - Submit invalid (empty) form → validation toast / inline error,
 *     no row added
 *   - Submit a valid address through the actual form fields → the row
 *     appears in the address list with the typed street + city
 *
 * Locks in the address-management mutation path (post + state update +
 * re-render) which exercises the userProfileSchema transform on the
 * mutation response.
 */

const apiURL = process.env.VITE_E2E_API_URL ?? "http://localhost:8080";
const PASSWORD = "Test1234!";

interface SeededBuyer {
  email: string;
  accessToken: string;
}

async function seedBuyer(request: APIRequestContext): Promise<SeededBuyer> {
  const stamp = Date.now() + Math.floor(Math.random() * 1_000);
  const email = `e2e_qa_addr_${stamp}@vnshop.local`;
  const reg = await request.post(`${apiURL}/auth/register`, {
    data: { firstName: "QA", lastName: "Addr", email, password: PASSWORD },
  });
  expect(reg.ok(), `register: ${reg.status()} ${await reg.text()}`).toBeTruthy();
  const login = await request.post(`${apiURL}/auth/login`, {
    data: { username: email, password: PASSWORD },
  });
  expect(login.ok()).toBeTruthy();
  const accessToken = (await login.json())?.data?.accessToken;
  expect(accessToken).toBeTruthy();
  return { email, accessToken };
}

async function expectNoGlobalError(page: Page): Promise<void> {
  await expect(page.getByText(/Có lỗi xảy ra|Something went wrong/i)).toHaveCount(0);
  await expect(page.getByText(/Invalid input/i)).toHaveCount(0);
}

test.describe("profile addresses UI", () => {
  test("Add address form opens and accepts a valid submission", async ({ page }) => {
    await seedBuyer(page.request);
    await page.goto("/profile");

    // Wait for shell, then click Addresses tab.
    await expect(
      page.getByRole("heading", { name: /Personal info|Thông tin cá nhân/i }),
    ).toBeVisible({ timeout: 20_000 });

    await page.getByRole("button", { name: /^(Addresses|Địa chỉ)$/i }).click();

    // Empty-state copy renders for a new buyer.
    await expect(
      page.getByText(/You don't have any addresses yet|Bạn chưa có địa chỉ nào/i),
    ).toBeVisible({ timeout: 10_000 });

    // Click "Add address".
    await page.getByRole("button", { name: /Add address|Thêm địa chỉ/i }).click();

    // Form fields appear. They use no test-ids; anchor on the visible labels.
    // Each label is a sibling of an input/select. We'll fill by label text.
    const streetField = page.getByLabel(/Street|Số nhà/i).first();
    await expect(streetField).toBeVisible({ timeout: 10_000 });
    await streetField.fill("42 QA Test Street");

    // District + city are required by the FE validator.
    await page.getByLabel(/District|Quận/i).first().fill("District 1");
    await page.getByLabel(/City|Tỉnh/i).first().fill("Ho Chi Minh");

    // Submit.
    await page.getByRole("button", { name: /Save address|Lưu địa chỉ/i }).click();

    // Success toast appears.
    await expect(
      page.getByText(/Address added|Đã thêm địa chỉ/i),
    ).toBeVisible({ timeout: 15_000 });

    // The new address row is visible by its street.
    await expect(page.getByText("42 QA Test Street").first()).toBeVisible({
      timeout: 10_000,
    });

    await expectNoGlobalError(page);
  });

  test("Submitting the address form with empty street + city blocks", async ({ page }) => {
    await seedBuyer(page.request);
    await page.goto("/profile");

    await expect(
      page.getByRole("heading", { name: /Personal info|Thông tin cá nhân/i }),
    ).toBeVisible({ timeout: 20_000 });

    await page.getByRole("button", { name: /^(Addresses|Địa chỉ)$/i }).click();
    await page.getByRole("button", { name: /Add address|Thêm địa chỉ/i }).click();

    // Click Save without filling — FE shows a validation toast.
    await page.getByRole("button", { name: /Save address|Lưu địa chỉ/i }).click();

    // The validate-missing toast surfaces in either language.
    await expect(
      page.getByText(/Please enter the street|Vui lòng nhập số nhà/i),
    ).toBeVisible({ timeout: 10_000 });

    // No success toast for "Address added" should appear (proves the
    // mutation didn't run). The form is still open with the Save button
    // still visible — that's the canonical "submission was blocked" signal.
    await expect(
      page.getByText(/Address added|Đã thêm địa chỉ/i),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: /Save address|Lưu địa chỉ/i }),
    ).toBeVisible({ timeout: 5_000 });

    await expectNoGlobalError(page);
  });
});
