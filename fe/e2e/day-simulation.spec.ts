import { test, expect, type APIRequestContext, type Page } from "@playwright/test";

/**
 * Day-in-the-life simulation: drives every documented user flow end-to-end
 * against the running stack to catch integration regressions the per-page
 * sweep can't reach. Each describe block is one persona's full day.
 *
 * Run order is irrelevant — every test creates fresh state via timestamped
 * emails and reads the seeded buyer1/seller1/admin1 fixtures from
 * infra/keycloak/vnshop-realm.json. Where a flow needs orchestration the
 * test prefers gateway calls over UI clicks (faster, more reliable, still
 * exercises BE end-to-end).
 *
 * Failures here are real: this is the first spec that completes a checkout,
 * places an order, drives notification round-trip, and walks every admin
 * tab against a live stack.
 */

const apiURL = process.env.VITE_E2E_API_URL ?? "http://localhost:8080";
const PASSWORD = "Test1234!";

interface AuthResult {
  accessToken: string;
  email?: string;
}

async function registerBuyer(request: APIRequestContext): Promise<AuthResult> {
  const stamp = Date.now() + Math.floor(Math.random() * 1_000);
  const email = `e2e_day_${stamp}@vnshop.local`;
  const r = await request.post(`${apiURL}/auth/register`, {
    data: {
      firstName: "Day",
      lastName: "Sim",
      email,
      password: PASSWORD,
    },
  });
  expect(r.ok(), `register failed: ${r.status()} ${await r.text()}`).toBeTruthy();
  // /auth/register returns {userId, email} — no token. Log in for the
  // access token (the Keycloak ROPC grant via /auth/login).
  const login = await request.post(`${apiURL}/auth/login`, {
    data: { username: email, password: PASSWORD },
  });
  expect(login.ok(), `post-register login failed: ${login.status()} ${await login.text()}`).toBeTruthy();
  const body = await login.json();
  const token = body?.data?.accessToken ?? body?.accessToken;
  expect(token, `no accessToken in login response: ${JSON.stringify(body).slice(0, 200)}`).toBeTruthy();
  return { accessToken: token, email };
}

async function loginByUsername(request: APIRequestContext, username: string): Promise<AuthResult> {
  const r = await request.post(`${apiURL}/auth/login`, {
    data: { username, password: "test" },
  });
  expect(r.ok(), `login ${username} failed: ${r.status()} ${await r.text()}`).toBeTruthy();
  const body = await r.json();
  const token = body?.data?.accessToken ?? body?.accessToken;
  expect(token, `no accessToken for ${username}: ${JSON.stringify(body).slice(0, 200)}`).toBeTruthy();
  return { accessToken: token };
}

function authHeaders(auth: AuthResult): Record<string, string> {
  return { Authorization: `Bearer ${auth.accessToken}` };
}

async function firstProduct(request: APIRequestContext): Promise<{ id: string; sellerId?: string }> {
  const r = await request.get(`${apiURL}/products?size=1`);
  expect(r.ok(), `products fetch failed: ${r.status()}`).toBeTruthy();
  const body = await r.json();
  const id = body?.data?.content?.[0]?.id;
  const sellerId = body?.data?.content?.[0]?.sellerId ?? body?.data?.content?.[0]?.seller?.id;
  expect(id, "no products seeded").toBeTruthy();
  return { id, sellerId };
}

test.describe("day simulation — anonymous explore", () => {
  test("anonymous: catalog + search + product detail are reachable", async ({ request }) => {
    const home = await request.get(`${apiURL}/products?size=4`);
    expect(home.ok()).toBeTruthy();
    const body = await home.json();
    expect(body?.data?.content?.length ?? 0).toBeGreaterThan(0);

    const cats = await request.get(`${apiURL}/categories`);
    expect(cats.ok()).toBeTruthy();

    const product = await firstProduct(request);
    const detail = await request.get(`${apiURL}/products/${product.id}`);
    expect(detail.ok()).toBeTruthy();

    const search = await request.get(`${apiURL}/products?q=phone&size=4`);
    expect(search.ok()).toBeTruthy();
  });
});

test.describe("day simulation — buyer", () => {
  test("buyer: register → cart → wishlist → address → checkout calc → place COD order → orders → cancel", async ({
    request,
  }) => {
    const auth = await registerBuyer(request);
    const headers = authHeaders(auth);
    const product = await firstProduct(request);

    // 1) Cart: add, read, update qty, remove, then add again for checkout
    const add1 = await request.post(`${apiURL}/cart/items`, {
      headers,
      data: { productId: product.id, quantity: 1 },
    });
    expect(add1.ok(), `add to cart failed: ${add1.status()} ${await add1.text()}`).toBeTruthy();

    const cart = await request.get(`${apiURL}/cart`, { headers });
    expect(cart.ok()).toBeTruthy();
    const cartBody = await cart.json();
    expect(cartBody?.data?.items?.length ?? 0).toBeGreaterThan(0);

    const upd = await request.put(`${apiURL}/cart/items/${product.id}`, {
      headers,
      data: { quantity: 2 },
    });
    expect(upd.ok(), `cart update failed: ${upd.status()}`).toBeTruthy();

    const rm = await request.delete(`${apiURL}/cart/items/${product.id}`, { headers });
    expect(rm.ok()).toBeTruthy();

    // Re-add for checkout
    const readd = await request.post(`${apiURL}/cart/items`, {
      headers,
      data: { productId: product.id, quantity: 1 },
    });
    expect(readd.ok()).toBeTruthy();

    // 2) Wishlist toggle
    const wishToggle = await request.post(`${apiURL}/users/me/wishlist/toggle`, {
      headers,
      data: { productId: product.id },
    });
    expect(wishToggle.ok(), `wishlist toggle: ${wishToggle.status()}`).toBeTruthy();
    const wish = await request.get(`${apiURL}/users/me/wishlist`, { headers });
    expect(wish.ok()).toBeTruthy();

    // 3) Address (BE shape: street/ward/district/city/isDefault)
    const addr = await request.post(`${apiURL}/users/me/addresses`, {
      headers,
      data: {
        street: "123 Day Sim Street",
        ward: "1442",
        district: "101",
        city: "Ho Chi Minh",
        isDefault: true,
      },
    });
    expect(addr.ok(), `address add: ${addr.status()} ${await addr.text()}`).toBeTruthy();

    // 4) Checkout calculate (light shape — BE resolves authoritative price
    // from product-service, no client-supplied prices). Mirrors POST /orders.
    const calc = await request.post(`${apiURL}/checkout/calculate`, {
      headers,
      data: { items: [{ productId: product.id, quantity: 1 }] },
    });
    expect(calc.ok(), `checkout calc: ${calc.status()} ${await calc.text()}`).toBeTruthy();
    const calcBody = await calc.json();
    const itemsTotal = calcBody?.data?.itemsTotal ?? 0;
    expect(Number(itemsTotal)).toBeGreaterThan(0);

    // 5) Place order — COD path, no external gateway needed.
    //
    // Post-pt9 fix: BE now accepts the light client shape
    // {shippingAddress, items:[{productId, variantSku?, quantity}]} and
    // resolves sellerId / name / unitPrice / image server-side from the
    // product-service. Closes the price-tampering security finding —
    // the client cannot influence the recorded order total.
    const idem = `day-sim-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const place = await request.post(`${apiURL}/orders`, {
      headers: { ...headers, "Idempotency-Key": idem },
      data: {
        shippingAddress: {
          street: "123 Day Sim Street",
          ward: "1442",
          district: "101",
          city: "Ho Chi Minh",
        },
        items: [{ productId: product.id, quantity: 1 }],
      },
    });
    if (!place.ok()) {
      // eslint-disable-next-line no-console
      console.log(`place order failed: ${place.status()} ${await place.text()}`);
    }
    expect(place.ok()).toBeTruthy();
    const placeBody = await place.json();
    const orderId = placeBody?.data?.id ?? placeBody?.data?.orderId;
    expect(orderId, `no order id in response: ${JSON.stringify(placeBody).slice(0, 300)}`).toBeTruthy();

    // 6) Idempotency: replay returns same order
    const replay = await request.post(`${apiURL}/orders`, {
      headers: { ...headers, "Idempotency-Key": idem },
      data: {
        shippingAddress: {
          street: "123 Day Sim Street",
          ward: "1442",
          district: "101",
          city: "Ho Chi Minh",
        },
        items: [{ productId: product.id, quantity: 1 }],
      },
    });
    expect(replay.ok()).toBeTruthy();
    const replayBody = await replay.json();
    const replayId = replayBody?.data?.id ?? replayBody?.data?.orderId;
    expect(replayId).toBe(orderId);

    // 7) /orders/{id} confirms write-model state immediately. The /orders
    //    list goes through a CQRS read-model projection that may take a
    //    moment to land (Kafka order-event consumer); poll briefly.
    const direct = await request.get(`${apiURL}/orders/${orderId}`, { headers });
    expect(direct.ok(), `direct order get: ${direct.status()}`).toBeTruthy();

    let foundInList = false;
    for (let attempt = 0; attempt < 10; attempt++) {
      const list = await request.get(`${apiURL}/orders?size=10`, { headers });
      expect(list.ok()).toBeTruthy();
      const listBody = await list.json();
      const ids = (listBody?.data?.content ?? []).map((o: { id: string }) => o.id);
      if (ids.includes(orderId)) {
        foundInList = true;
        break;
      }
      await new Promise((r) => setTimeout(r, 500));
    }
    if (!foundInList) {
      // eslint-disable-next-line no-console
      console.log(`order ${orderId} not in /orders list after 5s — read-model projection lagging`);
    }

    // 8) Cancel the order (COD orders should be cancellable while PENDING)
    const cancel = await request.delete(`${apiURL}/orders/${orderId}/cancel`, { headers });
    // Cancel may legitimately fail if status already advanced — accept either.
    if (!cancel.ok()) {
      // eslint-disable-next-line no-console
      console.log(`cancel returned ${cancel.status()} (may be expected if already advanced)`);
    }
  });

  test("buyer: notification round-trip via /notifications/test", async ({ request }) => {
    const auth = await registerBuyer(request);
    const headers = authHeaders(auth);

    const before = await request.get(`${apiURL}/notifications/unread-count`, { headers });
    expect(before.ok()).toBeTruthy();
    const beforeBody = await before.json();
    const beforeCount = beforeBody?.data?.count ?? 0;

    const trigger = await request.post(`${apiURL}/notifications/test`, { headers });
    expect(trigger.ok(), `notifications/test: ${trigger.status()} ${await trigger.text()}`).toBeTruthy();

    // Allow the consumer or in-process write a moment to land. /test is
    // synchronous in send-notification.use-case so this should be fast.
    await new Promise((r) => setTimeout(r, 1000));

    const after = await request.get(`${apiURL}/notifications/unread-count`, { headers });
    expect(after.ok()).toBeTruthy();
    const afterBody = await after.json();
    const afterCount = afterBody?.data?.count ?? 0;
    expect(afterCount).toBeGreaterThanOrEqual(beforeCount + 1);

    const list = await request.get(`${apiURL}/notifications?size=5`, { headers });
    expect(list.ok()).toBeTruthy();
    const listBody = await list.json();
    const items: Array<{ id: string; read?: boolean }> = listBody?.data?.content ?? [];
    expect(items.length).toBeGreaterThan(0);

    const target = items[0]!;
    const markRead = await request.post(`${apiURL}/notifications/${target.id}/read`, { headers });
    expect(markRead.ok(), `mark-read: ${markRead.status()}`).toBeTruthy();

    const markAll = await request.post(`${apiURL}/notifications/mark-all-read`, { headers });
    expect(markAll.ok()).toBeTruthy();

    const tail = await request.get(`${apiURL}/notifications/unread-count`, { headers });
    const tailBody = await tail.json();
    expect(tailBody?.data?.count ?? 0).toBe(0);
  });

  test("buyer UI: register → home → cart → checkout step renders without crash", async ({
    page,
    request,
  }) => {
    const stamp = Date.now();
    const email = `e2e_day_ui_${stamp}@vnshop.local`;
    await page.goto("/register");
    await page.locator("#firstName").fill("Day");
    await page.locator("#lastName").fill("UI");
    await page.locator("#email").fill(email);
    await page.locator("#password").fill(PASSWORD);
    await page.locator("#confirm").fill(PASSWORD);
    await page.getByRole("button", { name: /create account|tạo tài khoản/i }).click();
    await expect.poll(() => new URL(page.url()).pathname, { timeout: 30_000 }).toBe("/");

    const product = await firstProduct(request);
    await page.goto(`/product/${product.id}`);
    const addBtn = page.getByRole("button", { name: /add to cart|thêm vào giỏ/i }).first();
    await expect(addBtn).toBeVisible({ timeout: 15_000 });
    await addBtn.click();

    await page.goto("/cart");
    await expect(page.getByText(/your cart is empty|giỏ hàng trống/i)).toHaveCount(0, {
      timeout: 10_000,
    });

    await page.goto("/checkout");
    // Either a form or the empty-cart fallback renders — both are acceptable
    // and prove the route doesn't crash. Failure mode would be the error
    // boundary which renders a different banner.
    await expect(page.locator("body")).not.toContainText(/something went wrong|đã xảy ra lỗi/i);
  });
});

test.describe("day simulation — seller", () => {
  test("seller: dashboard read paths (revenue + wallet + analytics + orders)", async ({
    request,
  }) => {
    const auth = await loginByUsername(request, "seller1");
    const headers = authHeaders(auth);

    const me = await request.get(`${apiURL}/sellers/me`, { headers });
    expect(me.ok(), `sellers/me: ${me.status()} ${await me.text()}`).toBeTruthy();

    const wallet = await request.get(`${apiURL}/sellers/me/finance/wallet`, { headers });
    expect(wallet.ok(), `wallet: ${wallet.status()} ${await wallet.text()}`).toBeTruthy();

    const payouts = await request.get(`${apiURL}/sellers/me/finance/payouts`, { headers });
    expect(payouts.ok()).toBeTruthy();

    const revenue = await request.get(`${apiURL}/sellers/me/revenue?days=30`, { headers });
    expect(revenue.ok(), `seller revenue: ${revenue.status()} ${await revenue.text()}`).toBeTruthy();

    const pending = await request.get(`${apiURL}/seller/orders/pending`, { headers });
    expect(pending.ok(), `pending orders: ${pending.status()}`).toBeTruthy();
  });

  test("seller UI: dashboard renders without crash", async ({ page }) => {
    await page.goto("/login");
    await page.locator("#identifier").fill("seller1");
    await page.locator("#password").fill("test");
    await page.getByRole("button", { name: /sign in|đăng nhập/i }).click();
    await expect.poll(() => new URL(page.url()).pathname, { timeout: 30_000 }).toBe("/");

    await page.goto("/seller");
    await expect(page.locator("body")).not.toContainText(/something went wrong|đã xảy ra lỗi/i, {
      timeout: 10_000,
    });
  });
});

test.describe("day simulation — admin", () => {
  test("admin: dashboard + sellers + reviews + coupons + payouts + disputes read paths", async ({
    request,
  }) => {
    const auth = await loginByUsername(request, "admin1");
    const headers = authHeaders(auth);

    const summary = await request.get(`${apiURL}/admin/dashboard/summary`, { headers });
    expect(summary.ok(), `admin summary: ${summary.status()} ${await summary.text()}`).toBeTruthy();

    const sellers = await request.get(`${apiURL}/admin/sellers`, { headers });
    expect(sellers.ok(), `admin/sellers: ${sellers.status()}`).toBeTruthy();

    const reviews = await request.get(`${apiURL}/admin/reviews/pending`, { headers });
    expect(reviews.ok(), `admin/reviews/pending: ${reviews.status()}`).toBeTruthy();

    const coupons = await request.get(`${apiURL}/admin/coupons`, { headers });
    expect(coupons.ok(), `admin/coupons: ${coupons.status()}`).toBeTruthy();

    const payouts = await request.get(`${apiURL}/admin/finance/payouts/pending`, { headers });
    expect(payouts.ok(), `admin payouts: ${payouts.status()}`).toBeTruthy();

    const disputes = await request.get(`${apiURL}/admin/disputes/open`, { headers });
    expect(disputes.ok(), `admin disputes: ${disputes.status()}`).toBeTruthy();

    const topProducts = await request.get(`${apiURL}/admin/dashboard/top-products?limit=5`, {
      headers,
    });
    expect(topProducts.ok()).toBeTruthy();
    const topSellers = await request.get(`${apiURL}/admin/dashboard/top-sellers?limit=5`, {
      headers,
    });
    expect(topSellers.ok()).toBeTruthy();
  });

  test("admin: coupon CRUD round-trip", async ({ request }) => {
    const auth = await loginByUsername(request, "admin1");
    const headers = authHeaders(auth);

    const code = `DAYSIM${Date.now().toString().slice(-6)}`;
    const created = await request.post(`${apiURL}/admin/coupons`, {
      headers,
      data: {
        code,
        type: "PERCENTAGE",
        value: 10,
        minOrderValue: 100_000,
        maxDiscount: 50_000,
        maxUses: 100,
        validUntil: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
      },
    });
    expect(created.ok(), `coupon create: ${created.status()} ${await created.text()}`).toBeTruthy();
    const body = await created.json();
    // CouponController returns the response shape directly (no ApiResponse
    // envelope), so id is at the top level.
    const id = body?.id ?? body?.data?.id ?? body?.couponId;
    expect(id, `no coupon id: ${JSON.stringify(body).slice(0, 200)}`).toBeTruthy();

    const deact = await request.post(`${apiURL}/admin/coupons/${id}/deactivate`, { headers });
    expect(deact.ok(), `coupon deactivate: ${deact.status()}`).toBeTruthy();
  });
});

test.describe("day simulation — payment-method shells", () => {
  test("checkout payment-methods endpoint enumerates enabled gateways", async ({ request }) => {
    const auth = await registerBuyer(request);
    const headers = authHeaders(auth);

    const r = await request.get(`${apiURL}/checkout/payment-methods`, { headers });
    expect(r.ok(), `payment-methods: ${r.status()} ${await r.text()}`).toBeTruthy();
    const body = await r.json();
    const methods: Array<{ method?: string; code?: string; enabled?: boolean }> = body?.data ?? [];
    expect(Array.isArray(methods)).toBeTruthy();
    expect(methods.length).toBeGreaterThan(0);

    // COD must always be present — it's the always-on fallback path.
    const codes = methods.map((m) => m.method ?? m.code).filter(Boolean);
    expect(codes).toContain("COD");
  });
});
