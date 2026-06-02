# Security Audit Results

Full OWASP Top 10 pentest-style audit conducted 2026-06-02 (pt48).

## Summary

| Severity | Count | Fixed | Remaining |
|----------|-------|-------|-----------|
| CRITICAL | 2 | 1 | 1 |
| HIGH | 12 | 9 | 3 |
| MEDIUM | 24 | 8 | 16 |
| LOW | 12 | 0 | 12 |
| **Total** | **50** | **18** | **32** |

---

## CRITICAL Findings

### C-01: Product-service permits all requests without authentication ✅ FIXED
- **File:** `services/product-service/.../SecurityConfig.java`
- **Issue:** `.anyRequest().permitAll()` on ALL endpoints including seller mutations
- **Impact:** Anyone reaching port 8082 directly can create/modify products
- **Fix applied:** Require auth for non-GET endpoints; permit public reads only

### C-02: Kafka consumers trust payment events without verification ⚠️ OPEN
- **File:** `services/order-service/.../PaymentCompletedListener.java`
- **Issue:** Forged `payment.completed` message marks orders as paid + credits wallets
- **Impact:** Financial fraud — mark orders paid without payment
- **Fix needed:** Kafka ACLs + message signing (HMAC envelope) or cross-service verification

---

## HIGH Findings

### H-01: No seller role enforcement at gateway ✅ FIXED
- **File:** `services/api-gateway/.../SecurityConfig.java`
- **Fix:** Added `.pathMatchers("/seller/**", "/sellers/me/**").hasRole("SELLER")`

### H-02: Question answer endpoint — no ownership check ✅ FIXED (Phase 2)
- **File:** `services/product-service/.../QuestionController.java`
- **Fix:** Verify caller is the product's seller before allowing answer

### H-03: Invoice generation — missing ownership check ✅ FIXED (Phase 2)
- **File:** `services/order-service/.../InvoiceController.java`
- **Fix:** Verify caller is buyer of order or seller of sub-order

### H-04: Database passwords hardcoded in application.yml ✅ FIXED (Phase 2)
- **Files:** All 10 service application.yml files
- **Fix:** `password: ${DB_PASSWORD:vnshop}` — env-var with local-dev default

### H-05: Keycloak admin client secret hardcoded ✅ FIXED
- **File:** `services/user-service/.../application.yml`
- **Fix:** Removed default value — `${KEYCLOAK_ADMIN_CLIENT_SECRET:}`

### H-06: No rate limiting on /auth/** ✅ FIXED
- **File:** `services/api-gateway/.../RouteConfig.java`
- **Fix:** Applied `rateLimited()` filter to auth route

### H-07: Default credentials in docker-compose.yml ⚠️ PARTIALLY FIXED
- **Fix applied:** Redis auth added; DB passwords externalized
- **Remaining:** Keycloak admin/admin, MongoDB vnshop/vnshop123

### H-08: Actuator endpoints exposed without auth ⚠️ OPEN
- **File:** `services/user-service/.../SecurityConfig.java`
- **Fix needed:** Restrict to /actuator/health only, or bind to separate management port

### H-09: No account lockout ⚠️ OPEN
- **Fix needed:** Redis-backed failed-login counter with progressive delays

### H-10: Seller-finance Kafka listener trusts event data ⚠️ OPEN
- **File:** `services/seller-finance-service/.../OrderCreatedFinanceListener.java`
- **Fix needed:** Cross-verify amounts with order-service before crediting

### H-11: Weak password policy (min 3 chars) ✅ FIXED
- **File:** `services/user-service/.../RegisterRequest.java`
- **Fix:** min=8, pattern requires uppercase+lowercase+digit

### H-12: No JWT failure logging ⚠️ OPEN
- **Fix needed:** Custom AuthenticationEntryPoint/AccessDeniedHandler across all services

---

## MEDIUM Findings (selected)

| # | Title | Status |
|---|-------|--------|
| M-01 | Review vote manipulation (no per-user dedup) | ✅ Fixed (Phase 2) |
| M-02 | Order-service no ADMIN role on /admin/** | ✅ Fixed |
| M-03 | Seller-finance no ADMIN role on /admin/** | ✅ Fixed |
| M-04 | PayPal capture cross-validation missing | ✅ Fixed (Phase 2) |
| M-05 | Redis deployed without authentication | ✅ Fixed (Phase 2) |
| M-06 | Swagger UI accessible in production | ✅ Fixed |
| M-07 | Unbounded pagination (max 2000) | ✅ Fixed (capped at 50) |
| M-08 | Verbose error messages leak internals | ✅ Fixed |
| M-09 | Missing security headers | ✅ Fixed (X-Frame, Content-Type) |
| M-10 | CSRF on cookie-based auth endpoints | ⚠️ Open |
| M-11 | No HTTPS enforcement / HSTS | ⚠️ Open |
| M-12 | Elasticsearch security disabled | ⚠️ Open |
| M-13 | SSRF via FX/SePay configurable URLs | ⚠️ Open |
| M-14 | Kafka log injection via notification body | ⚠️ Open |
| M-15 | Dispute endpoint allows both buyer+seller args | ⚠️ Open |
| M-16 | No audit trail for payment refunds | ⚠️ Open |
| M-17 | ROPC auth flow (deprecated in OAuth 2.1) | ⚠️ Open |
| M-18 | Gateway actuator exposes route topology | ⚠️ Open |
| M-19 | No rate limiting on search endpoint | ⚠️ Open |
| M-20 | MongoDB creds in .env.example | ⚠️ Open |

---

## LOW Findings (all open — tech debt)

| # | Title |
|---|-------|
| L-01 | JwtPrincipalUtil.currentSellerId() == currentUserId() (no role validation) |
| L-02 | Kafka PLAINTEXT protocol (no SASL/TLS) |
| L-03 | Coupon code logged without CRLF sanitization |
| L-04 | User email logged in password-reset error path (PII) |
| L-05 | NoSQL injection risk — threadId passed directly to MongoDB filter |
| L-06 | Dynamic JPQL WHERE via string concatenation (code smell) |
| L-07 | Keycloak admin secret (duplicate of H-05) |
| L-08 | Redis no auth (duplicate of M-05) |
| L-09 | Kafka consumer StringDeserializer without schema enforcement |
| L-10 | No logback.xml — missing log pattern sanitization |
| L-11 | Keycloak role assignment failure silently swallowed |
| L-12 | PII in logs without structured logging |

---

## Recommended Fix Order (remaining items)

**Phase 3 — Short-term (1-2 blocks):**
1. Account lockout (Redis counter, 5 attempts → 15 min lock)
2. JWT failure logging (custom EntryPoint across all services)
3. Elasticsearch xpack.security enable + password
4. Actuator restriction (health only, or management port)
5. Gateway actuator — remove `gateway` from exposure

**Phase 4 — Architectural (multi-block):**
6. Kafka ACLs + signed event envelope (HMAC-SHA256)
7. ROPC → Authorization Code + PKCE migration
8. Structured logging (logback-spring.xml with JSON encoder)
9. CSRF selective enablement for cookie auth endpoints
10. SSRF allowlists for external URL configuration

---

## Systemic Theme

The architecture relied on the API gateway as a **single security perimeter**. Every downstream service used `permitAll()` or `authenticated()` without role checks — a collapse-on-bypass architecture. Phase 1+2 fixes added defense-in-depth at the service level, but Kafka event trust remains the largest systemic gap.
