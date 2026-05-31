# UI/UX Audit Fixes — Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Fix all 111 UI/UX audit items across 5 phased branches.

**Architecture:** Phased branch strategy — Phase 1 (critical-bugs + state-data parallel), Phase 2 (dead-buttons + ux-antipatterns parallel), Phase 3 (accessibility). Shared utilities created first, then category-specific fixes.

**Tech Stack:** React 18, TypeScript, Zustand, TanStack React Query, Tailwind CSS, Sonner (toasts), React Router 7, i18next

---

## Phase 1A: Critical Bugs — `fix/critical-bugs`

### Task 1: Create shared utilities (auth guard + coming-soon toast)

**Files:**
- Create: `fe/src/app/lib/ui/coming-soon.ts`
- Create: `fe/src/app/hooks/use-auth-guard.ts`

- [ ] **Step 1: Create coming-soon toast utility**

```typescript
// fe/src/app/lib/ui/coming-soon.ts
import { toast } from "sonner";

export const comingSoon = (feature: string) =>
  toast.info(`${feature} is coming soon!`);
```

- [ ] **Step 2: Create auth guard hook**

```typescript
// fe/src/app/hooks/use-auth-guard.ts
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { useAuth } from "./use-auth";

export function useAuthGuard() {
  const { authenticated } = useAuth();
  const navigate = useNavigate();

  return (action: () => void) => {
    if (!authenticated) {
      toast.error("Please log in to continue", {
        action: { label: "Log in", onClick: () => navigate("/login") },
      });
      return;
    }
    action();
  };
}
```

- [ ] **Step 3: Commit**

```
git add fe/src/app/lib/ui/coming-soon.ts fe/src/app/hooks/use-auth-guard.ts
git commit -m "feat: add shared auth-guard hook and coming-soon toast utility"
```

---

### Task 2: Fix payment failure showing success (Critical #1)

**Files:**
- Modify: `fe/src/app/pages/checkout/CheckoutPage.tsx`

- [ ] **Step 1: Fix VNPAY/MOMO payment failure fallthrough**

In `handlePlaceOrder`, the catch block after `vnpayCreate`/`momoCreate` falls through to `setStep("success")`. Change it to show an error state instead:

```typescript
// Replace the catch block (around line 293-300):
} catch (err) {
  toast.error(
    err instanceof ApiError
      ? t("checkout.payment.initFailedPrefix", { message: err.message })
      : t("checkout.payment.initFailedShort"),
  );
  // Do NOT fall through to success — order exists but payment failed.
  // Navigate to orders page so buyer can retry payment.
  setPlacedOrderId(order.id);
  navigate(`/orders/${order.id}`);
  return;
}
```

- [ ] **Step 2: Commit**

```
git commit -am "fix: prevent payment failure from showing success screen"
```

---

### Task 3: Add double-click guard on Place Order (Critical #2)

**Files:**
- Modify: `fe/src/app/pages/checkout/CheckoutPage.tsx`

- [ ] **Step 1: Add submission guard using existing isProcessing state**

The `isProcessing` state already exists but the button may still be clickable. Add early return:

```typescript
// At the top of handlePlaceOrder, add:
if (isProcessing) return;
```

Also ensure the button in CheckoutSummary is disabled during processing (verify it passes `isProcessing` as `disabled`).

- [ ] **Step 2: Commit**

```
git commit -am "fix: add double-click guard to place order button"
```

---

### Task 4: Pass selected variant to addToCart (Critical #3)

**Files:**
- Modify: `fe/src/app/pages/ProductPage.tsx`

- [ ] **Step 1: Update handleAddToCart to include variant info**

```typescript
// Replace handleAddToCart (around line 187):
const handleAddToCart = () => addToCart(product, quantity, { color: selectedColor, size: selectedSize });

// Replace handleBuyNow:
const handleBuyNow = () => {
  addToCart(product, quantity, { color: selectedColor, size: selectedSize });
  void navigate("/checkout");
};
```

- [ ] **Step 2: Update addToCart in vnshop-context to accept variants**

Check if `addToCart` in vnshop-context.tsx accepts a variant param. If not, add optional third param and pass it through to the cart API.

- [ ] **Step 3: Commit**

```
git commit -am "fix: pass selected color/size variant to addToCart"
```
