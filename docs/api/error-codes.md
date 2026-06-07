# VNShop API Error Codes

All VNShop services return errors in this standard shape:

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "Order with ID 123 not found",
  "details": ["Field 'orderId' must be a valid UUID"],
  "timestamp": "2026-06-06T12:00:00Z",
  "traceId": "abc123def456"
}
```

- `code` — machine-readable error code; use this for programmatic handling
- `message` — human-readable description; safe to display in UI after sanitization
- `details` — optional list of field-level validation messages (populated on 400s)
- `timestamp` — ISO-8601 instant the error occurred
- `traceId` — OTEL trace ID for correlating with distributed traces; may be `null`

---

## AUTH_*

| Code | HTTP | Description |
|------|------|-------------|
| `UNAUTHORIZED` | 401 | Request is missing or has an invalid authentication token. |
| `FORBIDDEN` | 403 | Authenticated user does not have permission for this resource. |
| `INVALID_SIGNATURE` | 401 | Webhook or request signature validation failed. |

## ORDER_*

| Code | HTTP | Description |
|------|------|-------------|
| `ORDER_NOT_FOUND` | 404 | No order exists with the given ID, or it is not visible to the caller. |
| `ORDER_ACCESS_DENIED` | 403 | Caller is not the owner of this order. |
| `ORDER_NOT_PAYABLE` | 422 | Order is in a state that does not allow payment (e.g. already paid, cancelled). |
| `PRODUCT_NOT_FOUND` | 404 | One or more products referenced in the order could not be found. |
| `PRODUCT_CATALOG_UNAVAILABLE` | 503 | Product catalog service is temporarily unreachable. |
| `CART_UNAVAILABLE` | 503 | Cart service is temporarily unreachable during checkout. |

## PAYMENT_*

| Code | HTTP | Description |
|------|------|-------------|
| `PAYMENT_ACCESS_DENIED` | 403 | Caller does not own this payment. |
| `PAYMENT_NOT_REFUNDABLE` | 422 | Payment is in a state that does not allow refund. |
| `UNSUPPORTED_PAYMENT_METHOD` | 400 | The requested payment method is not accepted. |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | A request with the same idempotency key but different parameters was already received. |
| `CHARGEBACK_NOT_FOUND` | 404 | No chargeback record exists with the given ID. |
| `ORDER_NOT_FOUND` | 404 | No order exists with the given ID when initiating payment. |

## INVENTORY_*

| Code | HTTP | Description |
|------|------|-------------|
| `INSUFFICIENT_STOCK` | 422 | Requested quantity exceeds available stock. |
| `PRODUCT_NOT_FOUND` | 404 | Product SKU does not exist in inventory. |

## CART_*

| Code | HTTP | Description |
|------|------|-------------|
| `CART_FULL` | 422 | Cart has reached its maximum number of distinct items. |
| `CART_ITEM_LIMIT_EXCEEDED` | 422 | Requested quantity exceeds the per-item limit. |
| `CART_ITEM_NOT_FOUND` | 404 | The referenced item is not present in the cart. |
| `INVALID_CART_OPERATION` | 400 | Operation is not valid for the current cart state. |
| `CART_VERSION_CONFLICT` | 409 | Cart was modified by another request; optimistic lock failed. |
| `CURRENCY_MISMATCH` | 500 | Cart contains items with inconsistent currencies. |

## Generic

| Code | HTTP | Description |
|------|------|-------------|
| `VALIDATION_ERROR` | 400 | One or more request fields failed validation. See `details` for field-level messages. |
| `BAD_REQUEST` | 400 | The request is malformed or contains invalid parameters. |
| `INTERNAL_ERROR` | 500 | Unexpected server-side error. Use `traceId` to correlate with service logs. |
| `SERVICE_UNAVAILABLE` | 503 | A downstream dependency is temporarily unavailable. |
| `INVOICE_ACCESS_DENIED` | 403 | Caller does not have permission to access this invoice. |
