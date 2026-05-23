import { test, expect, type Page } from "@playwright/test";

/**
 * UI-driven QA spec for the flash-sale strip on the home page.
 *
 * What this proves through the actual SPA:
 *   - The flash sale section header always renders on home (it's
 *     unconditional — the BE returns campaigns or none, but the
 *     section chrome stays mounted)
 *   - The countdown / empty-state body renders past Suspense without
 *     the global error fallback (regression check for the
 *     activeFlashSaleCampaignSchema parsing)
 *   - The Tabler IconBolt svg is present in the section header
 *     (regression check for the lucide → tabler migration)
 *
 * No backend or auth needed. Runs on the public home page.
 */

async function expectNoGlobalError(page: Page): Promise<void> {
  await expect(page.getByText(/Có lỗi xảy ra|Something went wrong/i)).toHaveCount(0);
  await expect(page.getByText(/Invalid input/i)).toHaveCount(0);
}

test.describe("flash sale strip UI", () => {
  test("Flash sale section header renders with the IconBolt", async ({ page }) => {
    await page.goto("/");

    // Wait for the home page to mount (Login CTA is the canonical signal).
    await expect(
      page.getByRole("button", { name: /^(Log in|Đăng nhập)$/i }).first(),
    ).toBeVisible({ timeout: 20_000 });

    // The Flash sale title — anchor on the localized label, not the emoji.
    // (The label includes a ⚡ emoji prefix in the translation; matching
    // 'FLASH SALE' is the stable substring across both languages.)
    await expect(page.getByText(/FLASH SALE/i).first()).toBeVisible({
      timeout: 15_000,
    });

    // Tabler IconBolt is the lightning icon in the section header. The
    // codemod replaced lucide IconZap → IconBolt; if it ever flips back
    // this assertion catches it.
    const bolt = page.locator("svg.tabler-icon-bolt").first();
    await expect(bolt).toBeVisible({ timeout: 10_000 });

    await expectNoGlobalError(page);
  });

  test("Flash sale body renders one of: countdown, empty-state, or product strip", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(
      page.getByRole("button", { name: /^(Log in|Đăng nhập)$/i }).first(),
    ).toBeVisible({ timeout: 20_000 });

    // The body has three render modes:
    //   - hasCampaigns: countdown timer + product strip
    //   - !hasCampaigns + isLoading: "Loading flash sale..." copy
    //   - !hasCampaigns + !isLoading (or expired): "Coming soon" / "expired"
    // Any of these copy variants confirms the schema parsed.
    await expect(
      page
        .getByText(
          /Hot deals|Giảm sốc|Loading flash sale|Đang tải flash sale|Coming soon|Sắp ra mắt|Ends in|Kết thúc sau|Flash sale ended|Hết flash sale/i,
        )
        .first(),
    ).toBeVisible({ timeout: 15_000 });

    await expectNoGlobalError(page);
  });
});
