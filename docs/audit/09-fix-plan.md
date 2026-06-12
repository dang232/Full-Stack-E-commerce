# 09 — Fix Plan (Prioritized Waves)

> Grouped so you can tackle them in parallel without breaking things.
> Each wave is independent — complete one before starting the next.

---

## Wave 1: Stop the Bleeding (Auth Bypasses)

**Time estimate:** 2-3 days  
**Risk if skipped:** Active exploitation possible TODAY

| # | Task | Service | Ref |
|---|------|---------|-----|
| 1.1 | Strip inbound `x-user-id` header at gateway | api-gateway | SEC-02 |
| 1.2 | Fix X-Forwarded-For trust (use remote address) | api-gateway | SEC-01 |
| 1.3 | Add SecurityConfig + `@PreAuthorize` to coupon-service | coupon-service | SEC-03 |
| 1.4 | Add SecurityConfig + `@PreAuthorize` to shipping-service | shipping-service | SEC-04 |
| 1.5 | Add auth guards to configuration-service | configuration-service | SEC-05 |
| 1.6 | Add ownership checks to invoice-service | invoice-service | SEC-06 |
| 1.7 | Fix `permitAll()` in inventory SecurityConfig | inventory-service | SEC-07 |
| 1.8 | Add gRPC auth interceptor to payment-service | payment-service | SEC-08 |
| 1.9 | Fix PayPal webhook signature verification | payment-service | SEC-09 |
| 1.10 | Reject SePay webhooks when secret not configured | payment-service | SEC-10 |
| 1.11 | Add `@PreAuthorize` to AdminReviewController | product-service | SEC-11 |
| 1.12 | Add `@PreAuthorize` to QuestionController | product-service | SEC-12 |
| 1.13 | Add SELLER role check to seller-finance-service | seller-finance-service | SEC-13 |
| 1.14 | Sanitize Handlebars templates (XSS) | notification-service | SEC-14 |
| 1.15 | Validate `next` param against allowlist (open redirect) | frontend | SEC-16 |
| 1.16 | Fix GDPR `hasRole('admin')` → `hasRole('ADMIN')` | user-service | SEC-19 |
| 1.17 | Enforce banned user state (disable in Keycloak) | user-service | SEC-18 |

**How to parallelize:**  
Each service is independent. Assign one dev per service. All changes are additive (adding security annotations) — low risk of breaking existing functionality.

**Testing:**  
After each fix, verify:
- Unauthenticated request → 401
- Wrong role → 403
- Correct role → 200

---

## Wave 2: Money Protection (Business Logic)

**Time estimate:** 3-4 days  
**Risk if skipped:** Revenue loss, fraud

| # | Task | Service | Ref |
|---|------|---------|-----|
| 2.1 | Add per-user coupon usage tracking | coupon-service | BIZ-01 |
| 2.2 | Cap discount to order amount (`max(0, total - discount)`) | coupon-service | BIZ-02 |
| 2.3 | Add coupon release to saga compensation | order-service | BIZ-03 |
| 2.4 | Implement verified purchase check (call order-service) | product-service | BIZ-04 |
| 2.5 | Add duplicate review prevention (unique constraint) | product-service | BIZ-05 |
| 2.6 | Filter DELETED products from catalog queries | product-service | BIZ-06 |
| 2.7 | Add `paymentMethod` field to CheckoutRequest | order-service | BIZ-07 |
| 2.8 | Guard order cancellation by fulfillment status | order-service | BIZ-08 |
| 2.9 | Prevent duplicate returns and disputes | order-service | BIZ-09 |
| 2.10 | Fix order number generation (use DB sequence or ULID) | order-service | BIZ-10 |
| 2.11 | Add idempotency guard to finance Kafka listener | seller-finance-service | BIZ-11 |
| 2.12 | Cap flash sale quantity per buyer | inventory-service | BIZ-12 |
| 2.13 | Fix flash sale role to BUYER | inventory-service | BIZ-13 |

**Dependencies:**  
- 2.3 depends on 2.1 (need the usage tracking table first)
- 2.7 is independent
- 2.10 requires a migration — deploy during low traffic

---

## Wave 3: Race Conditions (Concurrency Fixes)

**Time estimate:** 4-5 days  
**Risk if skipped:** Data corruption under load, double charges

| # | Task | Service | Ref |
|---|------|---------|-----|
| 3.1 | Route saga compensation through outbox (not direct Kafka) | order-service | RACE-01 |
| 3.2 | Persist idempotency key BEFORE calling payment gateway | payment-service | RACE-02 |
| 3.3 | Make payment idempotency check atomic (INSERT ON CONFLICT) | payment-service | RACE-03 |
| 3.4 | Fix order idempotency filter (single atomic setIfAbsent) | order-service | RACE-04 |
| 3.5 | Add `@Version` to Order entity | order-service | RACE-05 |
| 3.6 | Add `@Transactional` + `SELECT FOR UPDATE` to payout | seller-finance-service | RACE-06 |
| 3.7 | Fix Lua script: check buyer membership BEFORE decrement | inventory-service | RACE-07 |
| 3.8 | Replace keyspace notifications with ZSET-based expiry | inventory-service | RACE-08 |
| 3.9 | Save order BEFORE requesting payment (reorder saga steps) | order-service | RACE-09 |
| 3.10 | Add Redis WATCH/MULTI to cart operations | cart-service | RACE-10 |
| 3.11 | Make coupon validate+apply atomic (single SQL UPDATE) | coupon-service | RACE-11 |

**High risk changes:**  
- 3.1, 3.9 change saga flow — requires full E2E regression test
- 3.7 modifies Lua script — test with concurrent load before deploy
- 3.5 may cause `OptimisticLockException` — add retry logic

---

## Wave 4: Input Validation (Backend DTOs)

**Time estimate:** 2-3 days  
**Risk if skipped:** 500 errors, garbage data, injection

| # | Task | Service | Ref |
|---|------|---------|-----|
| 4.1 | Add `@Valid` + Bean Validation to all coupon DTOs | coupon-service | VAL-01 |
| 4.2 | Enable `ValidationPipe` + class-validator in notification-service | notification-service | VAL-02 |
| 4.3 | Install class-validator + add to cart-service DTOs | cart-service | VAL-03 |
| 4.4 | Add validation to AddressRequest + BuyerProfileRequest | user-service | VAL-04, VAL-05 |
| 4.5 | Add validation to ProductRequest + `@Valid` on controller | product-service | VAL-06 |
| 4.6 | Replace raw Map with typed DTO for admin changeStatus | order-service | VAL-07 |
| 4.7 | Add `@Min(1)` to ParcelDto dimensions | shipping-service | VAL-08 |
| 4.8 | Validate tracking code format (alphanumeric only) | shipping-service | VAL-09 |
| 4.9 | Add `@Size(max=200)` to search query + sanitize ES chars | search-service | VAL-10, VAL-11 |
| 4.10 | Add `max-in-memory-size` to gateway config | api-gateway | VAL-13 |
| 4.11 | Validate taxCode format (VN 10/13 digit) | invoice-service | VAL-14 |
| 4.12 | Validate Redis key inputs (UUID format check) | inventory-service | VAL-12 |

**How to parallelize:**  
Every service is independent. Each dev adds validation to their service. Changes are backward-compatible (rejecting invalid input that shouldn't have worked anyway).

---

## Wave 5: Frontend Validation (Wire Up Zod + RHF)

**Time estimate:** 3-4 days  
**Risk if skipped:** Bad UX, garbage input reaches server

| # | Task | File | Ref |
|---|------|------|-----|
| 5.1 | Create shared schemas: `lib/schemas/auth.ts`, `address.ts`, `product.ts` | new files | — |
| 5.2 | Wire Zod + RHF to RegisterPage (phone validation!) | RegisterPage.tsx | FE-VAL-01 |
| 5.3 | Wire Zod + RHF to LoginPage | LoginPage.tsx | FE-VAL-02 |
| 5.4 | Wire Zod + RHF to PasswordResetPage | PasswordResetPage.tsx | FE-VAL-03 |
| 5.5 | Fix seller product modal: `type="number"` + inline errors | seller-product-modal.tsx | FE-VAL-04 |
| 5.6 | Wire Zod + RHF to CheckoutAddressStep | CheckoutAddressStep.tsx | FE-VAL-05 |
| 5.7 | Add min-length + rating range to review form | ProductPage.tsx | FE-VAL-06 |
| 5.8 | Add maxUses + validUntil inputs to CouponDialog | CouponDialog.tsx | FE-VAL-08 |
| 5.9 | Fix idempotency key (stable hash, not array ref) | CheckoutPage.tsx | SLOP-13 |

**Pattern to follow for each form:**
```tsx
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { registerSchema } from '@/lib/schemas/auth';

const { register, handleSubmit, formState: { errors } } = useForm({
  resolver: zodResolver(registerSchema),
  mode: 'onBlur', // validate on blur for real-time feedback
});
```

---

## Wave 6: Code Cleanup (Dead Code + Refactor)

**Time estimate:** 2 days  
**Risk if skipped:** None immediate — maintainability debt

| # | Task | Ref |
|---|------|-----|
| 6.1 | Delete `use-auth-guard.ts` | SLOP-01 |
| 6.2 | Delete `error-parser.ts` | SLOP-02 |
| 6.3 | Delete `idempotency.ts` | SLOP-03 |
| 6.4 | Delete `initial-avatar.ts` | SLOP-04 |
| 6.5 | Delete `coming-soon.ts` | SLOP-05 |
| 6.6 | Delete `empty-state.tsx` | SLOP-06 |
| 6.7 | Delete `form-dialog.tsx` | SLOP-07 |
| 6.8 | Gate DesignSystemPage behind `import.meta.env.DEV` | SLOP-10 |
| 6.9 | Replace 70+ inline colors with Tailwind/theme vars | SLOP-11 |
| 6.10 | Fix StripeForm polling cleanup (add cancelledRef) | SLOP-13 |
| 6.11 | Fix WebSocket reconnect timer cleanup | SLOP-14 |
| 6.12 | Split ProductPage into sub-components | SLOP-08 |
| 6.13 | Extract `useCheckout()` hook from CheckoutPage | SLOP-09 |

---

## Wave 7: Infrastructure Hardening

**Time estimate:** 2-3 days  
**Risk if skipped:** Cascading failures under load

| # | Task | Ref |
|---|------|-----|
| 7.1 | Add pagination to all list endpoints (9 services) | MISS-01 |
| 7.2 | Add JPA auditing (createdBy/updatedBy) to 3 services | MISS-02 |
| 7.3 | Add `@RetryableTopic` + DLT to all Kafka consumers | MISS-11 |
| 7.4 | Add HTTP client timeouts to invoice, cart, shipping | MISS-12 |
| 7.5 | Add processed_events cleanup scheduled job | MISS-13 |
| 7.6 | Switch coupon-service from ddl-auto:update to Flyway | MISS-10 |
| 7.7 | Add WebSocket connection limit per user | MISS-08 |
| 7.8 | Add message sending rate limit | MISS-07 |
| 7.9 | Implement notification retry (replace no-op) | MISS-04 |

---

## Summary

| Wave | Focus | Findings Fixed | Days |
|------|-------|---------------|------|
| 1 | Auth bypasses | 17 | 2-3 |
| 2 | Business logic | 13 | 3-4 |
| 3 | Race conditions | 11 | 4-5 |
| 4 | Backend validation | 12 | 2-3 |
| 5 | Frontend validation | 9 | 3-4 |
| 6 | Code cleanup | 13 | 2 |
| 7 | Infrastructure | 9 | 2-3 |
| **Total** | | **84 tasks** | **~18-24 days** |

Remaining medium-severity findings (accessibility, minor UX) can be addressed in a Wave 8 after the critical path is clear.
