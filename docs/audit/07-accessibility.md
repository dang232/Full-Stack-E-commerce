# 07 — Accessibility Issues

> WCAG failures that make the app unusable for screen reader users,
> keyboard-only users, or users with visual impairments.

---

## A11Y-01: FormDialog — No Dialog Semantics or Focus Trap

**File:** `fe/src/app/components/form-dialog.tsx`  
**Lines:** 81-170

**What's wrong:**  
- Renders a backdrop div with `role="button"` instead of `role="dialog" aria-modal="true"`
- No focus trap — Tab key escapes to background page
- Screen readers announce it as a button, not a dialog container
- The existing `Modal` component (`ui/modal.tsx`) has proper semantics — FormDialog ignores it

**Impact:** Screen reader users cannot identify the modal. Keyboard users Tab into invisible background elements.

**Fix:** Delete FormDialog (it's dead code — see SLOP-07). If revived, extend `Modal` component which already has focus trap.

---

## A11Y-02: ConfirmDialog — No Focus Trap

**File:** `fe/src/app/components/ui/confirm-dialog.tsx`  
**Lines:** 41-96

**What's wrong:**  
Only handles Escape via `useEscapeKey`. Does not trap Tab/Shift+Tab. Focus escapes past the last button into background page. Does not restore focus to trigger element on close.

**Fix:**  
Reuse the focus trap logic from `modal.tsx` (lines 84-104):
```tsx
// Add to ConfirmDialog:
const trapRef = useFocusTrap(); // extract modal's trap into a shared hook
```

---

## A11Y-03: Validation Errors — Toast Only, Not Announced

**File:** `fe/src/app/components/form-dialog.tsx`  
**Lines:** 58-73

**What's wrong:**  
Validation failures call `toast.error()`. Toasts are ephemeral (disappear after seconds) and not reliably announced by screen readers. No `aria-describedby` linking errors to inputs, no `role="alert"`.

**Impact:** Screen reader user submits form, nothing happens. They don't know what's wrong.

**Fix:**  
Show persistent inline errors with `role="alert"`:
```tsx
{error && (
  <p id={`${field.key}-error`} role="alert" className="text-red-500 text-xs mt-1">
    {error}
  </p>
)}
<input aria-describedby={error ? `${field.key}-error` : undefined} ... />
```

---

## A11Y-04: Register Page — Errors Not Linked to Inputs

**File:** `fe/src/app/pages/RegisterPage.tsx`  
**Lines:** 131-283

**What's wrong:**  
firstName, lastName, password, and confirm fields display inline errors but lack `aria-describedby`. Only the email field (line 189) has it. Error paragraphs lack `id` attributes.

**Fix:**  
```tsx
<input id="firstName" aria-describedby={errors.firstName ? "firstName-error" : undefined} />
{errors.firstName && (
  <p id="firstName-error" role="alert" className="text-red-500 text-xs">
    {errors.firstName}
  </p>
)}
```

---

## A11Y-05: NotificationBell — Missing role="menuitem" and Keyboard Nav

**File:** `fe/src/app/components/notification-bell.tsx`  
**Lines:** 141-238

**What's wrong:**  
Dropdown panel has `role="menu"` but notification items (rendered as `<button>`) lack `role="menuitem"`. No ArrowUp/ArrowDown keyboard navigation (unlike the user menu in `navbar.tsx` which implements it properly).

**Fix:**  
```tsx
<button role="menuitem" tabIndex={-1} onKeyDown={handleArrowNav}>
  {notification.title}
</button>
```
Plus implement `handleArrowNav` that moves focus between items on ArrowUp/Down.

---

## A11Y-06: Loading States — No aria-live Announcements

**Files:**  
- `CheckoutPage.tsx:347-351`
- `CartPage.tsx:121`
- `WishlistPage.tsx`
- `MessagesPage.tsx`

**What's wrong:**  
Plain text loading indicators without `aria-live` or `aria-busy`. Screen reader users navigating to these pages get no announcement that content is loading.

**Fix:**  
```tsx
<div aria-live="polite" aria-busy={isLoading}>
  {isLoading ? <Spinner aria-label={t('common.loading')} /> : children}
</div>
```

---

## A11Y-07: Hardcoded Colors Fail Dark Mode Contrast

**Files:**  
- `facet-list.tsx:45-53` — `color: '#4b5563'` (gray-600 on dark background fails 4.5:1)
- `AdminPage.tsx:113-118` — `#6366F1` and `#6b7280`
- `NotificationBell.tsx` — `#00BFB3` on card background

**Impact:** WCAG AA requires 4.5:1 contrast for normal text. These hardcoded colors don't adapt to dark theme backgrounds.

**Fix:** Replace with Tailwind classes that respect dark mode:
```tsx
// Before:
style={{ color: '#4b5563' }}
// After:
className="text-gray-600 dark:text-gray-300"
```

---

## A11Y-08: Disabled Checkout Button — No Explanation for Screen Readers

**File:** `fe/src/app/pages/CartPage.tsx`  
**Lines:** 391-398

**What's wrong:**  
Checkout button is `disabled={!authenticated}` with `aria-disabled` but no `aria-describedby` explaining WHY. The visual guest banner above is not programmatically linked.

**Fix:**  
```tsx
<button
  aria-disabled={!authenticated}
  aria-describedby={!authenticated ? "login-required-hint" : undefined}
>
  {t('cart.checkout')}
</button>
{!authenticated && (
  <p id="login-required-hint" className="sr-only">
    {t('cart.loginRequired')}
  </p>
)}
```

---

## A11Y-09: ProductCard — role="link" on div Without Proper Semantics

**File:** `fe/src/app/pages/HomePage.tsx`  
**Lines:** 75-85

**What's wrong:**  
`role="link"` on a `motion.div` with `tabIndex={0}` and `onClick`. ARIA spec says `role="link"` should be activatable via Enter (not Space). Implementation handles both, confusing AT users. Also hides rich content (price, rating) behind flat `aria-label`.

**Fix:**  
Use an actual `<a>` tag wrapping the card:
```tsx
<a href={`/products/${product.id}`} className="block">
  <motion.div> ... card content ... </motion.div>
</a>
```
