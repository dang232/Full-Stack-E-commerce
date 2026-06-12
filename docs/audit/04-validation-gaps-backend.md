# 04 — Validation Gaps (Backend)

> Input that should be rejected but isn't. Every entry here means garbage data
> reaches the database or causes unhandled exceptions (500 errors).

---

## VAL-01: Coupon Service — Zero @Valid Annotations Anywhere

**Service:** coupon-service  
**File:** `services/coupon-service/src/main/java/com/vnshop/couponservice/infrastructure/web/CouponController.java`  
**Lines:** 52, 69, 79, 85

**What's wrong:**  
`spring-boot-starter-validation` is in pom.xml but no `@Valid` on any `@RequestBody`. No Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`) on any DTO.

**What happens:**  
A request with `null` code, `null` type, negative `maxUses`, `null` validUntil, or `null` orderAmount passes straight to the domain layer and throws `NullPointerException` → unhandled 500 error with stack trace.

**Fix for every DTO in this service:**
```java
// CreateCouponRequest.java
@NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Z0-9_-]+$")
private String code;

@NotNull
private DiscountType type;

@NotNull @DecimalMin("0.01")
private BigDecimal discountValue;

@NotNull @Min(1)
private Integer maxUses;

@NotNull @Future
private Instant validUntil;
```
Then add `@Valid` to controller: `public ResponseEntity<?> create(@Valid @RequestBody CreateCouponRequest req)`

---

## VAL-02: Notification Service — No ValidationPipe, No class-validator

**Service:** notification-service  
**File:** `services/notification-service/src/main.ts`  
**Lines:** 6-46

**What's wrong:**  
No `app.useGlobalPipes(new ValidationPipe())` in bootstrap. No `class-validator` decorators on any DTO. Kafka consumers also trust payloads without schema.

**What happens:**  
Any malformed JSON is accepted. Missing fields cause runtime crashes deep in business logic. Kafka poison pills crash the consumer.

**Fix:**
```typescript
// main.ts
app.useGlobalPipes(new ValidationPipe({
  whitelist: true,       // strip unknown fields
  forbidNonWhitelisted: true,
  transform: true,
}));

// DTOs:
import { IsNotEmpty, IsEnum, IsOptional, MaxLength } from 'class-validator';
export class SendNotificationDto {
  @IsNotEmpty() userId: string;
  @IsEnum(NotificationChannel) channel: NotificationChannel;
  @MaxLength(200) title: string;
  @MaxLength(4000) body: string;
}
```

---

## VAL-03: Cart Service — No Runtime Validation Library

**Service:** cart-service  
**File:** `services/cart-service/src/cart/infrastructure/add-cart-item.request.ts`  
**Lines:** 1-5

**What's wrong:**  
Request DTOs are plain TypeScript interfaces — no runtime validation. `class-validator` is not installed. Controller accepts any shape.

**What happens:**  
`quantity: -5` or `quantity: 999999` or `productId: null` passes through. Domain layer catches some (quantity <= 0), but negative values wrapped in strings or missing fields cause unhandled errors.

**Fix:**  
Install `class-validator` + `class-transformer`, enable `ValidationPipe`, and add decorators:
```typescript
export class AddCartItemRequest {
  @IsUUID() productId: string;
  @IsOptional() @IsUUID() variantId?: string;
  @IsInt() @Min(1) @Max(10) quantity: number;
}
```

---

## VAL-04: User Service — AddressRequest Has Zero Validation

**Service:** user-service  
**File:** `services/user-service/src/main/java/com/vnshop/userservice/infrastructure/web/AddressRequest.java`  
**Line:** 5

**What's wrong:**  
All fields (street, district, city, postalCode, phone) have no annotations. No length limits, no format checks.

**What happens:**  
A 10MB string in the `street` field is accepted and stored in PostgreSQL. Phone field accepts "hello world."

**Fix:**
```java
@NotBlank @Size(min = 5, max = 200)
private String street;

@NotBlank @Size(max = 100)
private String district;

@NotBlank @Size(max = 100)
private String city;

@Pattern(regexp = "^\\d{5,6}$", message = "Invalid postal code")
private String postalCode;

@Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "Invalid VN phone")
private String phone;
```

---

## VAL-05: User Service — BuyerProfileRequest Has Zero Validation

**Service:** user-service  
**File:** `services/user-service/src/main/java/com/vnshop/userservice/infrastructure/web/BuyerProfileRequest.java`  
**Line:** 3

**What's wrong:**  
Profile update accepts any string for name and phone without length or format constraints. Controller has no `@Valid`.

**Fix:**
```java
@NotBlank @Size(min = 1, max = 100)
private String name;

@Pattern(regexp = "^(\\+84|0)\\d{9,10}$")
private String phone;
```

---

## VAL-06: Product Service — ProductRequest DTO Has Zero Validation

**Service:** product-service  
**File:** `services/product-service/src/main/java/com/vnshop/productservice/infrastructure/web/ProductController.java`  
**Line:** 52 (create), 67 (update)

**What's wrong:**  
No `@Valid` on create or update endpoints. `ProductRequest` has no constraints — name, price, description all accept anything.

**What happens:**  
- Price: -999 → negative price in catalog
- Name: empty string → invisible product
- Description: 10MB string → DB bloat

**Fix:**
```java
@NotBlank @Size(min = 2, max = 200)
private String name;

@NotNull @DecimalMin("0.01") @DecimalMax("999999999")
private BigDecimal price;

@Size(max = 10000)
private String description;

@NotNull @Min(0)
private Integer stock;
```

---

## VAL-07: Order Service — Admin changeStatus Accepts Raw Map Body

**Service:** order-service  
**File:** `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/AdminOrderController.java`  
**Lines:** 52-62

**What's wrong:**  
```java
@PutMapping("/{orderId}/status")
public ResponseEntity<?> changeStatus(@RequestBody Map<String, String> body) {
    String status = body.get("status"); // No validation
}
```
No DTO, no enum validation. Any arbitrary string is accepted as a status value.

**What happens:**  
`{"status": "HACKED"}` → either crashes with `IllegalArgumentException` (if enum parsed) or stores invalid status.

**Fix:**
```java
public record ChangeStatusRequest(@NotNull OrderStatus status) {}
// OrderStatus is the enum — Jackson rejects invalid values automatically
```

---

## VAL-08: Shipping Service — ParcelDto Accepts Zero/Negative Dimensions

**Service:** shipping-service  
**File:** `services/shipping-service/src/main/java/com/vnshop/shippingservice/infrastructure/web/ParcelDto.java`  
**Lines:** 1-9

**What's wrong:**  
`weightKg`, `lengthCm`, `widthCm`, `heightCm` have no `@Min` constraints. Zero dimensions cause division-by-zero in volumetric weight calculation. Negative dimensions produce negative shipping fees.

**Fix:**
```java
@DecimalMin("0.01") private BigDecimal weightKg;
@Min(1) private Integer lengthCm;
@Min(1) private Integer widthCm;
@Min(1) private Integer heightCm;
```

---

## VAL-09: Shipping Service — Tracking Code Path Traversal

**Service:** shipping-service  
**File:** `services/shipping-service/src/main/java/com/vnshop/shippingservice/infrastructure/carrier/GhtkCarrierGateway.java`  
**Line:** 73

**What's wrong:**  
Tracking code from path variable is interpolated directly into the carrier API URL without sanitization. `../../../etc/passwd` style values could manipulate the outbound HTTP request path.

**Fix:**
```java
@Pattern(regexp = "^[A-Z0-9-]{5,30}$")
@PathVariable String trackingCode
```

---

## VAL-10: Search Service — Unbounded Query String

**Service:** search-service  
**File:** `services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/web/SearchController.java`  
**Line:** 50

**What's wrong:**  
`@RequestParam(name = "q") String query` — no `@Size` limit. A 1MB query string hits Elasticsearch, causing timeouts or crashes.

**What happens:**  
DoS via large query. Also, special Elasticsearch query syntax characters could modify the query structure (query injection).

**Fix:**
```java
@RequestParam(name = "q", required = false)
@Size(max = 200) String query
```
Plus sanitize Elasticsearch special chars: `+ - = && || > < ! ( ) { } [ ] ^ " ~ * ? : \ /`

---

## VAL-11: Search Service — No Page Size Upper Bound

**Service:** search-service  
**File:** `services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/web/SearchController.java`  
**Lines:** 48-63

**What's wrong:**  
Client can request `?size=99999`. While `safeSize = Math.min(Math.max(size ?? 20, 1), 100)` exists for the autocomplete endpoint, the main search endpoint passes raw `Pageable` from Spring with no cap.

**Fix:**  
Add a custom `PageableHandlerMethodArgumentResolver` with max page size:
```java
@Override
public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
    resolver.setMaxPageSize(100);
    resolvers.add(resolver);
}
```

---

## VAL-12: Inventory Service — Redis Key Injection via productId

**Service:** inventory-service  
**File:** `services/inventory-service/src/main/java/com/vnshop/inventoryservice/infrastructure/flash/RedisLuaFlashSaleGateway.java`  
**Lines:** 122-130

**What's wrong:**  
`productId` is concatenated into Redis key names without validation: `"flash:stock:" + productId`. If productId contains `:`, `\n`, or other special chars, it can manipulate Redis key space.

**Fix:**
```java
// Validate UUID format before using as key component
if (!UUID_PATTERN.matcher(productId).matches()) {
    throw new IllegalArgumentException("Invalid productId format");
}
```

---

## VAL-13: API Gateway — No Request Body Size Limit

**Service:** api-gateway  
**File:** `services/api-gateway/src/main/resources/application.yml`

**What's wrong:**  
No `spring.codec.max-in-memory-size` or `spring.webflux.multipart.max-file-size` configured. Attacker can send multi-GB request bodies, exhausting gateway memory.

**Fix:**
```yaml
spring:
  codec:
    max-in-memory-size: 10MB
  webflux:
    multipart:
      max-file-size: 5MB
      max-request-size: 10MB
```

---

## VAL-14: Invoice Service — taxCode and digitalCertId Unvalidated

**Service:** invoice-service  
**File:** `services/invoice-service/src/main/java/com/vnshop/invoiceservice/infrastructure/web/SellerAuthorizationController.java`  
**Line:** 67

**What's wrong:**  
`AuthorizeRequest` accepts arbitrary strings for `taxCode` and `digitalCertId`. Vietnamese tax codes have a specific format (10 or 13 digits with check digit).

**Fix:**
```java
@NotBlank @Pattern(regexp = "^\\d{10}(\\d{3})?$", message = "Invalid VN tax code")
private String taxCode;

@NotBlank @Size(max = 64)
private String digitalCertId;
```

---

## VAL-15: Configuration Service — Path Traversal via CONFIG_FILE_PATH

**Service:** configuration-service  
**File:** `services/configuration-service/src/configuration/configuration.service.ts`  
**Lines:** 17-19

**What's wrong:**  
`CONFIG_FILE_PATH` env var is used directly in file read without validation. If an attacker can set this env var (container escape, misconfigured orchestrator), they read arbitrary files.

**Also:** The `serviceName` path parameter in `GET /api/config/:serviceName` is not validated against an allowlist.

**Fix:**
```typescript
const ALLOWED_SERVICES = ['user', 'product', 'order', 'payment', ...];
if (!ALLOWED_SERVICES.includes(serviceName)) {
  throw new BadRequestException(`Unknown service: ${serviceName}`);
}
```
