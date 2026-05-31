# VNShop Marketplace — Full UI/UX Audit Fix Design

**Date:** 2026-05-31  
**Scope:** 111 items across 5 categories  
**Stack:** React 18 + TypeScript + Vite, Zustand, React Query, Tailwind CSS

---

## Branch Strategy

5 branches, phased to avoid merge conflicts on shared files:

```
Phase 1 (parallel):
  fix/critical-bugs     → 18 functionality-breaking issues
  fix/state-data        → 8 unique state/race/error items (3 overlap with critical)

Phase 2 (parallel, after Phase 1 merges):
  fix/dead-buttons      → 24 non-functional UI elements → stub with toast/placeholder
  fix/ux-antipatterns   → 20 UX issues (10 high + 10 medium severity)

Phase 3 (after Phase 2 merges):
  fix/accessibility     → 38 WCAG violations (additive, touches all files)
```

---

## Category 1: Critical Bugs (18 items) — `fix/critical-bugs`

### Order & Payment Flow

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 1 | CheckoutPage.tsx | 293 | Payment failure falls through to success | Check payment response status before navigating to success. On failure → error state with retry |
| 2 | CheckoutPage.tsx | 262 | No double-click guard on Place Order | `isSubmitting` ref guard + disable button during API call |
| 3 | ProductPage.tsx | 187 | Variant not passed to addToCart | Thread selected color/size state into addToCart payload |
| 4 | CheckoutPage.tsx | 354 | Address step allows undefined address | Disable "Next" when no address selected; validate before advance |
| 5 | CartPage.tsx | 69 | No max quantity / stock validation | Cap at `product.stock`, validate on update, show error if exceeded |
| 6 | use-cart.ts | 148 | Guest-to-server merge not idempotent | Idempotency key per merge, deduplicate by productId+variantId |

### Search & Discovery

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 7 | SearchPage.tsx | 358 | clearFilters wipes search query | Reset filter state only, preserve `q` param |
| 8 | SearchPage.tsx | 244 | Filters not synced to URL | Sync to `useSearchParams`, restore on mount |
| 9 | HomePage.tsx | 736 | Category tabs hardcoded IDs | Fetch from API, match by slug |

### Auth & Session

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 10 | LoginPage.tsx | 42 | navigate() before auth propagates | Await Zustand auth state update before navigate |
| 11 | client.ts | 131 | Token refresh race (concurrent 401s) | Queue retries behind single refresh promise (mutex) |
| 12 | client.ts | 123 | No request timeout | `AbortController` with 30s default timeout |

### Seller/Admin

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 13 | SellerProducts.tsx | 14 | Fetches ALL products | Pass `sellerId` filter to query |
| 14 | SellerProducts.tsx | 77 | Hard cap 50, no pagination | Add offset pagination with "Load more" |
| 15 | SellerOrders.tsx | 20 | Operator precedence in status filter | Add explicit parentheses |
| 16 | OrdersPage.tsx | 504 | useSuspenseQuery no Suspense boundary | Wrap with `<Suspense>` + `<ErrorBoundary>` |

### Wishlist & Context

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 17 | vnshop-context.tsx | 81 | toggleWishlist no auth guard | Check auth, toast + redirect if unauthenticated |
| 18 | HomePage.tsx | 797 | ProductsSection ignores loading/error | Add loading skeleton + error fallback |

---

## Category 2: State & Data Issues (8 unique) — `fix/state-data`

Items overlapping with critical bugs (token race #11, double-submit #2, OrdersPage crash #16) are fixed in that branch only.

### Race Conditions

| # | File | Issue | Fix |
|---|------|-------|-----|
| 1 | client.ts | Token refresh race across tabs | `BroadcastChannel` mutex — one tab refreshes, others wait |
| 2 | use-cart.ts | Optimistic update during load | Gate mutations behind `isSuccess` from cart query |

### Stale Data

| # | File | Issue | Fix |
|---|------|-------|-----|
| 3 | use-cart.ts | staleTime=30s, no refetchOnWindowFocus | Add `refetchOnWindowFocus: true` |
| 4 | vnshop-context.tsx / Root.tsx | Dark mode resets on reload | Persist to localStorage, read on mount |
| 5 | CheckoutPage.tsx | Idempotency key never regenerated | Fresh key per checkout attempt, reset on cart change |

### Missing Error Handling

| # | File | Issue | Fix |
|---|------|-------|-----|
| 6 | MessagesPage.tsx | threadId race | Guard with optional chaining + fallback UI |
| 7 | error-boundary.tsx | Retry creates flash loop | Exponential backoff + max-retry counter |
| 8 | vnshop-context.tsx | Guest wishlist migration partial failure | try/catch, toast partial failures, keep successful items |

---

## Category 3: Dead Buttons (24 items) — `fix/dead-buttons`

**Strategy:** Wire each dead element to a toast notification: `"This feature is coming soon!"` using Sonner (already in deps). For social links, open the actual social media URL in a new tab (placeholder URLs).

### Navigation (Root.tsx)

| Element | Fix |
|---------|-----|
| "Support" nav button | Toast: "Support center coming soon" |
| Social media icons (FB, IG, TW, YT) | Open `https://{platform}.com` in new tab |
| 15 footer links | Toast per link: "{Link name} coming soon" |
| Footer badges (DMCA, BoCongThuong, SSL) | No action needed — decorative. Add `aria-hidden` |
| "Notifications" dropdown item | Navigate to `/notifications` (page exists) |
| "Settings" dropdown item | Toast: "Settings coming soon" |

### Product & Shopping

| Element | Fix |
|---------|-----|
| Share button (ProductPage) | Copy URL to clipboard + toast "Link copied!" |
| "Size Guide" link | Toast: "Size guide coming soon" |
| "(X reviews)" count | Scroll to reviews section on click |
| App Store button (HomePage) | Toast: "Mobile app coming soon" |
| Google Play button (HomePage) | Toast: "Mobile app coming soon" |
| Voucher VNSHOP50 block | Copy code to clipboard + toast "Coupon copied!" |
| Payment badges (CartPage) | Decorative — add `aria-hidden`, no click handler needed |

### Checkout & Auth

| Element | Fix |
|---------|-----|
| Step progress circles | Make clickable to navigate between completed steps |
| "Remember me" checkbox | Bind to state, persist login preference to localStorage |

### Profile & Account

| Element | Fix |
|---------|-----|
| "Notifications" sidebar | Fix href to `/notifications` |
| "Reviews" sidebar | Navigate to `/orders?tab=reviews` if tab exists, otherwise toast "Reviews coming soon" |
| Payment Methods tab | Keep "Coming soon" but style as disabled tab |

### Seller/Admin & Wishlist

| Element | Fix |
|---------|-----|
| Filter button (SellerProducts) | Toast: "Filtering coming soon" |
| "Export Report" (AdminDashboard) | Toast: "Export coming soon" |
| Share button (WishlistPage) | Copy URL to clipboard + toast |
| Filter button (WishlistPage) | Toast: "Filtering coming soon" |

---

## Category 4: UX Anti-Patterns (20 items) — `fix/ux-antipatterns`

### HIGH Severity (10)

| # | Issue | Fix |
|---|-------|-----|
| 1 | Cart blocks guest entirely | Show localStorage cart for guests with "Login to checkout" CTA |
| 2 | Checkout address navigates away | Use inline modal/drawer for address form instead of navigation |
| 3 | Hover-only interactions on mobile | Always show action buttons on touch devices via `@media (hover: none)` |
| 4 | Seller order accept no confirmation | Add confirmation dialog before accept |
| 5 | Admin can't reject sellers | Add "Reject" button with reason field |
| 6 | PayoutsQueue shares isPending | Scope loading state per row using row ID |
| 7 | Messages show raw UUID | Resolve user name from participants list |
| 8 | Search scroll position lost | Use `scrollRestoration` or cache scroll position in session storage |
| 9 | Address mutations use array index as ID | Use address `_id` from API as key instead of array index |
| 10 | Orders capped at 50 | Add pagination (same pattern as seller products fix) |

### MEDIUM Severity (10)

| # | Issue | Fix |
|---|-------|-----|
| 11 | Checkout state lost on refresh | Persist checkout state to sessionStorage |
| 12 | Dark mode resets | (Handled in state-data branch) |
| 13 | Error boundary flash loop | (Handled in state-data branch) |
| 14 | Login toast no action button | Add "Log in" action button to the toast |
| 15 | Flash sale no scroll indicators | Add left/right arrow buttons + dot indicators |
| 16 | Password requirements shown after fail | Show requirements inline below field on focus |
| 17 | Default payment hardcoded to VNPAY | Default to user's last-used method, fall back to COD |
| 18 | Admin/Seller links visible to all | Conditionally render based on user role |
| 19 | Mobile menu missing theme/language | Add theme toggle + language switcher to mobile nav |
| 20 | Notification bell hardcoded Vietnamese | Use i18n translation key |

---

## Category 5: Accessibility (38 items) — `fix/accessibility`

### WCAG 4.1.2 — Name, Role, Value (15 items)

| Scope | Fix |
|-------|-----|
| Logo button | Add `aria-label="VNShop home"` |
| Hamburger menu | Add `aria-label="Open menu"` + `aria-expanded` |
| User dropdown trigger | Add `aria-label="Account menu"` + `aria-expanded` + `aria-haspopup` |
| Social icons | Add `aria-label="{platform}"` to each |
| Cart +/- buttons | Add `aria-label="Increase quantity"` / `"Decrease quantity"` |
| Cart delete buttons | Add `aria-label="Remove {product} from cart"` |
| Checkout address selection | Add `role="radiogroup"` + `role="radio"` + `aria-checked` |
| Checkout shipping selection | Same radiogroup pattern |
| Checkout payment selection | Same radiogroup pattern |
| Product gallery prev/next | Add `aria-label="Previous image"` / `"Next image"` |
| Tab interfaces (all) | Add WAI-ARIA tabs pattern: `role="tablist"`, `role="tab"`, `role="tabpanel"`, `aria-selected` |
| Search input | Ensure `aria-label` or visible label |
| Modal dialogs | Add `role="dialog"` + `aria-modal="true"` + `aria-labelledby` |
| Breadcrumbs | Add `aria-label="Breadcrumb"` + `aria-current="page"` |
| Pagination | Add `aria-label="Pagination"` + `aria-current="page"` |

### WCAG 4.1.3 — Status Messages (8 items)

| Scope | Fix |
|-------|-----|
| Login error messages | Add `role="alert"` |
| Register error messages | Add `role="alert"` |
| Cart update confirmations | Add `aria-live="polite"` |
| Search results count | Add `aria-live="polite"` to results summary |
| Toast notifications | Verify Sonner uses `role="alert"` (it does by default) |
| Form validation errors | Add `role="alert"` + `aria-describedby` linking to input |
| Error boundary message | Add `role="alert"` |
| Order status changes | Add `aria-live="polite"` |

### WCAG 2.1.1 — Keyboard (10 items)

| Scope | Fix |
|-------|-----|
| User dropdown | Add `onKeyDown` for ArrowUp/Down/Escape navigation |
| Add-to-cart button | Remove CSS hiding, ensure focusable (visible on focus too) |
| Search grid cards | Change `div+onClick` to `<a>` or add `tabIndex={0}` + `role="link"` + Enter handler |
| Product image gallery | Add keyboard arrow navigation |
| Quantity stepper | Ensure +/- buttons are `<button>` elements (not divs) |
| Mobile menu | Add focus trap when open |
| Modal dialogs | Add focus trap + return focus on close |
| Checkout steps | Ensure Tab moves through form fields in logical order |
| Filter checkboxes | Ensure all use native `<input type="checkbox">` |
| Dropdown menus | Add Escape to close + focus return |

### WCAG 1.4.3 — Contrast & Visual (5 items)

| Scope | Fix |
|-------|-----|
| Disabled button contrast | Ensure 3:1 minimum against background |
| Placeholder text | Ensure 4.5:1 contrast ratio |
| Focus indicators | Add visible `:focus-visible` ring to all interactive elements |
| Error text color | Verify red text meets 4.5:1 on both light/dark backgrounds |
| Link differentiation | Ensure links are distinguishable (underline or 3:1 contrast vs surrounding text) |

---

## Shared Patterns

### Toast Helper
Create a reusable `comingSoon(feature: string)` utility:
```typescript
import { toast } from 'sonner'
export const comingSoon = (feature: string) => 
  toast.info(`${feature} is coming soon!`)
```

### Auth Guard Helper
Reusable hook for guarding actions:
```typescript
export const useAuthGuard = () => {
  const { user } = useAuth()
  const navigate = useNavigate()
  return (action: () => void) => {
    if (!user) {
      toast.error('Please log in to continue', { action: { label: 'Log in', onClick: () => navigate('/login') } })
      return
    }
    action()
  }
}
```

### Token Refresh Mutex
Single-flight pattern for client.ts:
```typescript
let refreshPromise: Promise<string> | null = null
const refreshToken = async () => {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => { refreshPromise = null })
  }
  return refreshPromise
}
```

---

## Testing Strategy

- Each branch runs existing Vitest unit tests + Playwright E2E before merge
- Critical bugs: add targeted test cases for payment failure path, double-submit, variant passing
- State issues: add tests for race condition scenarios
- Accessibility: run `axe-core` audit via Playwright after a11y branch

## Quality Gates (per branch)

### Code Review
Each branch gets a dedicated code review pass before merge:
- **Correctness:** Does the fix actually resolve the reported issue without introducing regressions?
- **Style consistency:** Does new code match existing patterns (naming, imports, component structure)?
- **DRY/SOLID:** No duplicated logic — shared helpers used where applicable
- **Edge cases:** Error paths, empty states, loading states all handled
- **Performance:** No unnecessary re-renders, no N+1 queries, no unbounded lists

### Spec Compliance Check
After implementation, verify each fix against this spec:
- **Audit item coverage:** Every item listed in the branch's category is addressed (no silent skips)
- **Fix matches spec:** Implementation matches the described approach — deviations documented with rationale
- **No scope creep:** No unrelated changes snuck in
- **Shared patterns used:** `comingSoon()`, `useAuthGuard()`, token mutex pattern used where specified
- **Branch isolation:** Changes don't bleed into other categories' concerns

---

## Out of Scope

- Backend API changes (all fixes are frontend-only)
- New feature implementation (only stubs for dead buttons)
- Performance optimization (separate concern)
- Full WCAG audit certification (requires manual assistive technology testing)
