# Tips and Tricks

Patterns, practices, and idioms used across this codebase.

---

## Spring Boot

### Secret management
```yaml
# Always use env-var with default for local dev
spring:
  datasource:
    password: ${DB_PASSWORD:vnshop}

# For production-critical secrets, use empty default (fail-fast)
keycloak:
  admin:
    client-secret: ${KEYCLOAK_ADMIN_CLIENT_SECRET:}
```

### Keycloak realm role extraction
Spring's default JWT converter only maps `scope` claims. Keycloak puts roles in `realm_access.roles`. Custom converter needed:

```java
private static class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return Collections.emptyList();
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
    }
}
```

Used in: `seller-finance-service/infrastructure/config/SecurityConfig.java`

### Pagination cap
```yaml
spring:
  data:
    web:
      pageable:
        max-page-size: 50   # Prevents ?size=99999 resource exhaustion
```

### Circuit breaker + rate limiter composition
In Spring Cloud Gateway RouteConfig:
```java
// Rate limit: Redis-backed, per-user
private GatewayFilterSpec rateLimited(GatewayFilterSpec f, String service, ...) {
    return f.requestRateLimiter(c -> c.setRateLimiter(limiter).setKeyResolver(resolver))
            .circuitBreaker(c -> c.setName(service));
}
```

### Actuator security
```java
// Only health endpoint public; everything else authenticated
.requestMatchers("/actuator/health", "/actuator/prometheus", "/actuator/info").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

For production: bind to separate management port:
```yaml
management:
  server:
    port: 9091  # Not routed through gateway
```

---

## Kafka

### Outbox pattern (money-path events)
```
1. UseCase: save domain state + outbox row in same @Transactional
2. OutboxRelay: poll outbox table, publish to Kafka, mark published
3. Consumer: process event, idempotency check via dedup table
```

Used for: `payment.refund_requested` (order-service → payment-service)

### Direct send (acceptable when consumer is idempotent)
```java
kafkaTemplate.send("payment.refunded", event);
// At-least-once only — consumer has processed_refund table for dedup
```

### Consumer health indicator
```java
@Component
public class KafkaConsumerHealthIndicator implements HealthIndicator {
    public Health health() {
        adminClient.describeTopics(topics).topicNameValues(); // metadata-only
        return Health.up().build();
    }
}
```

### Error isolation in batch consumers
```typescript
// Process each message independently — one failure doesn't drop the batch
for (const event of events) {
  try {
    await sendNotification(event);
  } catch (err) {
    logger.error(`Failed: type=${event.type} user=${event.userId}`, err);
  }
}
```

---

## MongoDB (notification-service)

### TTL auto-expiry
```typescript
// 90-day auto-delete — no cron job needed
@Prop({ type: Date, index: { expires: 0 } })
expiresAt: Date;

// Set on creation: new Date(Date.now() + 90 * 24 * 60 * 60 * 1000)
```

### $facet for thread pagination
```typescript
// Single aggregation: count + paginated results
const pipeline = [
  { $match: { userId, threadId: { $ne: null } } },
  { $group: { _id: '$threadId', ... } },
  { $facet: {
      data: [{ $sort: { lastAt: -1 } }, { $skip: offset }, { $limit: size }],
      total: [{ $count: 'count' }]
  }}
];
```

### Compound indexes for efficient queries
```typescript
// userId + createdAt: primary list query
@Schema({ collection: 'notifications' })
// Indexes defined in schema:
// { userId: 1, createdAt: -1 }  — paginated list by user
// { userId: 1, threadId: 1 }    — thread queries
// { idempotencyKey: 1 }         — unique sparse (dedup)
```

---

## Redis

### Atomic dedup with SET NX
```typescript
// Single atomic command — first caller wins, no TOCTOU
const acquired = await redis.set(key, '1', 'EX', ttlSeconds, 'NX');
if (!acquired) return; // duplicate, skip
```

### MULTI transaction for drain
```typescript
// Atomic read + delete — no lost messages between commands
const multi = redis.multi();
multi.lrange(key, 0, -1);
multi.del(key);
const [items] = await multi.exec();
```

### Bounded queues with LTRIM
```typescript
await redis.rpush(queueKey, notificationId);
await redis.ltrim(queueKey, -500, -1); // Cap at 500 items
```

### Short TTL + heartbeat > long TTL
```typescript
// Registration: 2-min TTL
await redis.set(`socket:${userId}:${socketId}`, '1', 'EX', 120);

// Heartbeat: refresh every 30s
setInterval(() => redis.expire(`socket:${userId}:${socketId}`, 120), 30_000);
```

---

## WebSocket (socket.io)

### Manual reconnect with fresh JWT
```typescript
const connect = () => {
  const token = getAccessToken(); // Always fresh
  const socket = io('/ws/notifications', {
    auth: { token },
    reconnection: false, // We handle this ourselves
  });
  socket.on('disconnect', () => {
    setTimeout(connect, backoff()); // Exponential: 1s → 30s
  });
};
```

### Namespace separation
```
/ws/notifications  — notification delivery (bell, inbox)
/ws/messaging      — chat/messaging
```
Each namespace has its own auth, room management, and event schema.

### Room per userId
```typescript
// Server: join room on connect
void client.join(`user:${userId}`);

// Send to specific user (all their tabs/devices)
server.to(`user:${userId}`).emit('notification:new', payload);
```

### Offline queue + catch-up
```typescript
// On connect: drain queued notifications
const offlineIds = await connectionRegistry.drainOfflineQueue(userId);
if (offlineIds.length > 0) {
  const notifications = await repo.findByIds(offlineIds);
  client.emit('notification:catch-up', notifications);
}
```

---

## Frontend (React + TanStack Query)

### Optimistic updates with rollback
```typescript
const mutation = useMutation({
  mutationFn: markNotificationRead,
  onMutate: async (id) => {
    await queryClient.cancelQueries(['notifications']);
    const prev = queryClient.getQueryData(['notifications']);
    queryClient.setQueryData(['notifications'], old => /* optimistic update */);
    return { prev };
  },
  onError: (_, __, ctx) => queryClient.setQueryData(['notifications'], ctx.prev),
  onSettled: () => queryClient.invalidateQueries(['notifications']),
});
```

### Socket events → cache invalidation
```typescript
socket.on('notification:new', (notification) => {
  // Insert into cache directly (no refetch)
  queryClient.setQueryData(['notifications'], old => [notification, ...old]);
  queryClient.setQueryData(['unread-count'], old => old + 1);
});
```

### URL-driven state
```typescript
// Shareable, back-button works, SEO-friendly
const [searchParams, setSearchParams] = useSearchParams();
const type = searchParams.get('type') || 'all';
const page = parseInt(searchParams.get('page') || '0');
```

### Zod schemas aligned with BE DTOs
```typescript
export const notificationSchema = z.object({
  id: z.string(),
  type: notificationTypeSchema,
  title: z.string(),
  body: z.string(),
  deepLink: z.string().optional(),
  read: z.boolean(),
  createdAt: z.string(),
});
// Schema validates at the API boundary — type errors caught early
```

---

## Testing

### Mock JwtDecoder with realm_access roles
```java
@MockitoBean
private JwtDecoder jwtDecoder;

@BeforeEach
void configureJwt() {
    Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(300),
        Map.of("alg", "none"),
        Map.of("sub", "user-1", "realm_access", Map.of("roles", List.of("ADMIN", "SELLER"))));
    when(jwtDecoder.decode("token")).thenReturn(jwt);
}
```

### Jest forceExit for NestJS
```bash
npx jest --no-coverage --forceExit
# NestJS + socket.io leaves handles open; forceExit prevents hanging
```

### Exclude auto-config in @SpringBootTest
```java
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=" +
    "DataSourceAutoConfiguration," +
    "HibernateJpaAutoConfiguration," +
    "FlywayAutoConfiguration," +
    "KafkaAutoConfiguration"
})
```

### BA-grade journey pattern
```
Feature: Buyer completes purchase
  AC-1: Add to cart shows updated count
  AC-2: Checkout validates stock
  AC-3: Payment creates pending order
  AC-4: Successful payment triggers confirmation
  → Each AC is a Playwright test case
```

---

## Security

### Defense-in-depth role checks
```java
// Gateway level
.pathMatchers("/admin/**").hasRole("ADMIN")
.pathMatchers("/seller/**", "/sellers/me/**").hasRole("SELLER")

// Service level (in case gateway is bypassed)
.requestMatchers("/admin/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

### Generic error messages
```java
@ExceptionHandler(Exception.class)
ResponseEntity<?> handleGeneric(Exception ex) {
    log.error("Unhandled exception", ex);  // Full details server-side
    return ResponseEntity.status(500).body(ApiResponse.error("An unexpected error occurred"));
}
```

### Rate limit auth separately
```java
// Stricter limit for auth (5 req/10s) vs data endpoints (20 req/s)
.route("auth", r -> r.path("/auth/**")
    .filters(f -> rateLimited(f, "auth-service", strictLimiter, ipKeyResolver)))
```

### Disable Swagger via env var
```yaml
springdoc:
  api-docs:
    enabled: ${SWAGGER_ENABLED:false}
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:false}
```

---

## Docker Compose

### Profile separation
```yaml
# Infrastructure — always starts
redis:
  image: redis:7-alpine
  # no profiles key = starts on bare `docker compose up`

# Application — only with --profile apps
user-service:
  profiles: ["apps"]
  depends_on:
    postgres-user:
      condition: service_healthy
```

### Health checks for databases
```yaml
postgres-user:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U vnshop"]
    interval: 5s
    timeout: 5s
    retries: 5
    start_period: 10s
```

### Volume persistence
```yaml
volumes:
  postgres-user-data:     # Survives container recreation
  redis-data:
  mongo-data:
  elasticsearch-data:
```
