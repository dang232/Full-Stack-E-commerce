# 06 — Frontend Code Quality / AI Slop

> Dead code, god components, inconsistency, unused dependencies.
> Signs of AI-generated code that was never reviewed or integrated properly.

---

## SLOP-01: Dead Hook — useAuthGuard Never Imported

**File:** `fe/src/app/hooks/use-auth-guard.ts`  
**Lines:** 1-23

**Evidence:** `grep "from.*use-auth-guard"` → zero results across entire codebase.

**What it is:** A hook that checks authentication and redirects. The app uses `RequireAuth` route wrapper instead — this hook was likely generated but never adopted.

**Action:** Delete the file.

---

## SLOP-02: Dead Module — error-parser.ts (136 Lines, Zero Imports)

**File:** `fe/src/app/lib/api/error-parser.ts`  
**Lines:** 1-136

**Evidence:** `grep "from.*error-parser"` → zero results.

**What it is:** Exports `parseApiError`, `getUserFacingMessage`, `getErrorLabel`, and an `ERROR_CODE_LABELS` lookup table. Components use inline `err instanceof ApiError ? err.message : t(...)` instead.

**Action:** Delete the file. If error parsing is needed later, implement it in the ApiError class itself.

---

## SLOP-03: Dead Module — idempotency.ts (Never Used)

**File:** `fe/src/app/lib/api/idempotency.ts`  
**Lines:** 1-5

**Evidence:** Only the co-located test file references it. `CheckoutPage.tsx` imports `v4 as uuidv4` from `uuid` directly.

**What it is:** A wrapper around `uuid.v4()` that adds nothing. The abstraction was created but never adopted.

**Action:** Delete the file and its test. CheckoutPage already uses uuid directly.

---

## SLOP-04: Dead Module — initial-avatar.ts (40 Lines, Zero Imports)

**File:** `fe/src/app/lib/initial-avatar.ts`  
**Lines:** 1-40

**Evidence:** `grep "from.*initial-avatar"` → zero results. ProductPage hardcodes `style={{ background: "#EE4D2D" }}` instead.

**What it is:** Helper functions `initialAvatarColor` and `initialFromName` for generating avatar placeholders. Never connected to any component.

**Action:** Delete. If avatar colors are needed, implement in the Avatar component directly.

---

## SLOP-05: Dead Utility — coming-soon.ts (Never Imported)

**File:** `fe/src/app/lib/ui/coming-soon.ts`  
**Lines:** 1-4

**Evidence:** `grep "from.*coming-soon"` → zero results. Pages use `t('seller.settings.comingSoon')` translation keys instead.

**Action:** Delete.

---

## SLOP-06: Dead Component — EmptyState (49 Lines, Never Rendered)

**File:** `fe/src/app/components/ui/empty-state.tsx`  
**Lines:** 1-49

**Evidence:** `grep "from.*empty-state"` → zero results. Pages that show empty states render inline JSX.

**Action:** Delete, OR actually adopt it in pages that need empty states (orders, wishlist, notifications).

---

## SLOP-07: Dead Component — FormDialog (180 Lines, Hardcoded Vietnamese)

**File:** `fe/src/app/components/form-dialog.tsx`  
**Lines:** 1-180

**Evidence:** `grep "from.*form-dialog"` → zero results. Admin dialogs (CouponDialog, ShipDialog) implement their own modal forms.

**What it is:** A generic form dialog with:
- Hardcoded Vietnamese strings (`"Huỷ"`, `"Đang xử lý..."`) bypassing i18n
- No `role="dialog"` or `aria-modal`
- No focus trap
- Field validation via toast instead of inline errors

**Action:** Delete entirely. The concept is fine but implementation is broken. If a generic form dialog is needed, build from the existing `Modal` component which has proper a11y.

---

## SLOP-08: God Component — ProductPage.tsx (902 Lines)

**File:** `fe/src/app/pages/ProductPage.tsx`  
**Lines:** 122-848 (main function)

**What's wrong:**  
Single component handles: image gallery, color/size selection, cart actions, review submission + draft state, Q&A submission + draft state, recommendations rendering. 8 `useState` hooks.

**Should be:**
```
ProductPage.tsx (orchestrator, ~100 lines)
├── ProductGallery.tsx (~120 lines)
├── VariantSelector.tsx (~80 lines)
├── ProductActions.tsx (add to cart/wishlist, ~60 lines)
├── ReviewSection.tsx (~150 lines)
├── QASection.tsx (~100 lines)
└── Recommendations.tsx (~80 lines)
```

---

## SLOP-09: God Component — CheckoutPage.tsx (634 Lines, 11 useState)

**File:** `fe/src/app/pages/checkout/CheckoutPage.tsx`  
**Lines:** 42-634

**What's wrong:**  
Manages: address selection, shipping options, payment dispatch, coupon validation, session storage, idempotency keys, order placement. 11 `useState` calls in one function.

**Should be:**  
Extract a `useCheckout()` hook for state + logic, keep the component for rendering only. Or split into step components that the page orchestrates.

---

## SLOP-10: DesignSystemPage Exposed in Production (1253 Lines)

**File:** `fe/src/app/routes.ts`  
**Line:** 127  
**Component:** `fe/src/app/pages/DesignSystemPage.tsx` (1253 lines)

**What's wrong:**  
A developer reference page (color swatches, component samples) is in the production route tree. Any user can navigate to `/design-system`.

**Fix:**
```tsx
// Only register in dev:
...(import.meta.env.DEV ? [{ path: "design-system", element: ... }] : []),
```

---

## SLOP-11: 70+ Inline Hardcoded Colors Despite theme.ts Existing

**File:** `fe/src/app/lib/ui/theme.ts` defines `colors.primary = "#00BFB3"`, etc.  
**But:** Only `modal.tsx` imports it. 70+ other files hardcode the same hex values.

**Examples:**
- `OrdersPage.tsx:29-50` — multiple `#00BFB3`, `#FF6200`
- `ProductPage.tsx:93` — `style={{ background: "#EE4D2D" }}`
- `PaymentReturnPage.tsx:125-228` — 6+ hardcoded colors
- `MessagesPage.tsx` — `style={{ background: "#FF6200" }}`
- `CouponDialog.tsx` — `style={{ background: "#6366F1" }}`

**Fix:**  
Replace all with Tailwind CSS variables or theme.ts imports. A re-skin should require editing 1 file, not 70.

---

## SLOP-12: Unused Dependencies in package.json

**Installed but never imported in source:**
- `react-hook-form` (7.55.0) — zero usage
- `@hookform/resolvers` (3.9.1) — zero usage
- `zustand` (5.0.2) — minimal/no usage found

**Action:** Either wire them up (recommended for RHF) or remove to reduce bundle size.

---

## SLOP-13: Memory Leak — StripeForm Polling Never Cancelled on Unmount

**File:** `fe/src/app/components/checkout/StripePaymentSection.tsx`  
**Lines:** 98-121

**What's wrong:**  
After `stripe.confirmPayment` succeeds, `tick()` recurses via `window.setTimeout`. No cleanup on unmount — if user navigates away, `setPolling(false)` fires on an unmounted component.

**Compare:** `VietQrPaymentSection.tsx` correctly uses a `cancelled` flag with cleanup.

**Fix:**  
Add cleanup ref:
```tsx
const cancelledRef = useRef(false);
useEffect(() => () => { cancelledRef.current = true; }, []);

// In tick():
if (cancelledRef.current) return;
```

---

## SLOP-14: Stale Closure — WebSocket Reconnect Timer Not Cleared

**File:** `fe/src/app/hooks/use-messaging-socket.ts`  
**Lines:** 84-98

**What's wrong:**  
`scheduleReconnect` calls `window.setTimeout(connect, delay)` but cleanup (lines 103-114) only sets `stoppedRef.current = true` without `clearTimeout`. If component unmounts between schedule and fire, `connect()` creates a WebSocket against a stale token.

**Fix:**
```tsx
const timerRef = useRef<number>();
// In scheduleReconnect:
timerRef.current = window.setTimeout(connect, delay);
// In cleanup:
clearTimeout(timerRef.current);
```
