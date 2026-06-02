# System Architecture

## Service Map

| Service | Port | Tech | Purpose |
|---------|------|------|---------|
| api-gateway | 8080 | Spring Cloud Gateway (WebFlux) | Single entry point, JWT validation, CORS, rate limiting, routing |
| frontend | 3000 | React 18 + Vite + TanStack Query | SPA with Tabler Icons, dark mode, i18n |
| user-service | 8081 | Spring Boot 3 | Auth (Keycloak ROPC), profiles, addresses, wishlist, seller management |
| product-service | 8082 | Spring Boot 3 | Catalog, images (S3/R2), reviews, questions, categories |
| order-service | 8083 | Spring Boot 3 | Checkout, order saga, returns, disputes, invoices, coupons |
| payment-service | 8092 | Spring Boot 3 | PayPal, Stripe, VietQR, MoMo, VNPay gateways, FX conversion |
| inventory-service | 8084 | Spring Boot 3 | Stock reservations, Kafka consumer |
| cart-service | 8085 | Spring Boot 3 | Cart CRUD, Redis-backed |
| search-service | 8086 | Spring Boot 3 | Elasticsearch indexing, product search |
| shipping-service | 8087 | Spring Boot 3 | Shipping adapters (stub GHN/GHTK), tracking |
| notification-service | 8091 | NestJS (Node.js) | WebSocket real-time delivery, MongoDB, 12 event types, threading |
| messaging-service | 8093 | NestJS (Node.js) | Chat/messaging WebSocket |
| seller-finance-service | 8094 | Spring Boot 3 | Seller wallets, commission calculation, payouts |
| coupon-service | 8095 | Spring Boot 3 | Coupon CRUD, validation, usage tracking |
| recommendations-service | 8088 | Spring Boot 3 | Collaborative filtering, trending, personalized feeds |
| monitoring-service-v2 | 8096 | NestJS (Node.js) | TimescaleDB metrics, Prometheus export |
| configuration-service | 8097 | Spring Boot 3 | Feature flags, app config |

## Infrastructure

| Component | Port | Purpose |
|-----------|------|---------|
| PostgreSQL (×7) | 5432-5439 | Relational storage per service (user, product, order, payment, inventory, search, shipping) |
| MongoDB | 27017 | Notification storage (TTL indexes, $facet aggregation) |
| Redis | 6379 | Caching, rate limiting, cart, dedup, socket registry, offline queue |
| Kafka + KRaft | 9092 | Async event bus (order.*, payment.*, product.*, review.*, return.*, payout.*) |
| Elasticsearch | 9200 | Full-text product search |
| Keycloak | 8085/9090 | OAuth2/OIDC identity provider, realm roles (BUYER, SELLER, ADMIN) |
| MinIO / Cloudflare R2 | 9000 | Object storage for avatars, product images, review images |
| TimescaleDB | 5440 | Time-series metrics for monitoring |

## Communication Patterns

```
┌─────────────────────────────────────────────────────────────────┐
│  Frontend (React SPA)                                           │
│  - REST via api-gateway (JWT Bearer)                            │
│  - WebSocket: /ws/notifications (socket.io, JWT handshake)      │
│  - WebSocket: /ws/messaging (socket.io)                         │
└──────────────────────┬──────────────────────────────────────────┘
                       │ HTTP/WS
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  API Gateway (Spring Cloud Gateway)                             │
│  - JWT validation (Keycloak JWKS)                               │
│  - Role extraction (realm_access.roles → ROLE_*)                │
│  - Rate limiting (Redis-backed RequestRateLimiter)              │
│  - Circuit breaker (Resilience4j)                               │
│  - CORS enforcement                                             │
│  - WebSocket upgrade routing                                    │
└──────────────────────┬──────────────────────────────────────────┘
                       │ HTTP (service-to-service)
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Microservices (each validates JWT independently)               │
│  - Sync: REST calls through gateway or direct (internal)        │
│  - Async: Kafka events for cross-service state propagation      │
│  - Outbox: order-service uses outbox pattern for money-path     │
└──────────────────────┬──────────────────────────────────────────┘
                       │ Kafka
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Kafka Topics                                                   │
│  - order.created, order.paid, order.cancelled, order.shipped    │
│  - payment.completed, payment.refund_requested, payment.refunded│
│  - product.published, product.updated, product.deleted          │
│  - return.requested, review.replied, payout.completed           │
└─────────────────────────────────────────────────────────────────┘
```

## DDD Hexagonal Architecture

Used in notification-service (NestJS) and order-service (Spring Boot):

```
src/
├── domain/              # Zero framework imports — pure TypeScript/Java
│   ├── model/           # Aggregate roots, value objects, enums
│   ├── event/           # Domain events (published via EventEmitter/Spring Events)
│   ├── port/inbound/    # Use case interfaces (driving ports)
│   └── port/outbound/   # Repository, external service interfaces (driven ports)
├── application/         # Use case orchestration
│   ├── command/         # Write operations (SendNotification, Checkout, etc.)
│   ├── query/           # Read operations (FindNotifications, etc.)
│   └── event-handler/   # Domain event listeners
└── infrastructure/      # Framework adapters
    ├── persistence/     # JPA/Mongoose repositories implementing ports
    ├── messaging/       # Kafka consumers/producers
    ├── web/             # REST controllers + DTOs
    └── config/          # Spring/NestJS configuration
```

Key principles:
- Domain has no framework dependencies
- Ports define contracts, adapters implement them
- Use cases orchestrate domain logic
- Infrastructure is swappable (MinIO → R2 just by changing adapter config)

## Deployment Topology (Docker Compose)

```
docker compose up                    → Infrastructure only (Kafka, Redis, Postgres, etc.)
docker compose --profile apps up     → Infrastructure + all application services
docker compose --profile apps --profile legacy up → + legacy compatibility services
```

Profile separation ensures infra services (always needed) start independently of app services.
