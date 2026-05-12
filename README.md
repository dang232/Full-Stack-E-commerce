# VNShop — Multi-Seller Retail Marketplace Backend

A polyglot microservices e-commerce platform demonstrating DDD, CQRS, Hexagonal Architecture, and Event-Driven Saga at 10K concurrent user scale.

VNShop is a portfolio backend for a Vietnamese multi-seller marketplace inspired by Shopee, Lazada, and Tiki. It helps new developers, reviewers, and contributors understand the system quickly, run it locally, and make changes without breaking architectural boundaries.

## Quick Links

| Resource | Use it for |
| --- | --- |
| [Architecture doc](.sisyphus/ARCHITECTURE.md) | Full system design, bounded contexts, API conventions, and setup notes |
| [API docs](.sisyphus/ARCHITECTURE.md#7-api-design-conventions) | API envelope, error handling, DTO rules, and testing strategy |
| [Status doc](.sisyphus/STATUS.md) | Current service health, completed work, pending gaps, and coverage status |
| [Docker Compose](docker-compose.yml) | Local infrastructure and app service definitions |

## Architecture Overview

```text
                    +------------------------------+
                    |         CDN / Edge            |
                    |   Static assets, bot filtering |
                    +--------------+---------------+
                                   |
                    +--------------v---------------+
                    |  Spring Cloud Gateway        |
                    |  (Spring Boot + WebFlux)     |
                    |  SSL, LB, OAuth2/Keycloak,   |
                    |  Rate Limiting, Circuit Brkr, |
                    |  Waiting Room, Corr. ID       |
                    +------+-------+---------------+
                           |       |
              +------------+-------+--------------+
              |            |       |              |
    +---------v----+ +----v---+ +-v----------+   |
    | Keycloak 26  | | User   | |Product Svc |   |
    | Auth Server  | | Svc    | |Spring Boot |   |
    | OIDC / OAuth | |Spring  | |CQRS        |   |
    | JWT issuance | |Boot    | |PG + Redis  |   |
    +--------------+ +---+----+ +-----+------+   |
                         |            |          |
                         |     +------v------+   |
                         |     | Order Svc   |   |
                         |     | Spring Boot |   |
                         |     | CQRS + Saga |   |
                         |     +------+------+
                         |            |
                  +------v------+ +---v----------+
                  | Cart Svc    | | Kafka Cluster |
                  | NestJS      | | order.* events|
                  | Redis-only  | | notif.* topics|
                  +-------------+ +-------+-------+
                                          |
                    +---------------------+---------------------+
                    |                     |                     |
           +--------v------+    +--------v------+    +--------v------+
           | Search Svc    |    | Notification  |    | Payment Svc   |
           | NestJS        |    |    Svc        |    | NestJS        |
           | Elasticsearch |    | NestJS + Kafka|    | VNPAY/MoMo    |
           | Export engine |    | Email+SMS+Push|    | (planned)     |
           +---------------+    +---------------+    +---------------+
```

### Service-to-Port Table

| Service | Port | Runtime profile | Notes |
| --- | ---: | --- | --- |
| api-gateway | 8080 | apps | Public entry point, routing, OAuth2, rate limits |
| user-service | 8081 | apps | Users, profiles, sellers, identity-linked data |
| product-service | 8082 | apps | Catalog, variants, reviews, questions |
| inventory-service | 8083 | apps | Stock, reservations, flash sale inventory |
| cart-service | 8084 | apps | Redis-backed buyer cart |
| Keycloak | 8085 | default | Local OIDC/OAuth2 identity provider |
| search-service | 8086 | apps | Search read side and Elasticsearch indexing |
| notification-service | 8087 | apps | Email, SMS, push, in-app notifications |
| order-service | 8091 | apps | Orders, checkout, coupons, finance, saga orchestration |
| payment-service | 8092 | apps | Payment gateway integration |
| shipping-service | 8093 | apps | Carrier integration and shipment tracking |
| PostgreSQL | 5432 | default | Main relational database |
| Redis | 6379 | default | Cache, cart storage, rate-limit support |
| Kafka | 9092 | default | Event backbone |
| Elasticsearch | 9200 | default | Search index |
| Jaeger UI | 16686 | default | Local trace viewer |
| Alertmanager | 9093 | default | Local alert routing |

## Tech Stack

| Area | Technology |
| --- | --- |
| Java services | Java 25 LTS, Spring Boot 4.0.6, Spring Cloud Gateway, Maven 3.9 |
| Node services | Node.js 24 LTS, NestJS 11 |
| Identity | Keycloak 26.6, OIDC, OAuth2, JWT |
| Data stores | PostgreSQL 17.9, Redis 8.6, Elasticsearch 9.4.0 |
| Messaging | Kafka 4.2.0 design target, local Compose image `confluentinc/cp-kafka:8.2.0` |
| Quality | JaCoCo for Java coverage, Jest for NestJS coverage |
| Runtime | Docker, Docker Compose |

Current app profile includes 8 Spring Boot services and 2 NestJS services. The architecture document still describes some planned NestJS placements, but the status doc marks search-service and payment-service as Java/NestJS mismatches to resolve later.

## Quick Start

Start infrastructure and active application services:

```bash
docker compose --profile apps up -d
```

This starts the default infrastructure plus every service in the `apps` profile. First startup builds local service images, so it can take several minutes.

Local access points:

| URL | What opens |
| --- | --- |
| `http://localhost:8080` | API Gateway |
| `http://localhost:8085` | Keycloak admin console |
| `http://localhost:9200` | Elasticsearch |
| `http://localhost:16686` | Jaeger UI |

Default local credentials:

| System | Username | Password |
| --- | --- | --- |
| Keycloak admin | `admin` | `admin` |
| PostgreSQL | `vnshop` | `vnshop123` |

Common service ports:

```text
8080 gateway
8081 user-service
8082 product-service
8083 inventory-service
8084 cart-service
8085 keycloak
8086 search-service
8087 notification-service
8091 order-service
8092 payment-service
8093 shipping-service
```

Stop the stack:

```bash
docker compose --profile apps down
```

## Service Map

| Service | Port | Tech | Owns |
| --- | ---: | --- | --- |
| api-gateway | 8080 | Spring Boot, Spring Cloud Gateway | Edge routing, OAuth2 resource server setup, rate limiting, circuit breaking, request correlation |
| user-service | 8081 | Spring Boot | Buyer profiles, addresses, seller profile, seller approval, user-side finance data |
| product-service | 8082 | Spring Boot | Seller-owned catalog, categories, variants, product images, reviews, questions, product read models |
| inventory-service | 8083 | Spring Boot | Stock levels, reservations, flash sale inventory coordination |
| cart-service | 8084 | NestJS | Redis cart snapshots, buyer cart operations, cart health endpoint |
| search-service | 8086 | Spring Boot | Search indexing, faceted search, query read side, export support |
| notification-service | 8087 | NestJS | Kafka-driven email, SMS, push, and in-app notification workflows |
| order-service | 8091 | Spring Boot | Orders, checkout, sub-orders, coupons, commission, payouts, saga coordination |
| payment-service | 8092 | Spring Boot | Payment intents, VNPay/MoMo integration surface, payment status reconciliation |
| shipping-service | 8093 | Spring Boot | Shipment creation, carrier integration, tracking updates |

Deprecated standalone services are kept out of the app profile. Coupon logic moved into order-service. Review and question logic moved into product-service. Seller finance moved into order-service and user-service.

## Architecture Patterns

VNShop uses four core patterns together:

| Pattern | How VNShop uses it |
| --- | --- |
| Domain-Driven Design | Each bounded context owns its language, aggregate rules, use cases, and persistence schema. |
| Hexagonal Architecture | Domain and application code depend on ports. Framework and database code lives in adapters. |
| CQRS | Write paths enforce business rules. Read paths serve product, order, and search queries without exposing domain entities. |
| Event-Driven Saga | Cross-service workflows publish and consume Kafka events, especially order, payment, inventory, shipping, and notification flows. |

Hexagonal flow:

```text
                 inbound adapters
          REST controllers, Kafka consumers
                       |
                       v
+------------------------------------------------+
| application layer                              |
| use cases, commands, queries, ports            |
+----------------------+-------------------------+
                       |
                       v
+------------------------------------------------+
| domain layer                                   |
| aggregates, value objects, domain services     |
| no Spring, NestJS, JPA, Kafka, or HTTP imports |
+----------------------+-------------------------+
                       |
                       v
                 outbound ports
       repositories, event publishers, gateways
                       |
                       v
                outbound adapters
       JPA, Redis, Kafka, Keycloak, carriers
```

When adding behavior, start in the domain model, expose it through an application use case, then connect adapters last.

## Coding Convention

Follow these guardrails across services:

| Area | Rule |
| --- | --- |
| Java DTOs | Use `record`, not mutable classes. This applies to commands, requests, responses, and queries. |
| JPA entities | Use Lombok `@Getter` and `@Setter` on JPA entity classes. |
| JPA repositories | Use a two-layer adapter: `*JpaRepository implements Port` wraps `*SpringDataRepository extends JpaRepository`. |
| Domain layer | Keep zero framework imports. No Spring, NestJS, JPA, Kafka, HTTP, validation annotations, or persistence annotations in domain code. |
| Tests | 90% coverage is the target. Java services use JaCoCo. NestJS services use Jest. |
| API responses | Return the shared success/error envelope described in the architecture doc. |
| Git | Use conventional commits and name the affected bounded context in PR descriptions. |

## Project Structure

```text
services/
  api-gateway/          # Spring Cloud Gateway (8080)
  user-service/         # Auth + profiles (8081)
  product-service/      # Products + reviews (8082)
  inventory-service/    # Stock + flash sales (8083)
  cart-service/         # Redis cart (8084)
  search-service/       # Elasticsearch (8086)
  notification-service/ # Email/SMS/Push (8087)
  order-service/        # Orders + checkout + coupons + finance (8091)
  payment-service/      # VNPAY/MoMo (8092)
  shipping-service/     # GHN/GHTK (8093)
infra/                  # K8s, Prometheus, Alertmanager, etc.
.sisyphus/              # Architecture docs, analysis, evidence, plans
```

## How to Develop

1. Read [`.sisyphus/ARCHITECTURE.md`](.sisyphus/ARCHITECTURE.md) before changing service boundaries, domain rules, or integration flows.
2. Read [`.sisyphus/STATUS.md`](.sisyphus/STATUS.md) to see what is complete, what is pending, and which tests need local infrastructure.
3. Start with the domain model. Add or change value objects, aggregate methods, and domain services before touching controllers or persistence.
4. Add application use cases around domain behavior. Depend on ports, not adapters.
5. Add outbound adapters only after the port contract is clear.
6. Add inbound adapters last, such as REST controllers or Kafka consumers.
7. Run focused tests for the service you changed, then run wider tests before opening a PR.
8. Keep documentation in sync when behavior, ports, setup, or service ownership changes.

## Status

See [`.sisyphus/STATUS.md`](.sisyphus/STATUS.md) for current project status.

As of 2026-05-12, the requirements audit reports 119 tracked features with 71% total feature coverage. Architecture alignment is active: coupon, review, and seller-finance contexts were folded into their owning services, DTOs were standardized as Java records, JPA adapter conventions were enforced, and coverage gates were configured for the main Java services plus cart-service.

Known gaps called out by the status doc include product variants, guest cart, return/refund flow, real carrier tracking, admin dashboard, product image gallery, multilingual support, and digital invoice support.

## How to Contribute

1. Pick one bounded context and read its section in the architecture doc.
2. Check the status doc for open gaps and test constraints.
3. Follow the coding conventions in this README before writing code.
4. Keep the domain layer framework-free.
5. Add or update tests with every behavior change.
6. Update docs when your change affects setup, service ownership, APIs, or architecture.
7. In PR descriptions, state which bounded context changed and which tests you ran.

Good first contributions are small, bounded, and covered by tests: a missing use case, a DTO cleanup, a repository adapter fix, a service-specific test, or a doc update that helps the next contributor.
