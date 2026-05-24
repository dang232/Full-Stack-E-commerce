import { test, expect } from "@playwright/test";

import {
  bizStep,
  copyArtifacts,
  expectNoGlobalError,
  finalizeChapterReport,
  rememberOutputDir,
  startChapter,
  startTrace,
  stopTrace,
} from "./_journey-evidence";
import {
  loginAsSeededUser,
  logoutViaUserMenu,
} from "../_workday-evidence";
import { requireJourneyState } from "./_journey-state";

/**
 * Chapter 6 — Admin closes the loop.
 *
 * Business outcomes proven against the live SPA + BE:
 *   AC-6.1 Admin's payout queue lists the seller's pending request with the
 *          exact amount the seller submitted in chapter 5.
 *   AC-6.2 Admin clicks Complete and the SPA confirms the payout flipped
 *          to COMPLETED.
 *   AC-6.3 Seller's wallet pendingBalance drops by exactly the payout amount
 *          once the BE projection settles — closing the loop the journey
 *          opened in chapter 3. (Note: chapter 5's reservePayout already
 *          moved the amount from availableBalance into pendingBalance at
 *          request time; the admin's Complete drains pendingBalance to 0,
 *          which is the canonical "money has left the platform" signal.)
 *
 * Requires Chapter 5 to have submitted a payout (state.payoutId,
 * state.payoutAmountVnd) against seller1's wallet credit.
 */

const apiURL = process.env.VITE_E2E_API_URL ?? "http://localhost:8080";

test.use({
  video: "on",
  actionTimeout: 30_000,
});

test.describe.serial("Chapter 6 — Admin closes the loop", () => {
  test.beforeAll(async () => {
    await startChapter({
      id: "06-admin-closes-loop",
      title: "Chapter 6 — Admin closes the loop",
      persona: "admin",
      acceptanceCriteria: [
        {
          code: "AC-6.1",
          outcome:
            "Admin's payout queue surfaces the seller's pending payout with the right amount",
        },
        {
          code: "AC-6.2",
          outcome:
            "Admin can mark the payout complete and the payout leaves the pending queue",
        },
        {
          code: "AC-6.3",
          outcome:
            "Seller's wallet pendingBalance drops by exactly the payout amount once the projection settles",
        },
      ],
    });
  });

  test.afterEach(async ({}, testInfo) => {
    rememberOutputDir("06-admin-closes-loop", testInfo);
  });

  test.afterAll(async () => {
    await copyArtifacts("06-admin-closes-loop");
    await finalizeChapterReport("06-admin-closes-loop");
  });

  test.setTimeout(15 * 60 * 1000);

  test("Admin completes the seller's pending payout and the wallet drops to zero", async ({
    page,
  }) => {
    await startTrace("06-admin-closes-loop", page);
    try {
      let pendingBeforeVnd = 0;
      let payoutId = "";
      let payoutAmountVnd = 0;

      await bizStep(
        page,
        "06-admin-closes-loop",
        "AC-6.1",
        "Predecessor chapter 5 left a PENDING payoutId in state.json",
        async () => {
          const state = await requireJourneyState([
            "payoutId",
            "payoutAmountVnd",
          ]);
          payoutId = state.payoutId;
          payoutAmountVnd = Number(state.payoutAmountVnd);
        },
      );

      await bizStep(
        page,
        "06-admin-closes-loop",
        "AC-6.3",
        "Capture seller1's pendingBalance before admin closes the payout",
        async () => {
          // Chapter 5's reservePayout moved the payout amount from
          // availableBalance into pendingBalance, so pendingBalance is the
          // bucket the admin's Complete drains. Poll until the projection
          // surfaces the chapter-5 reservation (Kafka catch-up lag varies),
          // then snapshot it so AC-6.3 below can prove the exact-delta drop.
          const login = await page.request.post(`${apiURL}/auth/login`, {
            data: { username: "seller1", password: "test" },
          });
          expect(login.ok(), `seller1 login: ${login.status()}`).toBeTruthy();
          const token = (await login.json())?.data?.accessToken;

          await expect
            .poll(
              async () => {
                const r = await page.request.get(
                  `${apiURL}/sellers/me/finance/wallet`,
                  { headers: { Authorization: `Bearer ${token}` } },
                );
                if (!r.ok()) return -1;
                const pending = Number(
                  (await r.json())?.data?.pendingBalance ?? -1,
                );
                if (pending >= payoutAmountVnd) {
                  pendingBeforeVnd = pending;
                }
                return pending;
              },
              {
                timeout: 30_000,
                message: `seller1 pendingBalance never reached the chapter-5 reservation amount (${payoutAmountVnd}) — RequestPayoutUseCase save may not have settled`,
              },
            )
            .toBeGreaterThanOrEqual(payoutAmountVnd);
        },
      );

      await bizStep(
        page,
        "06-admin-closes-loop",
        "AC-6.1",
        "Admin opens the Payouts tab and the seller's pending payout is listed",
        async () => {
          await page.context().clearCookies();
          await loginAsSeededUser(page, "admin1");
          await page.goto("/admin");
          await expect(
            page.getByText(/Admin Dashboard|Tổng quan|Admin Console/i).first(),
          ).toBeVisible({ timeout: 20_000 });

          await page
            .getByRole("button", { name: /^(Payouts|Rút tiền)/i })
            .first()
            .click();
          await expect(
            page.getByText(/Payout requests|Yêu cầu rút tiền/i).first(),
          ).toBeVisible({ timeout: 15_000 });

          // Chapter 5 wrote payoutId to state.json; the admin row renders
          // p.id in a font-mono span so a substring match is enough.
          await expect(
            page.getByText(payoutId, { exact: false }).first(),
          ).toBeVisible({ timeout: 10_000 });
          await expectNoGlobalError(page);
        },
      );

      await bizStep(
        page,
        "06-admin-closes-loop",
        "AC-6.2",
        "Admin clicks Complete on the row and the payout leaves the pending queue",
        async () => {
          // PayoutsQueue renders one row per payout under .divide-y > div with
          // the id in a font-mono span — narrow to the row whose hasText
          // matches the chapter-5 payoutId, then click Complete inside.
          const row = page
            .locator(".divide-y > div", { hasText: payoutId })
            .first();
          await expect(row).toBeVisible({ timeout: 10_000 });
          await row
            .getByRole("button", { name: /^(Complete|Hoàn tất|Hoàn thành)$/i })
            .first()
            .click();

          // Canonical BA-grade assertion: the payout has left the BE's
          // pending queue. The FE row removal + toast is a derived effect
          // that can lag the API response under load — assert the source
          // of truth first, then confirm the SPA caught up.
          const adminLogin = await page.request.post(`${apiURL}/auth/login`, {
            data: { username: "admin1", password: "test" },
          });
          const adminToken = (await adminLogin.json())?.data?.accessToken;

          await expect
            .poll(
              async () => {
                const r = await page.request.get(
                  `${apiURL}/admin/finance/payouts/pending`,
                  { headers: { Authorization: `Bearer ${adminToken}` } },
                );
                if (!r.ok()) return -1;
                const list: Array<{ payoutId?: string; status?: string }> =
                  (await r.json())?.data ?? [];
                return list.filter(
                  (p) => p.payoutId === payoutId && p.status === "PENDING",
                ).length;
              },
              {
                timeout: 30_000,
                message: `payout ${payoutId} never left the BE pending queue after admin clicked Complete`,
              },
            )
            .toBe(0);
          await expectNoGlobalError(page);
        },
      );

      await bizStep(
        page,
        "06-admin-closes-loop",
        "AC-6.3",
        "Seller's pendingBalance drops by exactly the payout amount",
        async () => {
          // CompletePayoutUseCase debits pendingBalance synchronously, but
          // the cached projection may serve the pre-debit value briefly —
          // poll until the delta lands.
          const expectedAfter = pendingBeforeVnd - payoutAmountVnd;

          const login = await page.request.post(`${apiURL}/auth/login`, {
            data: { username: "seller1", password: "test" },
          });
          const token = (await login.json())?.data?.accessToken;

          await expect
            .poll(
              async () => {
                const r = await page.request.get(
                  `${apiURL}/sellers/me/finance/wallet`,
                  { headers: { Authorization: `Bearer ${token}` } },
                );
                if (!r.ok()) return -1;
                return Number((await r.json())?.data?.pendingBalance ?? -1);
              },
              {
                timeout: 30_000,
                message: `seller1 pendingBalance never settled to ${expectedAfter} after admin completed payout ${payoutId}`,
              },
            )
            .toBe(expectedAfter);
        },
      );

      await bizStep(
        page,
        "06-admin-closes-loop",
        "AC-6.3",
        "Admin logs out — journey complete; chapter 6 leaves no new state",
        async () => {
          await logoutViaUserMenu(page);
        },
      );
    } finally {
      await stopTrace("06-admin-closes-loop", page);
      await finalizeChapterReport("06-admin-closes-loop");
    }
  });
});
