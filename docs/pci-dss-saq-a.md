# PCI-DSS SAQ-A Self-Assessment

**Version:** 1.0
**Date:** 2026-06-04
**Assessor:** VNShop Engineering Team
**Next Review:** 2027-06-04

---

## 1. Scope Determination

### Applicability
VNShop qualifies for **SAQ-A** because:
- All payment processing is **fully outsourced** to PCI-DSS compliant payment gateways (PayPal, Stripe)
- No cardholder data (CHD) is stored, processed, or transmitted by VNShop systems
- No payment page elements are served from VNShop infrastructure
- Customers are redirected to the payment gateway for all card interactions

### Cardholder Data Flow

```
┌──────────┐     ┌──────────────┐     ┌─────────────────┐
│  Browser │────>│  VNShop API  │────>│ Payment Gateway  │
│          │     │  (order ref  │     │ (PayPal/Stripe)  │
│          │     │   only)      │     │                  │
│          │<────│              │<────│ (redirect URL)   │
│          │─────────────────────────>│ (card entry)     │
│          │<─────────────────────────│ (confirmation)   │
└──────────┘     └──────────────┘     └─────────────────┘
                       │
                       │ webhook callback
                       │ (payment ID + status only,
                       │  NO card data)
                       ▼
                 ┌──────────────┐
                 │ payment-svc  │
                 │ (stores ref  │
                 │  ID only)    │
                 └──────────────┘
```

**Data VNShop stores:** Payment reference ID, transaction status, amount, currency, timestamp.
**Data VNShop NEVER receives:** Card number (PAN), CVV, expiration date, cardholder name for payment purposes.

---

## 2. Controls Mapping

| SAQ-A Requirement | VNShop Implementation | Evidence |
|---|---|---|
| 2.1 — Change vendor defaults | All default passwords changed (Kafka, Redis, Postgres, Keycloak) | `docker-compose.yml` env vars, externalized secrets |
| 6.5 — Address common vulnerabilities | OWASP dependency scanning in CI, input validation, parameterized queries | `.github/workflows/ci.yml` (dependency-check + Trivy) |
| 8.1 — Unique IDs for all users | Keycloak manages all identities with UUID-based user IDs | `infra/keycloak/vnshop-realm.json` |
| 8.2 — Authentication mechanisms | Password policy enforced (8+ chars, not username), MFA for admin/seller | Keycloak realm config |
| 9.x — Physical access | N/A — cloud-hosted, no physical infrastructure managed | AWS shared responsibility model |
| 11.2 — Vulnerability scans | Trivy container scanning, OWASP dependency-check on every PR | CI pipeline evidence |
| 12.8 — Service provider management | PayPal and Stripe are PCI-DSS Level 1 certified | Gateway compliance certificates |

---

## 3. Network Security

| Control | Implementation |
|---|---|
| Encryption in transit | TLS via Ingress (cert-manager), SASL_SSL on Kafka, Redis password auth |
| Network segmentation | Kubernetes NetworkPolicy restricts pod-to-pod communication |
| No CHD on internal network | Payment gateway handles all card data externally |
| Firewall/Ingress | Nginx Ingress Controller with rate limiting + CORS policies |

---

## 4. Access Control

| Control | Implementation |
|---|---|
| Role-based access | Keycloak RBAC: admin, seller, buyer roles |
| MFA for privileged access | TOTP required for admin and seller roles |
| Principle of least privilege | Kafka ACLs per-service, K8s RBAC, database per-service credentials |
| Audit logging | All authentication events logged, API audit trail via structured logging |

---

## 5. Logging & Monitoring

| Control | Implementation |
|---|---|
| Audit trail | Structured JSON logs with userId, action, timestamp, traceId |
| Centralized logging | Loki + Promtail aggregation |
| Alerting | Prometheus alerts for anomalous patterns (high error rates, auth failures) |
| Log retention | 7 days hot (Loki), archive policy TBD |

---

## 6. Responsibility Matrix

| Responsibility | VNShop | Payment Gateway |
|---|---|---|
| Card data storage | ✗ Never | ✓ PCI-DSS Level 1 |
| Payment page serving | ✗ Redirect only | ✓ Hosted payment page |
| Tokenization | ✗ | ✓ |
| Fraud detection | Partial (order velocity) | ✓ Primary |
| Webhook security | ✓ Signature validation | ✓ Signs callbacks |
| Refund processing | ✓ Initiates via API | ✓ Executes |
| Compliance certification | SAQ-A (this doc) | Full ROC |

---

## 7. Annual Review Process

1. **Q1:** Review gateway compliance certificates (PayPal, Stripe publish annually)
2. **Q2:** Self-assessment review — update this document, verify no scope changes
3. **Q3:** Penetration test (external vendor) — confirm no CHD exposure
4. **Q4:** Update controls mapping if infrastructure changes occurred

---

## 8. Attestation

This self-assessment confirms that VNShop:
- Does not store, process, or transmit cardholder data
- Has fully outsourced payment processing to PCI-DSS Level 1 compliant providers
- Maintains appropriate security controls for its SAQ-A scope
- Will review this assessment annually and upon significant infrastructure changes
