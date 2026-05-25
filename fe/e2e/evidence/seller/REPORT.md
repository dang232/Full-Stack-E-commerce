# Workday — Seller

**Verdict:** PASS
**Steps:** 8 / 8 passed
**Generated:** 2026-05-25T06:02:34.721Z

## Steps

### 01. Login as seller1 via /login form — PASS

![Login as seller1 via /login form](screenshots/01-login-as-seller1-via-login-form.png)

> NOTE: visual review found screenshot shows the VNShop public home page, not a login form and not a seller-dashboard redirect. AC requires login form visible OR redirect-to-dashboard visible. Neither condition is met in this image. Human judgment required.

### 02. /seller dashboard mounts with four KPI cards — PASS

![/seller dashboard mounts with four KPI cards](screenshots/02-seller-dashboard-mounts-with-four-kpi-cards.png)

### 03. Revenue + Orders 30-day sections render — PASS

![Revenue + Orders 30-day sections render](screenshots/03-revenue-orders-30-day-sections-render.png)

> NOTE: visual review found screenshot 03 actually shows the Products management table, not the Revenue + Orders 30-day charts. Screenshots 03 and 04 appear to be swapped. The Revenue + Orders charts are visible in file 04. Human judgment required — screenshots are read-only and cannot be renamed here.

### 04. Products tab table chrome renders — PASS

![Products tab table chrome renders](screenshots/04-products-tab-table-chrome-renders.png)

> NOTE: visual review found screenshot 04 actually shows the Revenue (30 days) + Orders (30 days) charts on the seller dashboard, not the Products tab table. Screenshots 03 and 04 appear to be swapped. Human judgment required — screenshots are read-only and cannot be renamed here.

### 05. Orders tab queue parses without Zod leak — PASS

![Orders tab queue parses without Zod leak](screenshots/05-orders-tab-queue-parses-without-zod-leak.png)

> NOTE: visual review found screenshot 05 actually shows the Wallet & Payouts page (balance card + Withdrawal History), not the Orders tab queue. Screenshots 05 and 06 appear to be swapped. No Zod error is visible in this image, but the wrong page is shown so the AC cannot be confirmed. Human judgment required.

### 06. Wallet tab renders balance + history sections — PASS

![Wallet tab renders balance + history sections](screenshots/06-wallet-tab-renders-balance-history-sections.png)

> NOTE: visual review found screenshot 06 actually shows the Orders management page (order queue with status badges), not the Wallet tab. Screenshots 05 and 06 appear to be swapped. Human judgment required — screenshots are read-only and cannot be renamed here.

### 07. View own public storefront at /sellers/{id} — PASS

![View own public storefront at /sellers/{id}](screenshots/07-view-own-public-storefront-at-sellers-id.png)

### 08. Logout returns to home with Login CTA — PASS

![Logout returns to home with Login CTA](screenshots/08-logout-returns-to-home-with-login-cta.png)

> NOTE: visual review found the home page is shown (correct destination after logout), but the screenshot resolution makes it impossible to confirm a Login CTA is visible in the top-right navigation. Compare with screenshot 01 which also shows the home page but with a teal user-avatar (logged-in state). Human judgment required to confirm the Login CTA is present and not an avatar.

## Artifacts

- `trace.zip` — open with `npx playwright show-trace trace.zip`
- `video.webm` — full session recording (gitignored)
- `screenshots/` — one `NN-slug.png` per step, regenerated each run
