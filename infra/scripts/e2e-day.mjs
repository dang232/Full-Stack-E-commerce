#!/usr/bin/env node
/**
 * Day-in-the-life E2E suite. Runs through the major buyer / seller / admin
 * flows against a running stack via the API gateway, prints PASS/FAIL/SKIP
 * per step, and exits non-zero if any blocker fails.
 *
 * Usage:
 *   node infra/scripts/e2e-day.mjs                    # full run
 *   ONLY=auth,catalog node infra/scripts/e2e-day.mjs  # run only listed sections
 *
 * Auth:
 *   Buyer is registered fresh each run (random email so reruns are idempotent).
 *   Seller is the seeded `seller1`/`test`. Admin is `admin1`/`test`.
 *   The `vnshop-api` Keycloak client supplies password-grant tokens.
 */

const GATEWAY = process.env.GATEWAY ?? "http://localhost:8080";
const KEYCLOAK = process.env.KEYCLOAK ?? "http://localhost:8085";
const REALM = process.env.REALM ?? "vnshop";
const CLIENT_ID = process.env.CLIENT_ID ?? "vnshop-api";

const ONLY = process.env.ONLY ? new Set(process.env.ONLY.split(",")) : null;

const results = [];
let lastSection = "";

function step(section, name) {
  return { section, name };
}

async function record(section, name, fn) {
  if (ONLY && !ONLY.has(section)) return null;
  if (section !== lastSection) {
    console.log(`\n=== ${section} ===`);
    lastSection = section;
  }
  const start = Date.now();
  try {
    const value = await fn();
    const ms = Date.now() - start;
    console.log(`  PASS  ${name}  (${ms}ms)`);
    results.push({ section, name, status: "PASS", ms });
    return value;
  } catch (err) {
    const ms = Date.now() - start;
    const detail = err?.detail ?? err?.message ?? String(err);
    console.log(`  FAIL  ${name}  (${ms}ms)`);
    console.log(`        ${detail}`);
    results.push({ section, name, status: "FAIL", ms, detail });
    return null;
  }
}

class HttpError extends Error {
  constructor(status, body, expected) {
    super(`expected ${expected}, got ${status}: ${truncate(body, 240)}`);
    this.status = status;
    this.body = body;
    this.detail = `HTTP ${status} (expected ${expected}): ${truncate(body, 240)}`;
  }
}

function truncate(s, n) {
  if (typeof s !== "string") s = JSON.stringify(s);
  if (s == null) return "";
  if (s.length <= n) return s;
  return s.slice(0, n) + "...";
}

async function http(method, path, { token, body, headers = {}, expect = 200 } = {}) {
  const url = path.startsWith("http") ? path : `${GATEWAY}${path}`;
  const init = {
    method,
    headers: {
      Accept: "application/json",
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  };
  const res = await fetch(url, init);
  const text = await res.text();
  let parsed = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      // leave parsed null; treat as raw
    }
  }
  const expects = Array.isArray(expect) ? expect : [expect];
  if (!expects.includes(res.status)) {
    throw new HttpError(res.status, text, expects.join("/"));
  }
  return { status: res.status, body: parsed, raw: text };
}

async function passwordToken(username, password) {
  const res = await fetch(`${KEYCLOAK}/realms/${REALM}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "password",
      client_id: CLIENT_ID,
      username,
      password,
    }),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`token grant failed for ${username}: ${res.status} ${truncate(text, 200)}`);
  }
  const json = await res.json();
  return json.access_token;
}

function unwrap(envelope) {
  if (envelope?.data !== undefined) return envelope.data;
  return envelope;
}

const ctx = {};

async function main() {
  // 1. Auth: register a fresh buyer + login as buyer / seller / admin.
  await record("auth", "register fresh buyer via /auth/register", async () => {
    const stamp = Date.now();
    ctx.buyerEmail = `e2e_buyer_${stamp}@vnshop.local`;
    ctx.buyerPassword = "Test1234!";
    const res = await http("POST", "/auth/register", {
      body: {
        email: ctx.buyerEmail,
        password: ctx.buyerPassword,
        firstName: "E2E",
        lastName: "Buyer",
      },
      expect: 201,
    });
    ctx.buyerId = unwrap(res.body)?.userId;
    if (!ctx.buyerId) throw new Error(`no userId in response: ${truncate(res.raw, 200)}`);
  });

  await record("auth", "login buyer via password grant", async () => {
    ctx.buyerToken = await passwordToken(ctx.buyerEmail, ctx.buyerPassword);
  });

  await record("auth", "login seller (seeded seller1)", async () => {
    ctx.sellerToken = await passwordToken("seller1", "test");
  });

  await record("auth", "login admin (seeded admin1)", async () => {
    ctx.adminToken = await passwordToken("admin1", "test");
  });

  // 2. Catalog: public reads (no auth required).
  await record("catalog", "GET /products?size=5", async () => {
    const res = await http("GET", "/products?size=5");
    const data = unwrap(res.body);
    const list = data?.content ?? data;
    if (!Array.isArray(list) || list.length === 0) {
      throw new Error(`empty catalog (seed missing?): ${truncate(res.raw, 200)}`);
    }
    ctx.sampleProductId = list[0].id;
    ctx.sampleProduct = list[0];
  });

  await record("catalog", "GET /categories returns ids", async () => {
    const res = await http("GET", "/categories");
    const data = unwrap(res.body);
    if (!Array.isArray(data) || data.length === 0) {
      throw new Error(`expected non-empty array of category ids, got ${truncate(res.raw, 200)}`);
    }
  });

  await record("catalog", "GET /products/{id} (sample)", async () => {
    if (!ctx.sampleProductId) throw new Error("no sample product id from list step");
    await http("GET", `/products/${ctx.sampleProductId}`);
  });

  await record("catalog", "GET /search?q=tai", async () => {
    await http("GET", "/search?q=tai&size=5");
  });

  // 3. Seller: create a fresh product as seller1.
  await record("seller", "POST /sellers/me/products (create)", async () => {
    const stamp = Date.now();
    const sku = `E2E-SKU-${stamp}`;
    ctx.sellerProductSku = sku;
    const res = await http("POST", "/sellers/me/products", {
      token: ctx.sellerToken,
      body: {
        name: `E2E Test Product ${stamp}`,
        description: "Created by the e2e-day script for round-trip verification.",
        categoryId: "electronics",
        brand: "E2E",
        variants: [
          {
            sku,
            name: "Default",
            priceAmount: 199000,
            priceCurrency: "VND",
            imageUrl: "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=300",
            stockQuantity: 50,
          },
        ],
        images: [
          {
            url: "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600",
            alt: `E2E Test Product ${stamp}`,
            sortOrder: 0,
          },
        ],
      },
      expect: [200, 201],
    });
    const product = unwrap(res.body);
    ctx.sellerProductId = product?.id;
    ctx.sellerProduct = product;
    if (!ctx.sellerProductId) throw new Error(`no product id in response: ${truncate(res.raw, 240)}`);
  });

  await record("seller", "GET /sellers/me visible profile", async () => {
    // seller1 has no domain seller-profile until they POST /sellers/register.
    // 400 (bad_request "seller profile not found") is acceptable here.
    await http("GET", "/sellers/me", { token: ctx.sellerToken, expect: [200, 400, 404] });
  });

  // 3b. Public sellers (anonymous). Storefront SellerShowcase + SellerDetailPage
  // back onto these endpoints. The product created above gives us a sellerId
  // we can fetch and round-trip ratingCount / totalProducts on.
  await record("sellers", "GET /sellers (anonymous, paged)", async () => {
    const res = await http("GET", "/sellers?page=0&size=20");
    const data = unwrap(res.body);
    const list = data?.content;
    if (!Array.isArray(list)) {
      throw new Error(`expected paged content array, got ${truncate(res.raw, 200)}`);
    }
    if (typeof data?.page !== "number" || typeof data?.size !== "number" || typeof data?.totalElements !== "number") {
      throw new Error(`expected page/size/totalElements numbers, got ${truncate(res.raw, 200)}`);
    }
    // Bank details must NEVER leak through public endpoints.
    for (const s of list) {
      if ("bankName" in s || "bankAccount" in s) {
        throw new Error(`public seller leaked bank details: ${truncate(JSON.stringify(s), 200)}`);
      }
    }
  });

  await record("sellers", "GET /sellers/{id} (anonymous, includes stats)", async () => {
    if (!ctx.sellerProduct?.sellerId) throw new Error("missing sellerId from earlier seller-product step");
    const res = await http("GET", `/sellers/${ctx.sellerProduct.sellerId}`, { expect: [200, 404] });
    if (res.status === 404) {
      // seller1 hasn't run /sellers/register, so the user-service has no
      // SellerProfile row even though they own products. Documented pre-condition.
      return;
    }
    const data = unwrap(res.body);
    if (!data?.id || !data?.shopName) {
      throw new Error(`expected id+shopName, got ${truncate(res.raw, 200)}`);
    }
    if ("bankName" in data || "bankAccount" in data) {
      throw new Error(`public seller leaked bank details: ${truncate(res.raw, 200)}`);
    }
    if (typeof data.ratingCount !== "number" || typeof data.totalProducts !== "number") {
      throw new Error(`expected numeric ratingCount + totalProducts, got ${truncate(res.raw, 200)}`);
    }
  });

  // 4. Cart: buyer adds the seller's product, then a seeded one.
  await record("cart", "POST /cart/items (seller's new product)", async () => {
    if (!ctx.sellerProductId) throw new Error("missing seller product id");
    await http("POST", "/cart/items", {
      token: ctx.buyerToken,
      body: { productId: ctx.sellerProductId, quantity: 2 },
      expect: [200, 201],
    });
  });

  await record("cart", "GET /cart shows the added item", async () => {
    const res = await http("GET", "/cart", { token: ctx.buyerToken });
    const data = unwrap(res.body);
    const items = data?.items ?? [];
    if (!items.find((it) => it.productId === ctx.sellerProductId)) {
      throw new Error(`cart missing the new product: ${truncate(res.raw, 200)}`);
    }
    ctx.cartId = data?.cartId ?? data?.id;
  });

  // 5. Wishlist: toggle on/off.
  await record("wishlist", "POST /users/me/wishlist/toggle (add)", async () => {
    if (!ctx.sellerProductId) throw new Error("missing seller product id");
    await http("POST", "/users/me/wishlist/toggle", {
      token: ctx.buyerToken,
      body: { productId: ctx.sellerProductId },
      expect: [200, 201],
    });
  });

  await record("wishlist", "GET /users/me/wishlist contains item", async () => {
    const res = await http("GET", "/users/me/wishlist", { token: ctx.buyerToken });
    const data = unwrap(res.body);
    const ids = Array.isArray(data) ? data : data?.items ?? data?.productIds ?? [];
    const flat = ids.map((it) => (typeof it === "string" ? it : it?.productId)).filter(Boolean);
    if (!flat.includes(ctx.sellerProductId)) {
      throw new Error(`wishlist missing product: ${truncate(JSON.stringify(flat), 160)}`);
    }
  });

  // 6. Checkout: payment methods, shipping options, calculate.
  await record("checkout", "GET /checkout/payment-methods", async () => {
    const res = await http("GET", "/checkout/payment-methods", { token: ctx.buyerToken });
    const list = unwrap(res.body);
    if (!Array.isArray(list) || list.length === 0) {
      throw new Error(`expected payment method list, got ${truncate(res.raw, 200)}`);
    }
  });

  await record("checkout", "POST /checkout/shipping-options", async () => {
    await http("POST", "/checkout/shipping-options", {
      token: ctx.buyerToken,
      body: {
        address: { street: "1 Test Way", district: "Q1", city: "HCMC" },
      },
      expect: [200, 201],
    });
  });

  // 6b. Coupon: admin creates a test coupon, buyer validates + applies it.
  // Endpoints live on coupon-service (gateway forwards /coupons/** and the
  // /checkout/{validate,apply}-coupon aliases). The coupon code is
  // timestamp-stamped so reruns don't collide on the unique-code constraint.
  await record("coupon", "POST /admin/coupons (admin creates a test coupon)", async () => {
    const stamp = Date.now();
    ctx.couponCode = `E2E${stamp}`;
    const validUntil = new Date(stamp + 24 * 60 * 60 * 1000).toISOString();
    const res = await http("POST", "/admin/coupons", {
      token: ctx.adminToken,
      body: {
        code: ctx.couponCode,
        type: "PERCENTAGE",
        value: 10,
        minOrderValue: 0,
        maxDiscount: 50000,
        maxUses: 100,
        validUntil,
      },
      expect: [200, 201],
    });
    const coupon = unwrap(res.body);
    ctx.couponId = coupon?.id;
    if (!ctx.couponId) throw new Error(`no coupon id in response: ${truncate(res.raw, 200)}`);
  });

  await record("coupon", "POST /coupons/validate (buyer validates code)", async () => {
    if (!ctx.couponCode) throw new Error("missing coupon code from create step");
    const res = await http("POST", "/coupons/validate", {
      token: ctx.buyerToken,
      body: { code: ctx.couponCode, orderAmount: 200000 },
    });
    const data = unwrap(res.body);
    if (data?.valid !== true) {
      throw new Error(`expected valid=true, got ${truncate(res.raw, 200)}`);
    }
    // 10% of 200000 = 20000, well under the 50000 max
    if (typeof data?.discount !== "number" || data.discount <= 0) {
      throw new Error(`expected positive discount, got ${truncate(res.raw, 200)}`);
    }
  });

  await record("coupon", "POST /coupons/validate rejects unknown code", async () => {
    const res = await http("POST", "/coupons/validate", {
      token: ctx.buyerToken,
      body: { code: "DOES_NOT_EXIST_42", orderAmount: 200000 },
    });
    const data = unwrap(res.body);
    if (data?.valid !== false) {
      throw new Error(`expected valid=false for unknown code, got ${truncate(res.raw, 200)}`);
    }
  });

  await record("coupon", "POST /checkout/apply-coupon (buyer consumes coupon)", async () => {
    if (!ctx.couponCode) throw new Error("missing coupon code");
    const res = await http("POST", "/checkout/apply-coupon", {
      token: ctx.buyerToken,
      body: { code: ctx.couponCode, orderAmount: 200000 },
    });
    const data = unwrap(res.body);
    if (data?.code !== ctx.couponCode) {
      throw new Error(`expected coupon echoed back, got ${truncate(res.raw, 200)}`);
    }
    if (typeof data?.discount !== "number" || typeof data?.finalTotal !== "number") {
      throw new Error(`expected numeric discount + finalTotal, got ${truncate(res.raw, 200)}`);
    }
    if (data.finalTotal !== 200000 - data.discount) {
      throw new Error(`finalTotal=${data.finalTotal} should equal 200000 - discount=${data.discount}`);
    }
  });

  await record("coupon", "POST /admin/coupons/{id}/deactivate (cleanup)", async () => {
    if (!ctx.couponId) throw new Error("missing coupon id");
    const res = await http("POST", `/admin/coupons/${ctx.couponId}/deactivate`, {
      token: ctx.adminToken,
    });
    const data = unwrap(res.body);
    if (data?.active !== false) {
      throw new Error(`expected active=false after deactivate, got ${truncate(res.raw, 200)}`);
    }
  });

  // 7. Order: place via POST /orders (the cart-driven path).
  await record("order", "POST /orders (place order)", async () => {
    if (!ctx.sellerProduct) throw new Error("missing seller product details");
    const idempotencyKey = `e2e-${Date.now()}`;
    const variant = ctx.sellerProduct.variants?.[0];
    const sku = variant?.sku ?? ctx.sellerProductSku;
    const price = variant?.priceAmount ?? 199000;
    const sellerId = ctx.sellerProduct.sellerId;
    if (!sellerId) throw new Error(`seller id missing on product: ${truncate(JSON.stringify(ctx.sellerProduct), 200)}`);
    const res = await http("POST", "/orders", {
      token: ctx.buyerToken,
      headers: { "Idempotency-Key": idempotencyKey },
      body: {
        shippingAddress: { street: "1 Test Way", district: "Q1", city: "HCMC" },
        items: [
          {
            productId: ctx.sellerProductId,
            variantSku: sku,
            sellerId,
            name: ctx.sellerProduct.name,
            quantity: 1,
            unitPriceAmount: price,
            unitPriceCurrency: "VND",
            imageUrl: ctx.sellerProduct.images?.[0]?.url ?? null,
          },
        ],
      },
      expect: [200, 201],
    });
    const order = unwrap(res.body);
    ctx.orderId = order?.id;
    // SubOrderResponse exposes the id as `subOrderId`, not `id`. Capture both
    // shapes so we work whether the BE renames it later.
    const sub = order?.subOrders?.[0];
    ctx.subOrderId = sub?.subOrderId ?? sub?.id;
    if (!ctx.orderId) throw new Error(`no orderId in response: ${truncate(res.raw, 240)}`);
  });

  await record("order", "GET /orders lists my order", async () => {
    // The buyer-facing list is served from the order_summary projection,
    // which the order-service populates asynchronously by consuming its own
    // outbox via Kafka. There's a CQRS read-side window of a few hundred
    // milliseconds between POST /orders returning 201 and the projection
    // catching up. Retry briefly so the test reflects "was the order made"
    // rather than the projection latency.
    const deadline = Date.now() + 5_000;
    while (true) {
      const res = await http("GET", "/orders", { token: ctx.buyerToken });
      const data = unwrap(res.body);
      const list = data?.content ?? data;
      if (Array.isArray(list) && list.find((o) => (o.orderId ?? o.id) === ctx.orderId)) return;
      if (Date.now() >= deadline) {
        throw new Error(`my orders list missing the new order after 5s of polling: ${truncate(res.raw, 200)}`);
      }
      await new Promise((r) => setTimeout(r, 250));
    }
  });

  await record("order", "GET /orders/{id}", async () => {
    if (!ctx.orderId) throw new Error("no orderId");
    await http("GET", `/orders/${ctx.orderId}`, { token: ctx.buyerToken });
  });

  // 8. Seller fulfilment: accept + ship the sub-order.
  await record("fulfilment", "GET /seller/orders/pending", async () => {
    const res = await http("GET", "/seller/orders/pending", { token: ctx.sellerToken });
    const list = unwrap(res.body);
    if (!Array.isArray(list)) throw new Error(`expected array of pending sub-orders, got ${truncate(res.raw, 200)}`);
    if (!ctx.subOrderId) {
      // The pending list returns OrderResponse[], so dig into the first sub-order.
      for (const o of list) {
        const sub = o.subOrders?.[0];
        if (sub) {
          ctx.subOrderId = sub.subOrderId ?? sub.id;
          break;
        }
      }
    }
  });

  await record("fulfilment", "PUT /seller/orders/{subOrderId}/accept", async () => {
    if (!ctx.subOrderId) throw new Error("no sub-order id from pending list");
    await http("PUT", `/seller/orders/${ctx.subOrderId}/accept`, {
      token: ctx.sellerToken,
      expect: [200, 204],
    });
  });

  await record("fulfilment", "PUT /seller/orders/{subOrderId}/ship", async () => {
    if (!ctx.subOrderId) throw new Error("no sub-order id");
    await http("PUT", `/seller/orders/${ctx.subOrderId}/ship`, {
      token: ctx.sellerToken,
      body: { trackingNumber: `E2E-${Date.now()}`, carrier: "GHN" },
      expect: [200, 204],
    });
  });

  await record("shipping", "GET /shipping/tracking/{code}", async () => {
    const code = `E2E-${Date.now()}`;
    await http("GET", `/shipping/tracking/${code}?carrier=GHN`, {
      token: ctx.buyerToken,
      expect: [200, 404],
    });
  });

  // 9. Reviews + Q&A.
  await record("review", "POST /reviews (buyer leaves a review)", async () => {
    // Some review use cases require a delivered orderId. Pass it; if the BE
    // doesn't accept the just-placed order yet, accept either 200/201/202 (created)
    // OR 400 (the BE explicitly rejects unverified orders) as a documented
    // pre-condition. The smoke test verifies the endpoint round-trips, not
    // the business policy.
    await http("POST", "/reviews", {
      token: ctx.buyerToken,
      body: {
        productId: ctx.sellerProductId,
        orderId: ctx.orderId ?? "00000000-0000-0000-0000-000000000000",
        rating: 5,
        comment: "E2E test review — looks great.",
      },
      expect: [200, 201, 202, 400],
    });
  });

  await record("review", "GET /reviews/product/{id}", async () => {
    await http("GET", `/reviews/product/${ctx.sellerProductId}`);
  });

  await record("review", "POST /questions (buyer asks)", async () => {
    await http("POST", "/questions", {
      token: ctx.buyerToken,
      body: {
        productId: ctx.sellerProductId,
        question: "Is this in stock?",
      },
      expect: [200, 201],
    });
  });

  // 10. Recommendations.
  await record("recs", "GET /recommendations/frequently-bought-together/{id}", async () => {
    // 503 is the recommendations-service circuit-breaker fallback when the
    // service is unavailable — accept it as a documented degradation path.
    await http("GET", `/recommendations/frequently-bought-together/${ctx.sellerProductId}`, {
      expect: [200, 404, 503],
    });
  });

  // 11. Admin dashboards.
  await record("admin", "GET /admin/dashboard/summary", async () => {
    await http("GET", "/admin/dashboard/summary", { token: ctx.adminToken });
  });

  await record("admin", "GET /admin/dashboard/top-products", async () => {
    await http("GET", "/admin/dashboard/top-products", { token: ctx.adminToken });
  });

  await record("admin", "GET /admin/sellers", async () => {
    await http("GET", "/admin/sellers", { token: ctx.adminToken });
  });

  await record("admin", "GET /admin/disputes/open", async () => {
    await http("GET", "/admin/disputes/open", { token: ctx.adminToken });
  });

  // 12. User: profile + addresses.
  await record("user", "GET /users/me", async () => {
    // 400 is the legitimate "buyer profile not found" path before first PUT —
    // accept it so a brand-new user doesn't fail the suite.
    await http("GET", "/users/me", { token: ctx.buyerToken, expect: [200, 400, 404] });
  });

  await record("user", "PUT /users/me (upsert profile)", async () => {
    await http("PUT", "/users/me", {
      token: ctx.buyerToken,
      body: { name: "E2E Buyer", phone: "+84900000001", avatarUrl: null },
      expect: [200, 201],
    });
  });

  // Summary.
  console.log(`\n=== summary ===`);
  const pass = results.filter((r) => r.status === "PASS").length;
  const fail = results.filter((r) => r.status === "FAIL").length;
  console.log(`  ${pass} passed, ${fail} failed`);
  if (fail > 0) {
    console.log(`\nfailures:`);
    for (const r of results.filter((r) => r.status === "FAIL")) {
      console.log(`  - ${r.section}/${r.name}: ${r.detail}`);
    }
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error("fatal:", err);
  process.exit(2);
});
