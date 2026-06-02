# Session handover — 2026-06-02 (pt48: Security Hardening + Preferences Enforcement)

**Last commit (HEAD):** `f86e0198` (`security: externalize DB passwords + add Redis authentication`)

**Gates:**
- notification-service jest: 89 / 89 (16 suites).
- FE vitest: 169 / 169 (28 suites).
- FE typecheck: 0 errors (verified pt47).
- order-service mvn: 144 / 144.
- user-service mvn: 141 / 141.
- payment-service mvn: 89 / 89.
- product-service mvn: 33 / 33.
- seller-finance-service mvn: 20 / 20.

## Commits this session

| # | SHA | Summary |
|---|-----|---------|
| 1 | `29ae137b` | Notification preferences enforcement + F-05 profile mismatch fix |
| 2 | `57a9bab2` | Security Phase 1: product-service auth, gateway seller role, admin roles, password policy, rate limiting, headers, error handler, swagger, pagination, keycloak secret |
| 3 | `005bcd2` | Security Phase 2: invoice/question/PayPal ownership checks, review vote dedup + docs/architect/ |
| 4 | `f86e0198` | Externalize DB passwords (10 services) + Redis authentication |

## What this block was

Security hardening session driven by a full OWASP Top 10 audit (50 findings). Also completed notification preferences enforcement and created comprehensive architecture documentation.

### Notification Preferences Enforcement
- `SendNotificationUseCase`: checks `isChannelEnabled()` before persisting; short-circuits if all channels disabled
- `NotificationCreatedHandler`: checks IN_APP preference before WebSocket delivery
- `SocketioNotificationGateway`: filters catch-up notifications against preferences on reconnect
- `NotificationCreatedEvent`: carries `suppressedChannels` for downstream awareness

### OWASP Security Audit — 50 Findings
- 2 CRITICAL, 12 HIGH, 24 MEDIUM, 12 LOW
- 18 fixed this session, 32 remaining (mostly architectural or multi-block effort)

### Security Fixes Applied

**Phase 1 (Critical + Quick Wins):**
- product-service SecurityConfig: require auth for mutations (was permitAll)
- api-gateway: SELLER role on /seller/**, /sellers/me/**
- order-service + seller-finance: ADMIN role on /admin/** (defense-in-depth)
- seller-finance: Keycloak realm role converter for proper ROLE_ extraction
- Password policy: min 8 + uppercase/lowercase/digit
- Rate limiting on /auth/** (brute-force protection)
- Security headers: X-Frame-Options DENY, X-Content-Type-Options nosniff
- Generic error messages in catch-all handler
- Swagger disabled by default (SWAGGER_ENABLED env)
- Max page size 50 (search, product, order)
- Keycloak admin secret: removed hardcoded default

**Phase 2 (Access Control + Secrets):**
- InvoiceController: buyer/seller ownership check before generation
- QuestionController: restrict answer to product's seller
- PaymentController: cross-validate paypalOrderId matches stored reference
- ReviewController + VoteHelpfulUseCase: per-user vote dedup (V5 migration)
- DB passwords externalized: `${DB_PASSWORD:vnshop}` across 10 services
- Redis: `--requirepass` in docker-compose + password in 5 service configs
- .env.example: documented DB_PASSWORD and REDIS_PASSWORD

### Architecture Documentation (docs/architect/)
- README.md — index and orientation
- SYSTEM-ARCHITECTURE.md — service map, infra, DDD, deployment
- WHAT-WE-DID.md — chronological work log pt27→pt48
- SECURITY-AUDIT.md — 50 findings with status and fix plan
- TRADE-OFFS.md — 15 architectural decisions
- BUGS-AND-GOTCHAS.md — runtime surprises and known issues
- TIPS-AND-TRICKS.md — patterns for all tech in the stack

### Other
- F-05 fix: monitoring-service-v2 added to `profiles: ["apps"]`
- R2 swap verified: already complete from pt46, no changes needed
- .gitignore: excluded .playwright-mcp/ artifacts

## Gotchas this block

**#132. Spring Security role conversion for Keycloak.** Default OAuth2 JWT converter only maps `scope` claims. Keycloak puts roles in `realm_access.roles`. Need a custom `JwtAuthenticationConverter` with `KeycloakRealmRoleConverter` to prefix as `ROLE_*`. Without this, `hasRole("ADMIN")` never matches even with correct JWT.

**#133. Test JWT must include realm_access claims.** Mock JWTs in @SpringBootTest must include `Map.of("realm_access", Map.of("roles", List.of("ADMIN")))` for role-based security rules to pass. Simple `Map.of("sub", userId)` gets 403 on role-protected endpoints.

**#134. InvoiceController constructor change breaks tests.** Adding `ViewOrderUseCase` dependency for ownership check requires updating all test module setups that instantiate the controller directly.

## Open threads for the next session

**Still open (security audit remaining — see docs/architect/SECURITY-AUDIT.md):**
1. **Kafka event trust (CRITICAL)** — forged payment.completed marks orders paid. Needs ACLs + signed envelopes.
2. **Account lockout** — Redis-backed counter after failed login attempts.
3. **JWT failure logging** — custom AuthenticationEntryPoint across all services.
4. **Elasticsearch xpack.security** — currently disabled, exposed on host port.
5. **ROPC → Authorization Code + PKCE migration** — large effort, blocks MFA.
6. **Structured logging (logback-spring.xml)** — prevents CRLF log injection.
7. **SSRF allowlists** — FX/SePay URL validation at startup.
8. **Kafka schema registry** — prevent malformed event processing.

**Non-security remaining:**
9. **PayPal sandbox smoke test** — creds in .env, code complete. Needs `docker compose --profile apps up` and browser walkthrough.
10. **Email channel adapter** — SES integration for notifications.

**Recommended pick for pt49:** PayPal sandbox smoke test (validate the full payment flow in browser). Then Kafka ACLs/signing (close the last CRITICAL finding).

## How to resume

1. **Verify HEAD.** `git log --oneline -3` shows the 4 commits above.
2. **Smoke gates:**
   - `cd services/notification-service; npx jest --no-coverage --forceExit` → 89 / 89.
   - `cd fe; npx vitest run` → 169 / 169.
   - `cd services/order-service; .\mvnw.cmd test -q` → 144 / 144.
   - `cd services/user-service; .\mvnw.cmd test -q` → 141 / 141.
3. **Docker verification:** Ensure `.env` has `REDIS_PASSWORD=vnshop123` and `DB_PASSWORD=vnshop`.
4. **Full stack:** `docker compose --profile apps up -d` → all services connect with new auth.
5. **Architect docs:** `docs/architect/` has full project state documentation.
