# VNShop Platform Roadmap: Phases 4-8

**Generated:** 2026-06-04  
**Method:** 7-agent parallel audit (architecture, security, frontend, backend features, infrastructure, testing, developer experience)

## Overall Maturity Scorecard

| Dimension | Score | Grade | Trend |
|-----------|-------|-------|-------|
| Architecture | 5/10 | C | Needs structural work |
| Security | 6/10 | C+ | Strong design, weak secrets mgmt |
| Frontend | 7/10 | B | Solid, SEO gap |
| Backend Features | 7/10 | B | Comprehensive, scaling gaps |
| Infrastructure | 7/10 | B | Well-structured, hardening needed |
| Testing & Quality | 7/10 | B | Good coverage, missing load tests |
| Developer Experience | 7/10 | B | Great docs, tooling gaps |
| **Weighted Average** | **6.6/10** | **B-** | |

---

## TOP 5: Do This Week

| # | Action | Effort | Why |
|---|--------|--------|-----|
| 1 | Rotate all credentials in .env and git history (Stripe, R2, Kafka SASL). Run `git filter-repo` or BFG to purge `kafka_server_jaas.conf` from history. | S | Live keys on disk + in git history. Anyone with repo clone has payment sandbox and object storage access. |
| 2 | Re-enable Kafka SSL hostname verification (`ssl.endpoint.identification.algorithm=https`) across all 8 services. | S | Current config allows MITM on all Kafka traffic including payment and order events. |
| 3 | Remove hardcoded passwords from `docker-compose.yml` and `sentinel.conf` — replace with `${VARIABLE:-}` references to `.env`. | S | Credentials committed in plaintext; blocks any open-source or team sharing of the repo. |
| 4 | Add a `Makefile` with targets: `up`, `down`, `test-<service>`, `seed`, `logs`. | S | 16-service project with no task runner is a daily friction cost for every developer session. |
| 5 | Move infrastructure imports out of the application layer (create `AuditPort`, `MetricsPort`, `OutboxPort` interfaces). | S | Cheapest architectural fix — restores hexagonal dependency rule without restructuring packages. |

---

## What's Already Strong (Preserve)

- **Domain layer purity**: Zero infrastructure imports in the domain ring. Value objects (Money, Address, CouponId) enforce invariants correctly.
- **Outbox pattern**: Production-grade with batched polling, retry, dead-letter, exponential backoff, and trace propagation.
- **Keycloak production realm**: Hardened OAuth2 with PKCE, brute-force protection, short token lifespans.
- **Frontend API client**: Interceptor chain with correlation IDs, Zod validation, cross-tab token refresh, optimistic updates.
- **E2E test suite**: 47 Playwright specs + 6-chapter journey test covering all roles.
- **ArchUnit enforcement**: All 11 Java services validate hexagonal boundaries at build time.
- **CI security scanning**: OWASP dep-check, Trivy, buf breaking detection, path-based filtering.
- **Kafka ACLs**: Per-service SASL credentials with least-privilege topic access.
- **Istio mesh**: STRICT mTLS + deny-all baseline + explicit service-account allow rules.
- **Session handover docs**: 44+ handover files provide full decision audit trail.

---

## Phase 4: Security Hardening & Critical Fixes (Weeks 1-3)

Focus: Eliminate all critical security vulnerabilities and restore architectural invariants.

| # | Item | Effort | Dimension | Description |
|---|------|--------|-----------|-------------|
| 4.1 | Rotate all exposed credentials | S | Security | Rotate Stripe, PayPal, R2, Kafka passwords. Purge git history with BFG. |
| 4.2 | Re-enable Kafka SSL hostname verification | S | Security | Set `ssl.endpoint.identification.algorithm=https` in all service configs. |
| 4.3 | Externalize Docker Compose secrets | S | Infrastructure | Replace hardcoded passwords with `${VAR}` references; document in `.env.example`. |
| 4.4 | Create application-layer port interfaces | S | Architecture | `AuditPort`, `MetricsPort`, `OutboxPort` — remove infra imports from use cases. |
| 4.5 | Fix single NAT Gateway SPOF | M | Infrastructure | Terraform: create one NAT GW per AZ (`count = length(var.azs)`). |
| 4.6 | Enforce MFA authentication flow in Keycloak | M | Security | Add conditional OTP flow bound to `MFA-REQUIRED` role for admin/seller. |
| 4.7 | Add method-level `@PreAuthorize` to downstream services | M | Security | Every sensitive endpoint validates role + resource ownership independently of gateway. |
| 4.8 | Implement External Secrets Operator for K8s | M | Infrastructure | Replace plaintext `secret.yaml` with ESO pulling from AWS Secrets Manager. |
| 4.9 | Add Content-Security-Policy headers | S | Security | Configure CSP in gateway `ServerHttpSecurity` headers builder. |
| 4.10 | Add CSRF protection for cookie-based refresh endpoint | S | Security | Protect `/auth/refresh` that consumes `vnshop_rt` cookie. |

**Exit criteria**: Zero plaintext credentials in repo, all Kafka connections verify hostnames, K8s secrets externalized, method-level auth on all write endpoints.

---

## Phase 5: Saga Completion & Bounded Context Extraction (Weeks 4-7)

Focus: Fix the broken distributed transaction path and begin decomposing the god-service.

| # | Item | Effort | Dimension | Description |
|---|------|--------|-----------|-------------|
| 5.1 | Wire saga compensation to Kafka consumers | L | Architecture | Create consumers for `inventory.released`, `payment.refunded`, `shipping.cancelled`. Emit specific compensation-request events instead of generic `SAGA_COMPENSATING`. Remove inline try/catch from `CreateOrderUseCase`. |
| 5.2 | Extract coupon subdomain from order-service | L | Architecture | Move coupon domain/application/ports to the existing (but currently empty) coupon-service in docker-compose. Wire Kafka events for coupon validation requests. |
| 5.3 | Extract seller-finance subdomain | L | Architecture | `SellerWallet`, `CommissionCalculator`, `Payout`, 5 use cases, 3 repos become a standalone finance-service. |
| 5.4 | Add cart variant/SKU awareness | M | Backend | Add `variantId`/`selectedSku` to cart item model. Distinct line items per variant of same product. |
| 5.5 | Implement refund processing workflow | M | Backend | Refund use case calling Stripe `refunds.create`, PayPal capture refund, VNPay refund API. Emit `payment.refunded` event for notification and saga. |
| 5.6 | Add proper CQRS event-driven projection | M | Architecture | Domain event Kafka consumer triggers `OrderProjector` reactively. Add `QueryBus` abstraction. Remove direct JPA imports from application layer. |
| 5.7 | Add inbound port interfaces for use cases | S | Architecture | Define `CreateOrderPort`, `CancelOrderPort` etc. Controllers depend on interfaces. |

**Exit criteria**: Saga compensation provably reverses inventory+payment+shipping on failure. Order-service reduced from 30+ to ~12 use cases. Cart supports variants.

---

## Phase 6: Search, Performance & Testing Infrastructure (Weeks 8-11)

Focus: Replace JPA search with Elasticsearch, add load testing, and fill integration test gaps.

| # | Item | Effort | Dimension | Description |
|---|------|--------|-----------|-------------|
| 6.1 | Implement Elasticsearch for search service | L | Backend | Replace JPA `LIKE` queries with ES index. Retarget Kafka consumer to project into ES. Add Vietnamese analyzer, fuzzy matching, proper relevance scoring. |
| 6.2 | Add k6 performance test suite | L | Testing | Load scenarios: flash-sale concurrency, checkout saga under load, payment callbacks, search queries. Nightly CI job with trend storage. |
| 6.3 | Add Testcontainers integration tests | L | Testing | `@SpringBootTest` with Testcontainers for Postgres, Kafka, Redis. Verify Flyway migrations, event flows, cache behavior. Separate CI job. |
| 6.4 | Fill test coverage gaps (coupon, cart, search) | M | Testing | Bring coupon-service from 2 tests to full domain+application coverage. Cart-service needs variant logic tests. Search-service needs facet/autocomplete tests. |
| 6.5 | Add Docker Compose resource limits | S | Infrastructure | `deploy.resources.limits` on all app services. Document minimum system requirements. |
| 6.6 | Strengthen Pact contract assertions | S | Testing | Replace `assertNotNull("Pact verified")` with real HTTP calls and DTO deserialization checks. |
| 6.7 | Add mutation testing (PIT + Stryker) | M | Testing | Start with domain/application layers. CI gate on mutation score for critical services. |
| 6.8 | Add Prometheus K8s service discovery | M | Infrastructure | Replace `static_configs` with `kubernetes_sd_configs` + pod annotation relabeling. |

**Exit criteria**: Search handles 10K+ products with sub-200ms autocomplete. Flash-sale proven under 1000 concurrent users. All services have real integration tests against live infra.

---

## Phase 7: Frontend Maturity & Developer Experience (Weeks 12-14)

Focus: SEO, accessibility, and developer workflow improvements.

| # | Item | Effort | Dimension | Description |
|---|------|--------|-----------|-------------|
| 7.1 | Add SSR/SSG for SEO-critical pages | L | Frontend | Migrate product detail + category routes to Next.js or add Vite SSR plugin. At minimum: prerender + meta-tag injection + sitemap.xml. |
| 7.2 | Complete i18n adoption | M | Frontend | Replace all hardcoded Vietnamese strings with `t()` calls. Add missing translation keys to `vi.json`/`en.json`. |
| 7.3 | Add loading skeletons for lazy routes | S | Frontend | Replace "Dang tai..." with skeleton screens matching page layouts. |
| 7.4 | Add aria-live regions for dynamic content | S | Frontend | Cart badge, toasts, WebSocket notifications get `aria-live="polite"`. |
| 7.5 | Add Makefile / task runner | S | DevEx | `make up`, `make test`, `make seed`, `make logs <svc>`, `make debug <svc>`. |
| 7.6 | Add hot-reload for Java services | M | DevEx | `spring-boot-devtools` + volume mounts in a `dev` compose profile. |
| 7.7 | Add lightweight compose profile | M | DevEx | `--profile minimal` runs only gateway + one service + shared infra. Document in CONTRIBUTING.md. |
| 7.8 | Add PR/issue templates | S | DevEx | `.github/PULL_REQUEST_TEMPLATE.md` enforcing bounded context, test evidence, deferred items. |
| 7.9 | Add OpenAPI/Swagger docs | M | DevEx | `springdoc-openapi` on gateway. TypeScript client codegen for frontend. |
| 7.10 | Add devcontainer.json | M | DevEx | Consistent Linux-based environment. Sidesteps OneDrive reparse-point issues. |
| 7.11 | Add bundle size CI gate | S | Frontend | `size-limit` config + CI step failing on budget violations. |
| 7.12 | Add React.StrictMode | S | Frontend | Wrap app root to catch unsafe side effects early. |

**Exit criteria**: Product pages indexable by search engines. Full i18n coverage. Developer can start contributing within 15 minutes using devcontainer or Makefile.

---

## Phase 8: Production Readiness & Operational Excellence (Weeks 15-18)

Focus: Final hardening for production launch.

| # | Item | Effort | Dimension | Description |
|---|------|--------|-----------|-------------|
| 8.1 | Activate email channel (AWS SES) | M | Backend | Remove stub mode. Add SMS adapter (Twilio/Vietnam carrier). Firebase push for mobile. |
| 8.2 | Add database backup automation | M | Infrastructure | K8s CronJob: `pg_dump` per service DB, upload to S3 with 30-day retention. Test restore procedure. |
| 8.3 | Add CD smoke tests | M | Infrastructure | Post-deploy job: wait for rollout, hit health endpoints, run critical-path API calls before marking green. |
| 8.4 | Add delivery confirmation endpoint | S | Backend | Buyer confirms receipt. Triggers seller wallet credit release after hold period. |
| 8.5 | Add product soft-delete | S | Backend | Status `DELETED` + Kafka event to de-index from search. |
| 8.6 | Add guest cart on backend | M | Backend | Session-token keyed cart. Merge into user cart on login. |
| 8.7 | Populate prod Kustomize overlay | M | Infrastructure | Differentiated scaling (gateway/order higher), larger resource requests, tuned PDBs. |
| 8.8 | Add multi-platform Docker builds + image signing | M | Infrastructure | `linux/arm64` for Graviton. `cosign` signing in CD. |
| 8.9 | Add input sanitization layer | M | Security | OWASP Java HTML Sanitizer for user-generated content fields. |
| 8.10 | Add E2E smoke subset to CI | M | Testing | Route-smoke + auth-forms Playwright specs against lightweight compose in CI. |
| 8.11 | Add Java static analysis (SpotBugs/Error Prone) | S | Testing | Maven plugin, fail on high-severity findings. |
| 8.12 | Add IDE debug configurations | S | DevEx | `.vscode/launch.json` with Docker attach configs. JDWP ports in dev profile. |

**Exit criteria**: Platform can handle real buyer traffic with automated backups, monitoring, alerting, and incident response paths. All payment methods have working refund paths. Email/SMS notifications active.

---

## Dependency Graph

```
Phase 4 (Security) ──> Phase 5 (Saga + Extraction)
                   ──> Phase 6 (Search + Testing)
                   ──> Phase 7 (Frontend + DevEx)
                                                   ──> Phase 8 (Prod Readiness)
```

Phases 5, 6, and 7 can run in parallel after Phase 4 completes. Phase 8 depends on all three.

---

## Effort Summary

| Phase | S items | M items | L items | Estimated Weeks |
|-------|---------|---------|---------|-----------------|
| 4 | 5 | 4 | 0 | 3 |
| 5 | 1 | 3 | 3 | 4 |
| 6 | 2 | 3 | 3 | 4 |
| 7 | 5 | 5 | 1 | 3 |
| 8 | 3 | 7 | 0 | 4 |
| **Total** | **16** | **22** | **7** | **18** |

---

## Target Scores After Roadmap Completion

| Dimension | Current | Target | Key Lever |
|-----------|---------|--------|-----------|
| Architecture | 5/10 | 8/10 | Bounded context extraction + saga fix |
| Security | 6/10 | 9/10 | Credential rotation + method-level auth |
| Frontend | 7/10 | 9/10 | SSR + i18n completion |
| Backend Features | 7/10 | 9/10 | Elasticsearch + refunds + notifications |
| Infrastructure | 7/10 | 9/10 | NAT HA + backups + ESO |
| Testing & Quality | 7/10 | 9/10 | Load tests + Testcontainers |
| Developer Experience | 7/10 | 9/10 | Makefile + devcontainer + hot-reload |
| **Weighted Average** | **6.6/10** | **8.9/10** | |
