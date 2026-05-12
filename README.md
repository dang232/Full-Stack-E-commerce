# VNShop — Multi-Seller Retail Marketplace Backend

A polyglot microservices e-commerce platform demonstrating **DDD**, **CQRS**, **Hexagonal Architecture**, and **Event-Driven Saga** at 10K concurrent user scale. Inspired by Shopee, Lazada, Amazon Lightning Deals, and Alibaba Double 11.

> **Status**: Active development. [See full status document](.sisyphus/STATUS.md)

---

## Quick Links

| Document | Description |
|----------|-------------|
| [Architecture](.sisyphus/ARCHITECTURE.md) | Full technical architecture and design decisions |
| [Project Status](.sisyphus/STATUS.md) | What's done, what's missing, what's next |
| [Requirements Audit](.sisyphus/analysis/requirements-audit-v2.md) | 119-feature completion matrix |
| [Bounded Contexts](.sisyphus/analysis/bounded-contexts/) | Domain models per service |

---

## Architecture

```
                    ┌──────────────────────────────┐
                    │         CDN / Edge            │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │  Spring Cloud Gateway (8080)  │
                    │  OAuth2/Keycloak, Rate Limit,  │
                    │  Circuit Breaker, Waiting Room │
                    └──────┬───────┬───────────────┘
                           │       │
              ┌────────────┼───────┼──────────────┐
              │            │       │              │
    ┌─────────▼────┐ ┌────▼───┐ ┌─▼──────────┐   │
    │ Keycloak 26  │ │User    │ │Product Svc │   │
    │ Auth/OIDC    │ │(8081)  │ │(8082)      │   │
    └──────────────┘ └───┬────┘ └─────┬──────┘   │
                         │            │          │
                         │     ┌──────▼──────┐   │
                         │     │ Order Svc   │   │
                         │     │ (8091)      │   │
                         │     │ +Coupons    │   │
                         │     │ +Finance    │   │
                         │     └──────┬──────┘   │
                         │            │          │
                  ┌──────▼──────┐ ┌──▼──────────▼┐
                  │ Cart Svc    │ │ Kafka Cluster │
                  │ (8084)      │ │ order/notif   │
                  │ Redis-only  │ └───────┬───────┘
                  └─────────────┘         │
                    ┌─────────────────────┼─────────────────────┐
                    │                     │                     │
           ┌────────▼──────┐    ┌────────▼──────┐    ┌────────▼──────┐
           │ Search Svc    │    │ Notification  │    │ Payment Svc   │
           │ (8086)        │    │ Svc (8087)    │    │ (8092)        │
           │ Elasticsearch │    │ Email+SMS+Push│    │ VNPAY/MoMo    │
           └───────────────┘    └───────────────┘    └───────────────┘
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Transactional services** | Java 25 LTS + Spring Boot 4.0.6 (8 services) |
| **I/O-bound services** | Node.js 24 LTS + NestJS 11 (3 services) |
| **Auth** | Keycloak 26.6 (OIDC/OAuth2, JWT) |
| **Database** | PostgreSQL 17.9 (per-service schema isolation) |
| **Cache** | Redis 8.6 (cart, inventory, rate limiting) |
| **Message bus** | Kafka 4.2.0 (KRaft) |
| **Search** | Elasticsearch 9.4.0 |
| **Observability** | Jaeger (tracing), Prometheus (metrics), Alertmanager |
| **Container** | Docker, Kubernetes manifests in `infra/k8s/` |
| **Build** | Maven 3.9 (Java), npm (Node) |
| **Test coverage** | JaCoCo 90% (Java), Jest 90% (NestJS) |

---

## Quick Start

```bash
# Start infrastructure + all services
docker compose --profile apps up -d

# Health check
curl http://localhost:8080/actuator/health
```

**Default credentials**: Keycloak admin `admin` / `admin` at http://localhost:8085

**Ports**:
| Port | Service |
|------|---------|
| 8080 | API Gateway |
| 8081 | User Service |
| 8082 | Product Service |
| 8083 | Inventory Service |
| 8084 | Cart Service |
| 8085 | Keycloak |
| 8086 | Search Service |
| 8087 | Notification Service |
| 8091 | Order Service |
| 8092 | Payment Service |
| 8093 | Shipping Service |
| 5432 | PostgreSQL |
| 6379 | Redis |
| 9092 | Kafka |
| 9200 | Elasticsearch |
| 16686 | Jaeger UI |

---

## Project Structure

```
services/
  api-gateway/           # Spring Cloud Gateway (8080)
  user-service/          # Auth + profiles + seller registration (8081)
  product-service/       # Products + categories + reviews (8082)
  inventory-service/     # Stock + flash sale with Redis Lua (8083)
  cart-service/          # Redis-only shopping cart (8084) — NestJS
  search-service/        # Elasticsearch product search (8086)
  notification-service/  # Email/SMS/Push via Kafka (8087) — NestJS
  order-service/         # Orders + checkout + coupons + finance (8091)
  payment-service/       # VNPAY/MoMo payment processing (8092)
  shipping-service/      # GHN/GHTK carrier integration (8093)
  coupon-service/        # ⚰️ DEPRECATED — migrated to order-service
  review-service/        # ⚰️ DEPRECATED — migrated to product-service
  seller-finance-service/# ⚰️ DEPRECATED — migrated to order/user-service
infra/                   # Kubernetes manifests, Prometheus, Alertmanager, DB init
.sisyphus/               # Architecture docs, analysis, evidence, plans
```

---

## Architecture Patterns

### Hexagonal (Ports & Adapters)
```
┌──────────────────────────────────────┐
│              DOMAIN                   │
│  ZERO framework imports              │
│  Pure business logic                 │
│  ┌──────────┐     ┌──────────┐      │
│  │ Entities │     │  Ports   │      │
│  │  VO/DTO  │◄───►│ (out/in) │      │
│  └──────────┘     └──────────┘      │
└──────────────────┬───────────────────┘
                   │
    ┌──────────────┼──────────────┐
    │              │              │
┌───▼───┐    ┌────▼────┐    ┌───▼────┐
│  Web  │    │   JPA   │    │ Kafka  │
│  REST │    │  Repos  │    │  Event │
└───────┘    └─────────┘    └────────┘
```

### CQRS
Order and Product services separate write (transactional PostgreSQL) from read (Redis/Elasticsearch cache) paths.

### Event-Driven Saga
Checkout and fulfillment orchestrated via Kafka events — no blocking distributed transactions.

---

## Coding Conventions

| Rule | Requirement |
|------|------------|
| **DTOs** | Must be Java `record` — no class-based Command/Request/Response/Query |
| **JPA entities** | Must have Lombok `@Getter` + `@Setter` |
| **Repositories** | Two-layer: `*JpaRepository implements Port` wrapping `*SpringDataRepository extends JpaRepository` |
| **Domain purity** | Domain has ZERO imports from Spring, Jakarta, JPA, or any framework |
| **Test coverage** | 90% line+branch (JaCoCo) for Java; 90% all metrics (Jest) for NestJS |
| **Ports** | Domain defines interfaces; infrastructure implements adapters |

---

## How to Develop

1. **Read the architecture**: `.sisyphus/ARCHITECTURE.md`
2. **Check status**: `.sisyphus/STATUS.md` for what's done and what's next
3. **Understand the domain**: `.sisyphus/analysis/bounded-contexts/` for domain models
4. **Follow the pattern**: Domain first (zero framework imports), then infrastructure adapters
5. **Test-first**: All changes must pass `.\mvnw.cmd test` with 90% coverage

### Run a single service locally
```bash
cd services/order-service
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

### Run tests
```bash
# Java
cd services/order-service && .\mvnw.cmd test

# NestJS
cd services/cart-service && npm test -- --coverage
```

---

## Feature Coverage

**71% complete** — 85 of 119 features implemented. 8 partial, 26 missing.

Top priorities: Product Variants, Guest Cart, Return/Refund Flow, Admin Dashboard.

[Full feature matrix →](.sisyphus/analysis/requirements-audit-v2.md)

---

## License

MIT
