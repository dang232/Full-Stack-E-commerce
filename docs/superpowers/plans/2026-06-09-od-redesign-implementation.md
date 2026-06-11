# Open Design VNShop Redesign — Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the OD vnshop-redesign visual design into the existing `fe/` React SPA, translating 9 static HTML prototypes into the live Tailwind + React component pages.

**Architecture:** The existing `fe/` already has the correct token system (`theme.css`), routing, API layer, hooks, and page shells. This is a pure UI reskin — we replace the JSX markup in each page file to match the OD HTML structure, using existing Tailwind utilities mapped to the same OKLch token palette. No new dependencies are needed; the design system tokens are already 1:1 with OD's CSS variables.

**Tech Stack:** Vite 6, React 18, Tailwind CSS v4, react-router 7, TanStack Query, Zustand, lucide-react, motion (framer-motion), sonner toasts

---

## Design-to-Code Mapping

| OD HTML File | React Page File | Scope |
|---|---|---|
| `index.html` | `fe/src/app/pages/HomePage.tsx` | Hero, categories grid, flash sale, product grid, trust bar |
| `search.html` | `fe/src/app/pages/SearchPage.tsx` | Filter sidebar, sort pills, product grid, pagination |
| `product.html` | `fe/src/app/pages/ProductPage.tsx` | Gallery, info panel, reviews, Q&A, related |
| `cart.html` | `fe/src/app/pages/CartPage.tsx` | Cart items, quantity, coupon, summary sidebar |
| `checkout-flow.html` | `fe/src/app/pages/checkout/` | Stepper, address/shipping/payment/review steps, success |
| `login.html` | `fe/src/app/pages/LoginPage.tsx` | Split layout, brand panel, form |
| `seller-admin.html` | `fe/src/app/pages/seller/SellerPage.tsx` | Sidebar nav, KPI cards, order table, charts |
| `buyer-flows.html` | `fe/src/app/pages/OrdersPage.tsx`, `NotificationsPage.tsx`, `MessagesPage.tsx`, `WishlistPage.tsx`, `ProfilePage.tsx` | Orders list, notifications, chat, wishlist grid, profile form |
| `states-modals.html` | `fe/src/app/pages/RegisterPage.tsx`, `PasswordResetPage.tsx`, `NotFoundPage.tsx` + shared UI components | Register, reset, 404, empty states, error states, skeletons, dialogs |

## CSS Token Alignment (already done ✅)

The existing `fe/src/styles/theme.css` uses the **same OKLch values** as the OD `styles.css`:
- `--primary: oklch(52% 0.2 270)` ✅
- `--accent: oklch(72% 0.17 70)` ✅
- `--border: oklch(91% 0.006 260)` ✅
- Same radius scale, shadow scale, fluid type scale ✅
- Dark mode tokens match ✅

**No theme.css changes needed.** We just need to use the correct Tailwind utility classes.

## OD CSS → Tailwind Utility Cheat Sheet

| OD class / token | Tailwind utility |
|---|---|
| `var(--bg-primary)` | `bg-card` |
| `var(--bg-secondary)` | `bg-background` |
| `var(--bg-elevated)` | `bg-surface-elevated` |
| `var(--border)` | `border-border` |
| `var(--border-hover)` | `border-border-hover` |
| `var(--text-primary)` | `text-foreground` |
| `var(--text-secondary)` | `text-text-secondary` |
| `var(--text-muted)` | `text-muted-foreground` |
| `var(--primary)` | `bg-primary` / `text-primary` |
| `var(--primary-light)` | `bg-primary-light` |
| `var(--error)` | `bg-error` / `text-error` |
| `var(--success)` | `text-success` |
| `var(--radius-lg)` | `rounded-[var(--radius-lg)]` or `rounded-lg` |
| `var(--radius-xl)` | `rounded-[var(--radius-xl)]` or `rounded-xl` |
| `var(--shadow-lg)` | `shadow-[var(--shadow-lg)]` |
| `var(--duration-base)` | `duration-[var(--duration-base)]` |

## Shared Component Inventory

These components should be extracted/updated to be reused across pages:

| Component | Location | Used By |
|---|---|---|
| `Navbar` | `fe/src/app/components/navbar.tsx` (create) | All pages |
| `Footer` | `fe/src/app/components/footer.tsx` (create) | Public pages |
| `ProductCard` | Already in `HomePage.tsx`, extract | Home, Search, Wishlist |
| `CategoryPill` | Extract from Home | Home, Nav |
| `PageSkeleton` | Already exists | All pages |
| `EmptyState` | Create | Cart, Orders, Wishlist, Notifications |
| `ConfirmDialog` | Create | Seller, Admin, Cart |

---

## Task 1: Extract Shared Layout — Navbar + Footer

**Files:**
- Create: `fe/src/app/components/navbar.tsx`
- Create: `fe/src/app/components/footer.tsx`
- Modify: `fe/src/app/pages/Root.tsx`

**Why:** The OD design has a consistent Navbar (logo, search, actions) and Footer across all buyer-facing pages. Currently these are likely embedded in `Root.tsx` or repeated. We extract them as standalone components matching the OD markup.

- [ ] **Step 1: Create Navbar component**

Translate the OD `nav` element (announcement bar + sticky nav + categories bar) into React using Tailwind classes. Include:
- Announcement bar with promo text
- Sticky nav: logo, search input with suggestion dropdown, action buttons (bell, heart, cart badge, sign-in)
- Categories pill bar (horizontally scrollable)

Key behaviors from OD:
- `nav.scrolled` adds shadow on scroll (use `useEffect` + scroll listener)
- Search suggestions dropdown shows on focus, filters on input
- Cart badge shows item count from `useCart()` hook
- Dark mode toggle (already in `vnshop-context.tsx`)

- [ ] **Step 2: Create Footer component**

Translate the OD footer grid (5-col: brand + Shop + Sell + Help + Company) + bottom bar.

- [ ] **Step 3: Wire into Root.tsx layout**

Add `<Navbar />` and `<Footer />` to the Root outlet layout. Remove any existing nav/footer code in Root.

- [ ] **Step 4: Run typecheck + lint**

```bash
cd fe && npm run typecheck && npm run lint
```

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(fe): extract Navbar + Footer from OD redesign"
```

---

## Task 2: HomePage Reskin

**Files:**
- Modify: `fe/src/app/pages/HomePage.tsx`

**Why:** Match the OD `index.html` layout: hero banner, category grid (6 icons), flash sale section with countdown, recommended products grid, trust bar.

- [ ] **Step 1: Rewrite Hero section**

OD pattern: gradient purple background, badge, h1, description, CTA button. Use `motion` for entrance animation.

```tsx
<section className="mx-[var(--content-padding)] mt-6 bg-gradient-to-br from-primary to-[oklch(50%_0.22_295)] rounded-[var(--radius-2xl)] p-[clamp(32px,5vw,56px)] relative overflow-hidden">
  {/* Decorative circles */}
  <div className="absolute -top-[60%] -right-[15%] w-[500px] h-[500px] bg-white/[0.04] rounded-full animate-pulse" />
  <div className="relative z-10 max-w-[480px]">
    <div className="inline-flex items-center gap-1.5 bg-white/[0.12] backdrop-blur-sm px-3.5 py-1.5 rounded-full text-xs text-white font-medium border border-white/10 mb-4">
      <Zap className="w-3.5 h-3.5" /> Limited Time
    </div>
    <h1 className="text-[var(--text-4xl)] font-extrabold text-white leading-[1.15] mb-3.5 tracking-tight">
      Mid-Year Mega Sale<br/>Up to 70% Off
    </h1>
    ...
  </div>
</section>
```

- [ ] **Step 2: Rewrite Category Grid**

6-item grid with icon + label cards. Use existing `useCategories()` hook data.

- [ ] **Step 3: Rewrite Flash Sale section**

Flash header (icon + title + countdown timer), product cards in auto-fill grid. Use existing `useFlashSaleWithProducts()` and `useCountdown()` hooks.

- [ ] **Step 4: Rewrite Recommended section**

Standard product grid with `SectionHeader`. Use existing `useProducts()` hook.

- [ ] **Step 5: Add Trust Bar**

4-item grid: Free Shipping, Buyer Protection, Verified Sellers, Secure Payment — each with icon, title, description.

- [ ] **Step 6: Run verify**

```bash
cd fe && npm run typecheck && npm run lint
```

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(fe): HomePage reskin matching OD redesign"
```

---

## Task 3: SearchPage Reskin

**Files:**
- Modify: `fe/src/app/pages/SearchPage.tsx`

**Why:** Match OD `search.html`: sticky filter sidebar (categories, price range, rating, shipping, seller), active filter pills, sort pills toolbar, product grid with pagination.

- [ ] **Step 1: Rewrite filter sidebar**

Sticky panel with filter groups (checkboxes), price range inputs, "Apply Price" button, "Clear all" button. Wire to existing `useSearchFacets()` hook.

- [ ] **Step 2: Rewrite search toolbar**

Result count, sort pills (Relevant, Popular, Price ↑, Price ↓, Newest). Active filter pills with X dismiss above the toolbar.

- [ ] **Step 3: Rewrite product grid + pagination**

`grid-template-columns: repeat(auto-fill, minmax(200px, 1fr))` product cards + numbered pagination bar at bottom.

- [ ] **Step 4: Run verify + commit**

```bash
cd fe && npm run typecheck && npm run lint
git add -A && git commit -m "feat(fe): SearchPage reskin matching OD redesign"
```

---

## Task 4: ProductPage Reskin

**Files:**
- Modify: `fe/src/app/pages/ProductPage.tsx`

**Why:** Match OD `product.html`: two-column layout (sticky gallery left, info panel right), variant selector, quantity stepper, add-to-cart/buy-now buttons, tabs (description/specs/reviews/Q&A), related products.

- [ ] **Step 1: Rewrite gallery column**

Sticky gallery with main image (aspect-square) + thumbnail strip below. Click thumbnail to switch. Hover zoom effect.

- [ ] **Step 2: Rewrite product info panel**

Brand link, product name, rating stars + sold count, price block, variant selector (color dots + size pills), quantity stepper, action buttons, trust row (authentic/free-ship/returns).

- [ ] **Step 3: Rewrite tabs section**

Tab pills (Description, Specifications, Reviews, Q&A) with content panels. Reviews section with rating breakdown bar chart + review cards. Q&A section with question/answer pairs.

- [ ] **Step 4: Add Related Products grid**

Standard product card grid below tabs.

- [ ] **Step 5: Run verify + commit**

```bash
cd fe && npm run typecheck && npm run lint
git add -A && git commit -m "feat(fe): ProductPage reskin matching OD redesign"
```

---

## Task 5: CartPage Reskin

**Files:**
- Modify: `fe/src/app/pages/CartPage.tsx`

**Why:** Match OD `cart.html`: two-column layout (cart items left, summary sidebar right). Items show image, name, variant, seller, quantity stepper, remove button, price. Summary shows subtotal, shipping, coupon input, total, checkout button, guarantees.

- [ ] **Step 1: Rewrite cart item cards**

Each item: 100px image placeholder, info (name, variant, seller), bottom row (price + quantity stepper + trash button). Remove animation on delete.

- [ ] **Step 2: Rewrite summary sidebar**

Sticky sidebar: row items (subtotal, shipping, coupon discount), total, savings badge, coupon input + apply button, "Proceed to Checkout" button, guarantee items.

- [ ] **Step 3: Add empty state**

When cart is empty: icon, "Your cart is empty" message, "Continue Shopping" button.

- [ ] **Step 4: Run verify + commit**

```bash
cd fe && npm run typecheck && npm run lint
git add -A && git commit -m "feat(fe): CartPage reskin matching OD redesign"
```

---

## Task 6: Checkout Flow Reskin

**Files:**
- Modify: `fe/src/app/pages/checkout/CheckoutPage.tsx`
- Modify: `fe/src/app/pages/checkout/CheckoutAddressStep.tsx`
- Modify: `fe/src/app/pages/checkout/CheckoutShippingStep.tsx`
- Modify: `fe/src/app/pages/checkout/CheckoutPaymentStep.tsx`
- Modify: `fe/src/app/pages/checkout/CheckoutReviewStep.tsx`
- Modify: `fe/src/app/pages/checkout/CheckoutSuccess.tsx`
- Modify: `fe/src/app/pages/checkout/CheckoutSummary.tsx`

**Why:** Match OD `checkout-flow.html` stepper UX: 4 steps (Address → Shipping → Payment → Review) + success with confetti.

- [ ] **Step 1: Rewrite stepper UI**

Circle icons connected by lines, active/completed states with animations. Step labels below circles.

- [ ] **Step 2: Rewrite Address step**

Radio-select address cards (name, address, phone) with "Default" badge, "Add new address" dashed button.

- [ ] **Step 3: Rewrite Shipping step**

Shipping option cards (icon, name, description, ETA, price). Selected state with purple border + subtle bg.

- [ ] **Step 4: Rewrite Payment step**

Payment method cards: VietQR, Stripe, PayPal, COD. Security note at bottom.

- [ ] **Step 5: Rewrite Review step**

Summary sections (address, shipping, payment) with "Change" buttons + items list.

- [ ] **Step 6: Rewrite Success page**

Animated check icon, order ID, confetti animation, action buttons.

- [ ] **Step 7: Rewrite Summary sidebar**

Consistent across all steps: items total, shipping, total, continue button.

- [ ] **Step 8: Run verify + commit**

```bash
cd fe && npm run typecheck && npm run lint
git add -A && git commit -m "feat(fe): Checkout flow reskin matching OD redesign"
```

---

## Task 7: LoginPage + RegisterPage + PasswordResetPage Reskin

**Files:**
- Modify: `fe/src/app/pages/LoginPage.tsx`
- Modify: `fe/src/app/pages/RegisterPage.tsx`
- Modify: `fe/src/app/pages/PasswordResetPage.tsx`

**Why:** Match OD `login.html` (split layout with brand panel) and `states-modals.html` (register card, password reset card).

- [ ] **Step 1: Rewrite LoginPage**

Two-panel layout: left = purple gradient brand panel (logo, headline, trust stats grid), right = form (email, password, remember me, forgot, submit, divider, social buttons, register link). Mobile: only form side visible.

- [ ] **Step 2: Rewrite RegisterPage**

Centered card: logo, heading, form (first/last name row, email, phone, password, confirm), submit, terms, login link.

- [ ] **Step 3: Rewrite PasswordResetPage**

Centered card: key icon, heading, email input, submit, success message box, back link.

- [ ] **Step 4: Run verify + commit**

```bash
cd fe && npm run typecheck && npm run lint
git add -A && git commit -m "feat(fe): Auth pages reskin matching OD redesign"
```

---

## Task 8: Buyer Flows — Orders, Notifications, Messages, Wishlist, Profile

**Files:**
- Modify: `fe/src/app/pages/OrdersPage.tsx`
- Modify: `fe/src/app/pages/NotificationsPage.tsx`
- Modify: `fe/src/app/pages/MessagesPage.tsx`
- Modify: `fe/src/app/pages/WishlistPage.tsx`
- Modify: `fe/src/app/pages/ProfilePage.tsx`

**Why:** Match OD `buyer-flows.html` which defines the order list, notification list, chat UI, wishlist grid, and profile form.

- [ ] **Step 1: Rewrite OrdersPage**

Tab pills (All, Pending, Shipped, Delivered, Cancelled) + order cards. Each card: top row (order ID + date + status pill), item row (image + name + qty), bottom row (total + action buttons).

Status pill styles:
- Pending: `bg-amber-100 text-amber-800`
- Shipped: `bg-primary-light text-primary`
- Delivered: `bg-green-100 text-green-800`
- Cancelled: `bg-red-100 text-red-800`

- [ ] **Step 2: Rewrite NotificationsPage**

Notification list items: icon circle (colored variants), title with bold text, timestamp, unread dot. Unread items have `bg-primary-light`.

- [ ] **Step 3: Rewrite MessagesPage**

Two-panel layout: left sidebar (thread list with avatar, name, last message, time, unread badge), right chat panel (header, bubble messages, input bar with send button).

- [ ] **Step 4: Rewrite WishlistPage**

4-column product grid using shared `ProductCard` with heart always visible + active red.

- [ ] **Step 5: Rewrite ProfilePage**

Two-column: sidebar (avatar, name, email, nav items) + content panel (form with 2-col grid inputs, save/cancel buttons).

- [ ] **Step 6: Run verify + commit**

```bash
cd fe && npm run typecheck && npm run lint
git add -A && git commit -m "feat(fe): Buyer flow pages reskin matching OD redesign"
```

---

## Task 9: Seller Dashboard Reskin

**Files:**
- Modify: `fe/src/app/pages/seller/SellerPage.tsx`
- Modify: `fe/src/app/pages/seller/SellerDashboard.tsx`
- Modify: `fe/src/app/pages/seller/SellerOrders.tsx`
- Modify: `fe/src/app/pages/seller/SellerProducts.tsx`
- Modify: `fe/src/app/pages/seller/SellerWallet.tsx`

**Why:** Match OD `seller-admin.html` sidebar layout with KPI cards, order tables, revenue charts.

- [ ] **Step 1: Rewrite SellerPage layout**

Sidebar (240px, sticky): logo, nav sections (Dashboard, Orders with badge, Products, Reviews, Wallet, Settings, switch to Admin), main content area with padding.

- [ ] **Step 2: Rewrite SellerDashboard**

KPI cards row (revenue, orders, products, rating — each with icon, value, label, trend badge), recent orders table, revenue chart placeholder.

- [ ] **Step 3: Rewrite SellerOrders**

Table with columns: Order ID, Customer, Items, Total, Status pill, Actions (Accept/Ship buttons). Use OD table styles.

- [ ] **Step 4: Run verify + commit**

```bash
cd fe && npm run typecheck && npm run lint
git add -A && git commit -m "feat(fe): Seller dashboard reskin matching OD redesign"
```

---

## Task 10: Shared UI Components — EmptyState, ConfirmDialog, Skeletons

**Files:**
- Create: `fe/src/app/components/ui/empty-state.tsx`
- Create: `fe/src/app/components/ui/confirm-dialog.tsx`
- Modify: `fe/src/app/components/ui/page-skeleton.tsx`
- Modify: `fe/src/app/pages/NotFoundPage.tsx`

**Why:** Match OD `states-modals.html` patterns: empty states (cart, wishlist, orders, search, messages, notifications), confirmation dialogs (delete, accept order, ship), skeleton loading grid, 404 page.

- [ ] **Step 1: Create EmptyState component**

Generic component: `icon`, `title`, `description`, optional `action` button. Styled with centered layout, icon circle, subtle text.

```tsx
interface EmptyStateProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  action?: { label: string; onClick: () => void };
}
```

- [ ] **Step 2: Create ConfirmDialog component**

Modal overlay with centered card: icon circle (warning/danger variant), title, description, action buttons (cancel + confirm).

```tsx
interface ConfirmDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  description: string;
  confirmLabel?: string;
  variant?: "warning" | "danger";
}
```

- [ ] **Step 3: Update PageSkeleton**

Match OD skeleton pattern: 4-column card grid with shimmer rectangles (image aspect-square + 3 text lines at 75%, 50%, 30% width).

- [ ] **Step 4: Rewrite NotFoundPage**

Large "404" text (96px, primary color, low opacity), heading, description, two action buttons (Home + Search).

- [ ] **Step 5: Run verify + commit**

```bash
cd fe && npm run typecheck && npm run lint
git add -A && git commit -m "feat(fe): shared UI components (EmptyState, ConfirmDialog, skeletons, 404)"
```

---

## Task 11: Final Integration Pass

**Files:**
- Review all modified pages for consistency

- [ ] **Step 1: Ensure all pages use shared Navbar/Footer**

Check that `Root.tsx` renders `<Navbar />` + `<Outlet />` + `<Footer />` correctly and that seller/admin pages use their own sidebar layout without the public footer.

- [ ] **Step 2: Verify dark mode works on all pages**

Toggle dark mode and visually confirm all pages respect the theme tokens (no hardcoded colors).

- [ ] **Step 3: Run full verify pipeline**

```bash
cd fe && npm run verify
```

This runs: typecheck → lint → format:check → test → build

- [ ] **Step 4: Fix any failures**

Address any TypeScript, lint, or test errors surfaced by the verify pipeline.

- [ ] **Step 5: Final commit**

```bash
git add -A && git commit -m "feat(fe): OD redesign integration complete — final polish"
```

---

## Implementation Notes

### What NOT to change:
- `fe/src/app/lib/api/` — API client layer is untouched
- `fe/src/app/hooks/` — All data hooks remain as-is (they return the same types)
- `fe/src/app/types/` — Type definitions stay the same
- `fe/src/styles/theme.css` — Already aligned with OD tokens
- `fe/src/app/routes.ts` — No route changes needed

### Key patterns to follow:
1. **Use Tailwind utilities** — no inline `style={}` unless for CSS custom properties
2. **Use lucide-react** — same icons as OD's lucide unpkg
3. **Use motion** — for entrance animations (already established in HomePage)
4. **Use existing hooks** — `useCart()`, `useProducts()`, `useSearch()`, etc. provide all data
5. **Preserve accessibility** — keep `aria-label`, `role`, `tabIndex`, keyboard handlers from existing code
6. **Match OD spacing** — use `var(--content-padding)` for page margins, `var(--radius-lg)` for cards
