# Kafka SASL/PLAIN + ACL Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Secure the Kafka event bus with per-service authentication (SASL/PLAIN) and topic-level authorization (ACLs) so that a compromised or rogue container cannot forge events on topics it doesn't own (especially `payment.completed`).

**Architecture:** KRaft-mode SASL/PLAIN with StandardAuthorizer. Each service gets a unique username/password. ACLs restrict WRITE to owned topics and READ to subscribed topics. A super-user account is used for the init script and admin operations.

**Tech Stack:** Kafka KRaft (no ZooKeeper), SASL/PLAIN, StandardAuthorizer, Spring Boot `spring.kafka` properties, Docker Compose environment variables.

---

## Service Identity Matrix

| Service | Username | Produces To | Consumes From |
|---------|----------|-------------|---------------|
| order-service | `svc-order` | `order.*`, `payment.refund.requested` | `payment.completed`, `payment.refunded`, `inventory.released`, `shipping.cancelled` |
| payment-service | `svc-payment` | `payment.completed`, `payment.refunded` | `payment.refund.requested` |
| inventory-service | `svc-inventory` | `inventory.released` | *(none via Kafka — uses gRPC)* |
| product-service | `svc-product` | `product-events` | *(none)* |
| shipping-service | `svc-shipping` | `shipping.cancelled` | *(none)* |
| seller-finance-service | `svc-finance` | *(none)* | `order.created`, `order.paid`, `payment.refunded` |
| search-service | `svc-search` | *(none)* | `product-events` |
| recommendations-service | `svc-recommendations` | *(none)* | `order.created` |
| init-script (admin) | `admin` | *(topic creation + ACL management)* | *(all — super-user)* |

---

## Task 1: Add SASL/PLAIN Credentials to .env

**Files:**
- Modify: `.env`
- Modify: `.env.example`
- Modify: `.env.secrets.example`

- [ ] **Step 1: Read current .env files to understand format**

- [ ] **Step 2: Add Kafka credentials to .env**

```env
# Kafka SASL credentials
KAFKA_ADMIN_PASSWORD=admin-secret-change-me
KAFKA_SVC_ORDER_PASSWORD=order-secret-change-me
KAFKA_SVC_PAYMENT_PASSWORD=payment-secret-change-me
KAFKA_SVC_INVENTORY_PASSWORD=inventory-secret-change-me
KAFKA_SVC_PRODUCT_PASSWORD=product-secret-change-me
KAFKA_SVC_SHIPPING_PASSWORD=shipping-secret-change-me
KAFKA_SVC_FINANCE_PASSWORD=finance-secret-change-me
KAFKA_SVC_SEARCH_PASSWORD=search-secret-change-me
KAFKA_SVC_RECOMMENDATIONS_PASSWORD=recommendations-secret-change-me
```

- [ ] **Step 3: Update .env.example and .env.secrets.example with placeholder values**

- [ ] **Step 4: Commit**

```bash
git add .env .env.example .env.secrets.example
git commit -m "security(kafka): add SASL/PLAIN service credentials to env files"
```

---

## Task 2: Configure Kafka Broker for SASL/PLAIN + ACLs

**Files:**
- Modify: `docker-compose.yml` (Kafka service, lines 222-251)
- Create: `infra/kafka/kafka_server_jaas.conf`

- [ ] **Step 1: Create JAAS config file**

File: `infra/kafka/kafka_server_jaas.conf`

```
KafkaServer {
    org.apache.kafka.common.security.plain.PlainLoginModule required
    username="admin"
    password="${KAFKA_ADMIN_PASSWORD:-admin-secret-change-me}"
    user_admin="${KAFKA_ADMIN_PASSWORD:-admin-secret-change-me}"
    user_svc-order="${KAFKA_SVC_ORDER_PASSWORD:-order-secret-change-me}"
    user_svc-payment="${KAFKA_SVC_PAYMENT_PASSWORD:-payment-secret-change-me}"
    user_svc-inventory="${KAFKA_SVC_INVENTORY_PASSWORD:-inventory-secret-change-me}"
    user_svc-product="${KAFKA_SVC_PRODUCT_PASSWORD:-product-secret-change-me}"
    user_svc-shipping="${KAFKA_SVC_SHIPPING_PASSWORD:-shipping-secret-change-me}"
    user_svc-finance="${KAFKA_SVC_FINANCE_PASSWORD:-finance-secret-change-me}"
    user_svc-search="${KAFKA_SVC_SEARCH_PASSWORD:-search-secret-change-me}"
    user_svc-recommendations="${KAFKA_SVC_RECOMMENDATIONS_PASSWORD:-recommendations-secret-change-me}";
};
```

- [ ] **Step 2: Update Kafka broker in docker-compose.yml**

Replace the PLAINTEXT listeners with SASL_PLAINTEXT:

```yaml
kafka:
  image: confluentinc/cp-kafka:7.6.0
  ...
  volumes:
    - ./infra/kafka/kafka_server_jaas.conf:/etc/kafka/kafka_server_jaas.conf
  environment:
    ...
    KAFKA_LISTENERS: SASL_PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093,SASL_PLAINTEXT_HOST://0.0.0.0:29092
    KAFKA_ADVERTISED_LISTENERS: SASL_PLAINTEXT://kafka:9092,SASL_PLAINTEXT_HOST://localhost:29092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: SASL_PLAINTEXT:SASL_PLAINTEXT,SASL_PLAINTEXT_HOST:SASL_PLAINTEXT,CONTROLLER:PLAINTEXT
    KAFKA_INTER_BROKER_LISTENER_NAME: SASL_PLAINTEXT
    KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL: PLAIN
    KAFKA_SASL_ENABLED_MECHANISMS: PLAIN
    KAFKA_OPTS: "-Djava.security.auth.login.config=/etc/kafka/kafka_server_jaas.conf"
    # ACL configuration
    KAFKA_AUTHORIZER_CLASS_NAME: org.apache.kafka.metadata.authorizer.StandardAuthorizer
    KAFKA_SUPER_USERS: "User:admin"
    KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND: "false"
```

- [ ] **Step 3: Verify docker-compose config is valid**

Run: `docker compose config --quiet`

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml infra/kafka/kafka_server_jaas.conf
git commit -m "security(kafka): configure broker for SASL/PLAIN + StandardAuthorizer ACLs"
```

---

## Task 3: Update Init Script to Use SASL + Create ACLs

**Files:**
- Modify: `infra/scripts/init-kafka-topics.sh`

- [ ] **Step 1: Read current init script**

- [ ] **Step 2: Update to authenticate as admin and set ACLs**

The script needs to:
1. Use `--command-config` with admin SASL credentials when creating topics
2. After topic creation, configure ACLs per service

```bash
#!/bin/bash
set -e

BROKER="kafka:9092"
ADMIN_CONFIG="/tmp/admin.properties"

# Create admin client config
cat > $ADMIN_CONFIG <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="admin" password="${KAFKA_ADMIN_PASSWORD:-admin-secret-change-me}";
EOF

echo "Waiting for Kafka..."
until kafka-broker-api-versions --bootstrap-server $BROKER --command-config $ADMIN_CONFIG > /dev/null 2>&1; do
  sleep 2
done
echo "Kafka is ready."

# Create topics
TOPICS=(
  "messaging.message.sent:1"
  "product-events:1"
  "order.created:3"
  "order.updated:3"
  "order.paid:3"
  "order.shipped:3"
  "order.cancelled:3"
  "payment.completed:1"
  "payment.refund.requested:1"
  "payment.refunded:1"
  "inventory.released:1"
  "shipping.cancelled:1"
)

for entry in "${TOPICS[@]}"; do
  IFS=':' read -r topic partitions <<< "$entry"
  kafka-topics --bootstrap-server $BROKER --command-config $ADMIN_CONFIG \
    --create --if-not-exists --topic "$topic" --partitions "$partitions" --replication-factor 1
done
echo "Topics created."

# --- ACLs ---
ACL_CMD="kafka-acls --bootstrap-server $BROKER --command-config $ADMIN_CONFIG"

# order-service: produces order.*, payment.refund.requested
$ACL_CMD --add --allow-principal User:svc-order --operation Write --topic order --resource-pattern-type prefixed
$ACL_CMD --add --allow-principal User:svc-order --operation Write --topic payment.refund.requested
# order-service: consumes payment.completed, payment.refunded, inventory.released, shipping.cancelled
$ACL_CMD --add --allow-principal User:svc-order --operation Read --topic payment.completed
$ACL_CMD --add --allow-principal User:svc-order --operation Read --topic payment.refunded
$ACL_CMD --add --allow-principal User:svc-order --operation Read --topic inventory.released
$ACL_CMD --add --allow-principal User:svc-order --operation Read --topic shipping.cancelled
$ACL_CMD --add --allow-principal User:svc-order --operation Read --group order-service-payment
$ACL_CMD --add --allow-principal User:svc-order --operation Read --group order-service-refund
$ACL_CMD --add --allow-principal User:svc-order --operation Read --group order-service-projection
$ACL_CMD --add --allow-principal User:svc-order --operation Read --group order-service-finance
$ACL_CMD --add --allow-principal User:svc-order --operation Read --group order-service-saga-compensation

# payment-service: produces payment.completed, payment.refunded
$ACL_CMD --add --allow-principal User:svc-payment --operation Write --topic payment.completed
$ACL_CMD --add --allow-principal User:svc-payment --operation Write --topic payment.refunded
# payment-service: consumes payment.refund.requested
$ACL_CMD --add --allow-principal User:svc-payment --operation Read --topic payment.refund.requested
$ACL_CMD --add --allow-principal User:svc-payment --operation Read --group payment-service-paypal-refund

# inventory-service: produces inventory.released
$ACL_CMD --add --allow-principal User:svc-inventory --operation Write --topic inventory.released

# product-service: produces product-events
$ACL_CMD --add --allow-principal User:svc-product --operation Write --topic product-events

# shipping-service: produces shipping.cancelled
$ACL_CMD --add --allow-principal User:svc-shipping --operation Write --topic shipping.cancelled

# seller-finance-service: consumes order.created, order.paid, payment.refunded
$ACL_CMD --add --allow-principal User:svc-finance --operation Read --topic order.created
$ACL_CMD --add --allow-principal User:svc-finance --operation Read --topic order.paid
$ACL_CMD --add --allow-principal User:svc-finance --operation Read --topic payment.refunded
$ACL_CMD --add --allow-principal User:svc-finance --operation Read --group seller-finance-service
$ACL_CMD --add --allow-principal User:svc-finance --operation Read --group seller-finance-service-refund

# search-service: consumes product-events
$ACL_CMD --add --allow-principal User:svc-search --operation Read --topic product-events
$ACL_CMD --add --allow-principal User:svc-search --operation Read --group search-service

# recommendations-service: consumes order.created
$ACL_CMD --add --allow-principal User:svc-recommendations --operation Read --topic order.created
$ACL_CMD --add --allow-principal User:svc-recommendations --operation Read --group recommendations-service

echo "ACLs configured."
rm -f $ADMIN_CONFIG
```

- [ ] **Step 3: Update init-kafka container in docker-compose.yml to pass KAFKA_ADMIN_PASSWORD env var**

- [ ] **Step 4: Commit**

```bash
git add infra/scripts/init-kafka-topics.sh docker-compose.yml
git commit -m "security(kafka): update init script with SASL auth and per-service ACLs"
```

---

## Task 4: Add SASL Config to order-service

**Files:**
- Modify: `services/order-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml` (order-service environment)

- [ ] **Step 1: Read current application.yml Kafka section**

- [ ] **Step 2: Add SASL properties to application.yml**

Under `spring.kafka`, add:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    properties:
      security.protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_PLAINTEXT}
      sasl.mechanism: PLAIN
      sasl.jaas.config: >-
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_SASL_USERNAME:svc-order}"
        password="${KAFKA_SASL_PASSWORD:order-secret-change-me}";
```

- [ ] **Step 3: Add env vars to docker-compose.yml order-service section**

```yaml
KAFKA_SECURITY_PROTOCOL: SASL_PLAINTEXT
KAFKA_SASL_USERNAME: svc-order
KAFKA_SASL_PASSWORD: ${KAFKA_SVC_ORDER_PASSWORD:-order-secret-change-me}
```

- [ ] **Step 4: Run tests (should still pass with PLAINTEXT fallback in local dev)**

Run: `cd services/order-service && mvn test`

- [ ] **Step 5: Commit**

```bash
git add services/order-service/src/main/resources/application.yml docker-compose.yml
git commit -m "security(kafka): add SASL/PLAIN config to order-service"
```

---

## Task 5: Add SASL Config to payment-service

**Files:**
- Modify: `services/payment-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml` (payment-service environment)

- [ ] **Step 1: Read current application.yml Kafka section**

- [ ] **Step 2: Add SASL properties (same pattern as Task 4)**

```yaml
spring:
  kafka:
    properties:
      security.protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_PLAINTEXT}
      sasl.mechanism: PLAIN
      sasl.jaas.config: >-
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_SASL_USERNAME:svc-payment}"
        password="${KAFKA_SASL_PASSWORD:payment-secret-change-me}";
```

- [ ] **Step 3: Add env vars to docker-compose.yml payment-service section**

```yaml
KAFKA_SECURITY_PROTOCOL: SASL_PLAINTEXT
KAFKA_SASL_USERNAME: svc-payment
KAFKA_SASL_PASSWORD: ${KAFKA_SVC_PAYMENT_PASSWORD:-payment-secret-change-me}
```

- [ ] **Step 4: Run tests**

Run: `cd services/payment-service && mvn test`

- [ ] **Step 5: Commit**

```bash
git add services/payment-service/src/main/resources/application.yml docker-compose.yml
git commit -m "security(kafka): add SASL/PLAIN config to payment-service"
```

---

## Task 6: Add SASL Config to inventory-service

**Files:**
- Modify: `services/inventory-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml` (inventory-service environment)

- [ ] **Step 1: Add SASL properties to application.yml**

```yaml
spring:
  kafka:
    properties:
      security.protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_PLAINTEXT}
      sasl.mechanism: PLAIN
      sasl.jaas.config: >-
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_SASL_USERNAME:svc-inventory}"
        password="${KAFKA_SASL_PASSWORD:inventory-secret-change-me}";
```

- [ ] **Step 2: Add env vars to docker-compose.yml**

```yaml
KAFKA_SECURITY_PROTOCOL: SASL_PLAINTEXT
KAFKA_SASL_USERNAME: svc-inventory
KAFKA_SASL_PASSWORD: ${KAFKA_SVC_INVENTORY_PASSWORD:-inventory-secret-change-me}
```

- [ ] **Step 3: Run tests**

Run: `cd services/inventory-service && mvn test`

- [ ] **Step 4: Commit**

```bash
git add services/inventory-service/src/main/resources/application.yml docker-compose.yml
git commit -m "security(kafka): add SASL/PLAIN config to inventory-service"
```

---

## Task 7: Add SASL Config to product-service

**Files:**
- Modify: `services/product-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml` (product-service environment)

NOTE: product-service uses `kafka:29092` as bootstrap. The docker-compose env should override to `kafka:9092` with SASL.

- [ ] **Step 1: Add SASL properties to application.yml**

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    properties:
      security.protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_PLAINTEXT}
      sasl.mechanism: PLAIN
      sasl.jaas.config: >-
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_SASL_USERNAME:svc-product}"
        password="${KAFKA_SASL_PASSWORD:product-secret-change-me}";
```

- [ ] **Step 2: Add env vars to docker-compose.yml**

```yaml
KAFKA_BOOTSTRAP_SERVERS: kafka:9092
KAFKA_SECURITY_PROTOCOL: SASL_PLAINTEXT
KAFKA_SASL_USERNAME: svc-product
KAFKA_SASL_PASSWORD: ${KAFKA_SVC_PRODUCT_PASSWORD:-product-secret-change-me}
```

- [ ] **Step 3: Run tests**

Run: `cd services/product-service && mvn test`

- [ ] **Step 4: Commit**

```bash
git add services/product-service/src/main/resources/application.yml docker-compose.yml
git commit -m "security(kafka): add SASL/PLAIN config to product-service"
```

---

## Task 8: Add SASL Config to shipping-service

**Files:**
- Modify: `services/shipping-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml` (shipping-service environment)

- [ ] **Step 1: Add SASL properties to application.yml**

```yaml
spring:
  kafka:
    properties:
      security.protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_PLAINTEXT}
      sasl.mechanism: PLAIN
      sasl.jaas.config: >-
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_SASL_USERNAME:svc-shipping}"
        password="${KAFKA_SASL_PASSWORD:shipping-secret-change-me}";
```

- [ ] **Step 2: Add env vars to docker-compose.yml**

```yaml
KAFKA_SECURITY_PROTOCOL: SASL_PLAINTEXT
KAFKA_SASL_USERNAME: svc-shipping
KAFKA_SASL_PASSWORD: ${KAFKA_SVC_SHIPPING_PASSWORD:-shipping-secret-change-me}
```

- [ ] **Step 3: Run tests**

Run: `cd services/shipping-service && mvn test`

- [ ] **Step 4: Commit**

```bash
git add services/shipping-service/src/main/resources/application.yml docker-compose.yml
git commit -m "security(kafka): add SASL/PLAIN config to shipping-service"
```

---

## Task 9: Add SASL Config to seller-finance-service

**Files:**
- Modify: `services/seller-finance-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml` (seller-finance-service environment)

- [ ] **Step 1: Add SASL properties**

```yaml
spring:
  kafka:
    properties:
      security.protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_PLAINTEXT}
      sasl.mechanism: PLAIN
      sasl.jaas.config: >-
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_SASL_USERNAME:svc-finance}"
        password="${KAFKA_SASL_PASSWORD:finance-secret-change-me}";
```

- [ ] **Step 2: Add env vars to docker-compose.yml**

```yaml
KAFKA_SECURITY_PROTOCOL: SASL_PLAINTEXT
KAFKA_SASL_USERNAME: svc-finance
KAFKA_SASL_PASSWORD: ${KAFKA_SVC_FINANCE_PASSWORD:-finance-secret-change-me}
```

- [ ] **Step 3: Run tests**

Run: `cd services/seller-finance-service && mvn test`

- [ ] **Step 4: Commit**

```bash
git add services/seller-finance-service/src/main/resources/application.yml docker-compose.yml
git commit -m "security(kafka): add SASL/PLAIN config to seller-finance-service"
```

---

## Task 10: Add SASL Config to search-service + recommendations-service

**Files:**
- Modify: `services/search-service/src/main/resources/application.yml`
- Modify: `services/recommendations-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml` (both service environments)

- [ ] **Step 1: Add SASL properties to search-service application.yml**

```yaml
spring:
  kafka:
    properties:
      security.protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_PLAINTEXT}
      sasl.mechanism: PLAIN
      sasl.jaas.config: >-
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_SASL_USERNAME:svc-search}"
        password="${KAFKA_SASL_PASSWORD:search-secret-change-me}";
```

- [ ] **Step 2: Add SASL properties to recommendations-service application.yml**

```yaml
spring:
  kafka:
    properties:
      security.protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_PLAINTEXT}
      sasl.mechanism: PLAIN
      sasl.jaas.config: >-
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_SASL_USERNAME:svc-recommendations}"
        password="${KAFKA_SASL_PASSWORD:recommendations-secret-change-me}";
```

- [ ] **Step 3: Add env vars to docker-compose.yml for both services**

search-service:
```yaml
KAFKA_SECURITY_PROTOCOL: SASL_PLAINTEXT
KAFKA_SASL_USERNAME: svc-search
KAFKA_SASL_PASSWORD: ${KAFKA_SVC_SEARCH_PASSWORD:-search-secret-change-me}
```

recommendations-service:
```yaml
KAFKA_SECURITY_PROTOCOL: SASL_PLAINTEXT
KAFKA_SASL_USERNAME: svc-recommendations
KAFKA_SASL_PASSWORD: ${KAFKA_SVC_RECOMMENDATIONS_PASSWORD:-recommendations-secret-change-me}
```

- [ ] **Step 4: Run tests for both**

```bash
cd services/search-service && mvn test
cd services/recommendations-service && mvn test
```

- [ ] **Step 5: Commit**

```bash
git add services/search-service/ services/recommendations-service/ docker-compose.yml
git commit -m "security(kafka): add SASL/PLAIN config to search + recommendations services"
```

---

## Task 11: Integration Smoke Test

- [ ] **Step 1: Start the full stack**

```bash
docker compose --profile apps up -d
```

- [ ] **Step 2: Check Kafka broker logs for SASL readiness**

```bash
docker compose logs kafka | grep -i "sasl\|auth\|acl"
```
Expected: SASL mechanism enabled, no auth errors from the broker itself.

- [ ] **Step 3: Check init-kafka logs for topic + ACL creation**

```bash
docker compose logs init-kafka
```
Expected: All topics created, all ACLs configured, no errors.

- [ ] **Step 4: Check service logs for successful Kafka connections**

```bash
docker compose logs order-service | grep -i "kafka\|sasl\|auth" | head -20
docker compose logs payment-service | grep -i "kafka\|sasl\|auth" | head -20
```
Expected: Successful authentication, no "authentication failed" errors.

- [ ] **Step 5: Test ACL enforcement — try to produce to a forbidden topic**

```bash
# Create a test client config for svc-order
cat > /tmp/test-client.properties <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="svc-order" password="${KAFKA_SVC_ORDER_PASSWORD}";
EOF

# This should SUCCEED (order-service owns order.* topics)
echo "test" | docker compose exec -T kafka kafka-console-producer --broker-list kafka:9092 --topic order.created --producer.config /tmp/test-client.properties

# This should FAIL (order-service cannot write to payment.completed)
echo "forged" | docker compose exec -T kafka kafka-console-producer --broker-list kafka:9092 --topic payment.completed --producer.config /tmp/test-client.properties
```
Expected: First command succeeds, second command fails with authorization error.

- [ ] **Step 6: Commit test results or fix any issues**

---

## Verification Checklist

- [ ] Kafka broker starts with SASL_PLAINTEXT listeners
- [ ] All 8 services connect successfully with their credentials
- [ ] ACLs prevent cross-service topic writes (svc-order CANNOT write to payment.completed)
- [ ] ACLs allow legitimate traffic (payment-service CAN write to payment.completed)
- [ ] Init script creates all topics and ACLs without errors
- [ ] All service unit tests still pass (SASL config uses env defaults for local dev)
- [ ] `docker compose config --quiet` passes

---

## Task Dependencies

```
Task 1 (env credentials) ──── prerequisite for all
Task 2 (broker config)   ──── prerequisite for Tasks 3-10
Task 3 (init script)     ──── depends on Task 2
Tasks 4-10 (services)    ──── depend on Task 1, independent of each other
Task 11 (smoke test)     ──── depends on ALL previous tasks
```

**Recommended execution:**
- Sequential: Tasks 1 → 2 → 3 (foundational)
- Parallel: Tasks 4-10 (all independent service configs)
- Final: Task 11 (integration verification)

---

## Rollback Plan

If SASL breaks the cluster:
1. Revert broker listeners to PLAINTEXT in docker-compose.yml
2. Set `KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND: "true"` temporarily
3. Remove SASL properties from service application.yml files (env defaults to SASL_PLAINTEXT but broker accepts PLAINTEXT = connection refused)
4. Alternative: set `KAFKA_SECURITY_PROTOCOL: PLAINTEXT` env var on all services to skip SASL

## Local Dev Escape Hatch

For developers who don't want SASL locally, add to their `.env.local`:
```
KAFKA_SECURITY_PROTOCOL=PLAINTEXT
```

And revert broker to PLAINTEXT listeners. This is the trade-off: security in Docker vs convenience locally. The application.yml defaults to SASL_PLAINTEXT but respects the env override.
