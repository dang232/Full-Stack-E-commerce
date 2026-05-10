# VNShop Services

Phase 0 service skeletons live here.

| Service | Stack | Port | Purpose |
| --- | --- | --- | --- |
| `api-gateway` | Spring Boot + Spring Cloud Gateway | 8080 | Keycloak OAuth2, routing, rate limiting, circuit breaker |
| `user-service` | Spring Boot | 8081 | Buyer profile, seller profile, shop account, wallet basics |
| `product-service` | Spring Boot | 8082 | Seller-owned product catalog, variants, inventory, CQRS read models |
| `cart-service` | NestJS | 8083 | Multi-seller cart snapshots |
| `order-service` | Spring Boot | 8084 | Orders, sub-orders, seller fulfillment, Kafka workers |
| `search-service` | NestJS | 8086 | Elasticsearch indexing, faceted search, export |
| `notification-service` | NestJS | 8087 | Email, SMS, push, in-app notifications |
