# 05 — Validation Gaps (Frontend)

> Forms that accept garbage input, show no errors, or silently fail.
> The root cause: Zod + React Hook Form are in package.json but NEVER wired up.

---

## Pattern: What SHOULD Exist vs What DOES Exist

```
✅ Correct pattern (not used anywhere):
   Zod schema → React Hook Form resolver → per-field errors → real-time feedback

❌ Current pattern (used everywhere):
   useState per field → manual if/else in onSubmit → toast or silent failure
```

**Installed but unused:**
- `zod` (4.4.3) — only used for API RESPONSE parsing, never form INPUT
- `react-hook-form` (7.55.0) — zero imports found in any component
- `@hookform/resolvers` (3.9.1) — zero imports found

---

## FE-VAL-01: Registration — Phone Accepts Any Text

**File:** `fe/src/app/pages/RegisterPage.tsx`  
**Lines:** 208-217

**What's wrong:**  
```tsx
<input
  type="tel"
  value={phone}
  onChange={(e) => setPhone(e.target.value)}
  placeholder="+84..."
/>
```
No validation in `handleSubmit` either — phone is entirely unchecked. User types "hello world" and it submits.

**What should happen:**  
- Real-time: reject non-digit chars on keypress (except + and spaces)
- On blur: validate against VN phone pattern
- Show inline error under the field

**Fix:**
```tsx
const PHONE_RE = /^(\+84|0)\d{9,10}$/;

// In Zod schema:
const registerSchema = z.object({
  phone: z.string()
    .optional()
    .refine(v => !v || PHONE_RE.test(v), "Invalid VN phone number"),
  // ...
});
```

---

## FE-VAL-02: Login — Zero Client Validation

**File:** `fe/src/app/pages/LoginPage.tsx`  
**Lines:** 36-43, 128

**What's wrong:**  
Form uses `noValidate` (disables browser validation) but `handleSubmit` only checks `if (submitting) return`. An empty string or single space is trimmed and sent to the server.

**What should happen:**  
- Email format validated before submit
- Password minimum length checked
- Inline error shown, not just server rejection

**Fix:**
```tsx
const loginSchema = z.object({
  identifier: z.string().email("Enter a valid email"),
  password: z.string().min(8, "Password must be at least 8 characters"),
});
```

---

## FE-VAL-03: Password Reset — Any Character Submits

**File:** `fe/src/app/pages/PasswordResetPage.tsx`  
**Lines:** 33-59, 98

**What's wrong:**  
Button disabled only when `email.trim().length === 0`. A single character "x" triggers a POST to `/auth/password-reset-request`. No email format check.

**Fix:**
```tsx
const isValidEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
// Disable button until valid:
<button disabled={!isValidEmail || submitting}>
```

---

## FE-VAL-04: Seller Product Modal — Price/Stock Accept Text, Parse to 0

**File:** `fe/src/app/components/seller-product-modal.tsx`  
**Lines:** 37-38, 474-519

**What's wrong:**  
Price and stock inputs use `type="text"` with `inputMode="numeric"`. User types "abc" — field shows "abc". On submit, `parsePriceInput` strips non-digits: `Number(raw.replace(/\D/g, "")) || 0`. So "abc" → 0.

The `if (priceNum <= 0)` check catches it, but only as a generic toast. User has no per-field feedback and the input still shows "abc".

**Also:** Negative stock check `parsePriceInput(stock) < 0` on line 292 is dead code — the regex strips `-` so result is always >= 0.

**Fix:**  
Use `type="number"` with `min` attribute, or filter on keypress:
```tsx
<input
  type="number"
  min="1"
  step="1000"
  value={price}
  onChange={(e) => setPrice(e.target.value)}
  onBlur={() => {
    const num = Number(price);
    if (isNaN(num) || num <= 0) setErrors(prev => ({...prev, price: "Enter a valid price"}));
  }}
/>
{errors.price && <p className="text-red-500 text-xs mt-1">{errors.price}</p>}
```

---

## FE-VAL-05: Checkout Address — Only Checks Emptiness

**File:** `fe/src/app/pages/checkout/CheckoutAddressStep.tsx`  
**Lines:** 58-62

**What's wrong:**  
```tsx
if (!form.street || !form.district || !form.city) return;
```
That's the entire validation. No phone format, no postal code, no min length. Street of "a" is valid.

**What should happen:**  
- Street: min 10 chars
- Phone: VN format validated
- District/City: min 2 chars
- Show per-field errors

**Fix:**
```tsx
const addressSchema = z.object({
  street: z.string().min(10, "Street address too short"),
  district: z.string().min(2),
  city: z.string().min(2),
  phone: z.string().regex(/^(\+84|0)\d{9,10}$/, "Invalid phone"),
  postalCode: z.string().regex(/^\d{5,6}$/, "Invalid postal code").optional(),
});
```

---

## FE-VAL-06: Review Form — Single Character Allowed, No Rating Validation

**File:** `fe/src/app/pages/ProductPage.tsx`  
**Lines:** 679-680

**What's wrong:**  
Submit disabled only when `reviewDraft.comment.trim().length === 0`. A review of "a" with default rating 5 is submittable. No check that rating is within 1-5 range.

**Fix:**
```tsx
const canSubmit = reviewDraft.comment.trim().length >= 10
  && reviewDraft.rating >= 1
  && reviewDraft.rating <= 5;
```

---

## FE-VAL-07: Checkout Address — No Visible Error Messages

**File:** `fe/src/app/pages/checkout/CheckoutAddressStep.tsx`  
**Lines:** 58-62

**What's wrong:**  
When required fields are empty, the form silently does nothing (returns early). No errors state, no per-field error rendering, no toast. User confused why clicking "Save" has no effect.

**Fix:**  
Add error state and render under each field:
```tsx
const [errors, setErrors] = useState<Record<string, string>>({});

const validate = () => {
  const e: Record<string, string> = {};
  if (!form.street) e.street = t('checkout.address.required');
  if (!form.city) e.city = t('checkout.address.required');
  setErrors(e);
  return Object.keys(e).length === 0;
};
```

---

## FE-VAL-08: CouponDialog — Missing maxUses and validUntil Inputs

**File:** `fe/src/app/pages/admin/CouponDialog.tsx`  
**Lines:** 74-83

**What's wrong:**  
Always sends hardcoded `maxUses: 1000` and `validUntil: 30 days from now`. No input fields for these parameters. Admins cannot configure usage limits or expiry dates.

**Fix:**  
Add two form fields:
```tsx
<input type="number" min={1} label="Max uses" value={maxUses} />
<input type="datetime-local" label="Valid until" value={validUntil} />
```
