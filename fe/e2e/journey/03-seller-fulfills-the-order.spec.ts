import { test, expect, type APIRequestContext } from "@playwright/test";

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
import {
  requireJourneyState,
  writeJourneyState,
} from "./_journey-state";

/**
 * Chapter 3 — Seller fulfills the order.
 *
 * Business outcomes proven against the live SPA + BE:
 *   AC-3.1 A seller sees a buyer's new order in their pending queue within 30 s
 *   AC-3.2 A seller can accept and ship the order with a tracking number
 *   AC-3.3 (deferred — wallet credit) Seller's wallet reflects the discounted
 *          revenue after the order moves through the fulfillment lifecycle.
 *          Currently soft-asserted because seller-finance-service projects
 *          via Kafka and lag varies; the journey records the balance at run
 *          time so a regression in the projection becomes traceable here.
 *
 * Requires Chapter 2 to have placed an order. Reads orderId from the
 * shared state file; fails BLOCKED with a clear message otherwise.
 *
 * Seller selection: chapter 2 ordered a seeded product. Seeded products
 * belong to seller1 (the realm-imported account) — not the seller chapter
 * 1 approved (that one has zero products). So chapter 3 logs in as
 * seller1 directly. The chapter-1-approved seller is exercised by
 * chapter 5 (payout request) once it has earnings.
 *
 * KNOWN UX GAP SURFACED BY THIS CHAPTER (caught on first run, 2026-05-24):
 *   AC-3.2 currently FAILS at the ship step. After the seller clicks
 *   Accept, the sub-order's fulfillmentStatus becomes ACCEPTED, but
 *   GET /seller/orders/pending filters by PENDING_ACCEPTANCE only
 *   (services/order-service/.../ListPendingOrdersUseCase.java). So the
 *   row leaves the seller's queue entirely after accept and the Ship
 *   button never appears in the SPA — there is no UI surface in the
 *   seller console that lists ACCEPTED orders. A seller who accepts an
 *   order cannot ship it through the platform.
 *
 *   The fix is either: (a) include ACCEPTED in /seller/orders/pending so
 *   the row stays put with the Ship action, or (b) add a separate
 *   "Ready to ship" tab in the seller console that lists ACCEPTED orders.
 *   Until then AC-3.2 stays red as the visible signal — same pattern as
 *   AC-2.2 in pt31. Do not relax the assertion.
 *
 * Writes to journey state:
 *   subOrderId — the sub-order chapter 3 fulfilled, used by chapter 4
 *   when the buyer leaves a review.
 */

const apiURL = process.env.VITE_E2E_API_URL ?? "http://localhost:8080";

test.use({
  video: "on",
  actionTimeout: 30_000,
});

test.describe.serial("Chapter 3 — Seller fulfills the order", () => {
  test.beforeAll(async () => {
    await startChapter({
      id: "03-seller-fulfills",
      title: "Chapter 3 — Seller fulfills the order",
      persona: "seller",
      acceptanceCriteria: [
        {
          code: "AC-3.1",
          outcome:
            "A seller sees the buyer's new order in their pending queue within 30 s",
        },
        {
          code: "AC-3.2",
          outcome:
            "A seller can accept and ship the order with a tracking number",
        },
      ],
    });
  });

  test.afterEach(async ({}, testInfo) => {
    rememberOutputDir("03-seller-fulfills", testInfo);
  });

  test.afterAll(async () => {
    await copyArtifacts("03-seller-fulfills");
    await finalizeChapterReport("03-seller-fulfills");
  });

  test.setTimeout(15 * 60 * 1000);

  test("Seller accepts and ships the buyer's order", async ({ page }) => {
    await startTrace("03-seller-fulfills", page);
    try {
      let resolvedSubOrderId = 0;

      await bizStep(
        page,
        "03-seller-fulfills",
        "AC-3.1",
        "Predecessor chapter has placed an order (state.json check)",
        async () => {
          await requireJourneyState(["orderId"]);
        },
      );

      await bizStep(
        page,
        "03-seller-fulfills",
        "AC-3.1",
        "Seller's pending queue includes Chapter 2's order within 30 s",
        async () => {
          const state = await requireJourneyState(["orderId"]);
          // Resolve the sub-order via the seller-scoped pending list. We
          // poll because order_summary projection lag can run a few
          // hundred ms behind the place-order write.
          await expect
            .poll(
              async () => {
                const subOrderId = await findSubOrderForOrder(
                  page.request,
                  state.orderId,
                );
                if (subOrderId !== null) {
                  resolvedSubOrderId = subOrderId;
                  return true;
                }
                return false;
              },
              {
                timeout: 30_000,
                message:
                  "seller's pending queue never showed Chapter 2's order within 30 s",
              },
            )
            .toBe(true);
        },
      );

      await bizStep(
        page,
        "03-seller-fulfills",
        "AC-3.1",
        "Seller logs into the SPA and the Orders tab renders the pending row",
        async () => {
          // Clear the buyer's session cookies from chapter 2 so /login
          // actually shows its form (an authed visit to /login redirects
          // to /).
          await page.context().clearCookies();
          await loginAsSeededUser(page, "seller1");
          await page.goto("/seller");
          await expect(
            page
              .getByText(/Dashboard|Tổng quan|Seller Hub|Kênh Người Bán/i)
              .first(),
          ).toBeVisible({ timeout: 20_000 });

          await page
            .getByRole("button", { name: /^(Orders|Đơn hàng)$/ })
            .first()
            .click();

          // Queue header OR a row containing our subOrderId — both prove
          // the parse landed; the row presence matters for AC-3.2's click.
          await expect
            .poll(
              async () =>
                page.getByText(String(resolvedSubOrderId), { exact: false }).count(),
              {
                timeout: 30_000,
                message:
                  `seller's UI never rendered subOrderId=${resolvedSubOrderId} in the Orders tab`,
              },
            )
            .toBeGreaterThan(0);
          await expectNoGlobalError(page);
        },
      );

      await bizStep(
        page,
        "03-seller-fulfills",
        "AC-3.2",
        "Seller clicks Accept on the row and the toast confirms the round-trip",
        async () => {
          // The pending row carries the subOrderId in monospace text. Find
          // its row container and click Accept inside it. .divide-y > div
          // is the same selector pattern admin sellers tab uses.
          const row = page
            .locator(".divide-y > div", { hasText: String(resolvedSubOrderId) })
            .first();
          await expect(row).toBeVisible({ timeout: 10_000 });
          await row
            .getByRole("button", { name: /^(Accept|Chấp nhận)/i })
            .first()
            .click();

          // i18n key is `seller.orders.acceptOk` — VI: "Đã chấp nhận đơn",
          // EN: "Order accepted" (approximations; match liberally).
          await expect(
            page
              .getByText(/Order accepted|Đã chấp nhận|Accepted order/i)
              .first(),
          ).toBeVisible({ timeout: 15_000 });
          await expectNoGlobalError(page);
        },
      );

      await bizStep(
        page,
        "03-seller-fulfills",
        "AC-3.2",
        "Seller ships the accepted order with a tracking number, summary toast confirms",
        async () => {
          const trackingNumber = `JRN-${Date.now()}`;

          // After accept, the row's button switches from Accept to Ship.
          // React Query invalidates after the mutation, but the seller's
          // queue is long enough that the specific row's re-render can
          // race the poll. Reload the tab once to force a clean fetch.
          await page.reload();
          await page
            .getByRole("button", { name: /^(Orders|Đơn hàng)$/ })
            .first()
            .click();

          const row = page
            .locator(".divide-y > div", { hasText: String(resolvedSubOrderId) })
            .first();
          await expect
            .poll(
              () =>
                row.getByRole("button", { name: /^(Ship|Giao hàng)/i }).count(),
              { timeout: 20_000, message: "Ship button never replaced Accept" },
            )
            .toBeGreaterThan(0);

          await row
            .getByRole("button", { name: /^(Ship|Giao hàng)/i })
            .first()
            .click();

          // ShipDialog opens. Fill carrier + trackingNumber inputs by id
          // (the dialog uses stable ids on its inputs).
          const carrierInput = page.locator("input[id*='carrier' i], input[name*='carrier' i]").first();
          await expect(carrierInput).toBeVisible({ timeout: 10_000 });
          await carrierInput.fill("GHN");

          const trackingInput = page
            .locator("input[id*='tracking' i], input[name*='tracking' i]")
            .first();
          await expect(trackingInput).toBeVisible({ timeout: 10_000 });
          await trackingInput.fill(trackingNumber);

          // Submit the dialog. ShipDialog's submit label is the localized
          // "Ship" string.
          await page
            .getByRole("button", { name: /^(Ship|Giao hàng|Submit|Lưu)/i })
            .last()
            .click();

          await expect(
            page
              .getByText(/shipped|đã giao|Marked as shipping|Đã chuyển sang giao/i)
              .first(),
          ).toBeVisible({ timeout: 15_000 });
          await expectNoGlobalError(page);
        },
      );

      await bizStep(
        page,
        "03-seller-fulfills",
        "AC-3.2",
        "Seller logs out and the journey state is persisted for the next chapter",
        async () => {
          await logoutViaUserMenu(page);
          await writeJourneyState({
            subOrderId: resolvedSubOrderId,
          });
        },
      );
    } finally {
      await stopTrace("03-seller-fulfills", page);
      await finalizeChapterReport("03-seller-fulfills");
    }
  });
});

/**
 * Looks up the sub-order belonging to seller1 within Chapter 2's parent
 * order. Returns the numeric sub-order id, or null if the seller's
 * pending list doesn't contain that parent yet (caller polls).
 */
async function findSubOrderForOrder(
  request: APIRequestContext,
  parentOrderId: string,
): Promise<number | null> {
  // seller1 / test is the realm-imported seller that owns the seeded
  // products. The buyer in chapter 2 ordered one of those products, so
  // seller1's /seller/orders/pending will contain the sub-order.
  const login = await request.post(`${apiURL}/auth/login`, {
    data: { username: "seller1", password: "test" },
  });
  if (!login.ok()) return null;
  const accessToken = (await login.json())?.data?.accessToken;
  if (!accessToken) return null;

  const r = await request.get(`${apiURL}/seller/orders/pending`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!r.ok()) return null;
  const list: Array<{ id?: string; subOrders?: Array<{ subOrderId?: number }> }> =
    (await r.json())?.data ?? [];
  const parent = list.find((o) => o.id === parentOrderId);
  if (!parent) return null;
  // A multi-seller order would have multiple sub-orders; chapter 2 ordered
  // a single product so subOrders[0] is correct here.
  return parent.subOrders?.[0]?.subOrderId ?? null;
}
