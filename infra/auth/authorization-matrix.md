# VNShop Authorization Matrix

Gateway enforcement source: `services/api-gateway/src/main/java/com/vnshop/apigateway/infrastructure/config/SecurityConfig.java`. Service endpoint source: current `*Controller.java` files under `services/*/src/main/java`.

## Roles

| Role | Meaning |
| --- | --- |
| `UNAUTHENTICATED` | Public internet user without bearer token or login session. |
| `BUYER` | Authenticated shopper. Owns cart, checkout, order, profile, and address operations. |
| `SELLER` | Authenticated merchant. Owns seller profile, catalog management, and seller fulfillment operations. |
| `ADMIN` | Authenticated platform operator. Owns admin back-office operations. |
| `SERVICE` | OAuth2 client-credentials caller for service-to-service traffic. Not exposed to browsers. |

## Gateway Public Routes

| Role | Method | Path | Service | Notes |
| --- | --- | --- | --- | --- |
| `UNAUTHENTICATED` | `GET` | `/products/**` | product-service | Product listing and product detail browsing. |
| `UNAUTHENTICATED` | `GET` | `/search/**` | search-service | Product search. Covers `/search`. |
| `UNAUTHENTICATED` | `GET` | `/categories/**` | search-service | Category browsing. Covers `/categories`. |
| `UNAUTHENTICATED` | `GET` | `/health` | gateway/platform | Health probe. |
| `UNAUTHENTICATED` | `POST` | `/auth/**` | Keycloak/auth route | Login, registration, token, and callback entry points routed through auth surface. |
| `UNAUTHENTICATED` | `GET` | `/payment/*/callback` | payment-service | Provider browser redirect callback. Must validate provider state/signature in payment flow before changing order state. |

## Buyer Routes

| Role | Method | Path | Service | Notes |
| --- | --- | --- | --- | --- |
| `BUYER` | `GET` | `/products` | product-service | Also public. |
| `BUYER` | `GET` | `/products/{id}` | product-service | Also public. |
| `BUYER` | `GET` | `/search` | search-service | Also public. |
| `BUYER` | `GET` | `/categories` | search-service | Also public. |
| `BUYER` | `GET` | `/cart` | cart-service | Current cart read; requires `X-Buyer-Id` propagation from authenticated identity. |
| `BUYER` | `POST` | `/cart/items` | cart-service | Add cart item. |
| `BUYER` | `PUT` | `/cart/items` | cart-service | Update cart item quantity. |
| `BUYER` | `DELETE` | `/cart/items/{productId}/{variantSku}` | cart-service | Remove cart item. |
| `BUYER` | `DELETE` | `/cart` | cart-service | Clear cart. |
| `BUYER` | `POST` | `/checkout/calculate` | order-service | Calculate checkout breakdown for buyer cart. |
| `BUYER` | `GET` | `/checkout/payment-methods` | order-service | List available payment methods. |
| `BUYER` | `POST` | `/checkout/shipping-options` | order-service | Quote shipping options. |
| `BUYER` | `POST` | `/orders` | order-service | Create order. Requires `Idempotency-Key`. |
| `BUYER` | `GET` | `/orders` | order-service | List buyer orders. |
| `BUYER` | `GET` | `/orders/{id}` | order-service | Read own order. |
| `BUYER` | `POST` | `/orders/{id}/cancel` | order-service | Requested policy path for buyer cancel. Current controller uses `DELETE /orders/{id}/cancel`; align route or gateway policy before prod. |
| `BUYER` | `DELETE` | `/orders/{id}/cancel` | order-service | Current implemented cancel route. |
| `BUYER` | `POST` | `/payment/cod/confirm` | payment-service | Confirm COD payment for own order. |
| `BUYER` | `GET` | `/payment/status/{orderId}` | payment-service | Read own payment status. |
| `BUYER` | `GET` | `/users/me` | user-service | Read buyer profile. |
| `BUYER` | `PUT` | `/users/me` | user-service | Upsert buyer profile. |
| `BUYER` | `POST` | `/users/me/addresses` | user-service | Add buyer address. |
| `BUYER` | `DELETE` | `/users/me/addresses/{index}` | user-service | Delete buyer address. |
| `BUYER` | `PUT` | `/users/me/addresses/{index}/default` | user-service | Set default address. |

## Seller Routes

| Role | Method | Path | Service | Notes |
| --- | --- | --- | --- | --- |
| `SELLER` | `POST` | `/sellers/register` | user-service | Register seller profile for authenticated account. |
| `SELLER` | `GET` | `/sellers/me` | user-service | Read seller profile. |
| `SELLER` | `POST` | `/sellers/me/products` | product-service | Create seller product. Requires `X-Seller-Id` propagation from authenticated seller identity. |
| `SELLER` | `PUT` | `/sellers/me/products/{id}` | product-service | Update seller product. |
| `SELLER` | `GET` | `/seller/orders` | order-service | Requested policy path for seller order list. Current controller exposes `GET /seller/orders/pending`; align route or gateway policy before prod. |
| `SELLER` | `GET` | `/seller/orders/pending` | order-service | Current implemented pending seller orders route. |
| `SELLER` | `PUT` | `/seller/orders/{id}/accept` | order-service | Accept sub-order. Current controller variable name is `{subOrderId}`. |
| `SELLER` | `PUT` | `/seller/orders/{id}/reject` | order-service | Reject sub-order with reason. Current controller variable name is `{subOrderId}`. |
| `SELLER` | `PUT` | `/seller/orders/{id}/ship` | order-service | Mark sub-order shipped with tracking number. Current controller variable name is `{subOrderId}`. |

## Admin Routes

| Role | Method | Path | Service | Notes |
| --- | --- | --- | --- | --- |
| `ADMIN` | `GET` | `/admin/sellers` | user-service | List pending sellers. |
| `ADMIN` | `POST` | `/admin/sellers/{id}/approve` | user-service | Approve seller. |
| `ADMIN` | `GET` | `/admin/orders` | order-service | Platform order audit/list route reserved by policy; endpoint not yet implemented in current controllers. |
| `ADMIN` | `GET` | `/admin/finance` | seller-finance-service | Platform finance audit route reserved by policy; endpoint not found in current controllers. |

## Service-To-Service Routes

| Caller | Method | Path | Target | Notes |
| --- | --- | --- | --- | --- |
| `SERVICE` | any required internal call | internal service URL, not public gateway path | all services | Use Keycloak client `vnshop-api` with client credentials. Tokens carry `service:vnshop-api` audience/claim and are accepted only on internal network policy paths. |

## Enforcement Notes

- Gateway must treat `realm_access.roles` values as Spring roles. Current converter maps realm roles to `ROLE_*` authorities.
- Public rules must remain method-specific. `GET /products/**` is public, but seller product writes under `/sellers/me/products` require `SELLER`.
- Gateway currently gates `/admin/**` as `ADMIN` and all other non-public paths as authenticated. Fine-grained `BUYER`/`SELLER` route gating should be added at gateway and/or service method level before production cutover.
- Identity headers such as `X-Buyer-Id`, `X-Seller-Id`, and `X-User-Id` must be set by trusted gateway/service middleware from token claims, not accepted from public clients.
