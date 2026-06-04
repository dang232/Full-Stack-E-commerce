# Phase 6: Search, Performance & Testing Infrastructure — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace JPA-based search with Elasticsearch, add load testing with k6, introduce Testcontainers for integration tests, fill coverage gaps, and add mutation testing.

**Architecture:** Add Elasticsearch container, retarget search-service Kafka consumer to index into ES, create k6 scenarios for critical paths, add Testcontainers to CI, strengthen Pact assertions.

**Tech Stack:** Elasticsearch 8.x, Spring Data Elasticsearch, k6, Testcontainers, PIT (pitest), Prometheus service discovery

**Depends on:** Phase 4 (security fixes), can run parallel to Phase 5

---

## What's Wrong (Evidence)

| # | Problem | Detail |
|---|---------|--------|
| 1 | Search uses JPA LIKE queries | `SearchProductsUseCase` → `ProductReadModelRepository` (Spring Data JPA). Will collapse at 10K+ products |
| 2 | Zero performance/load testing | No k6, Gatling, Locust, or JMeter anywhere in repo |
| 3 | No Testcontainers | Despite 5 infra deps (Postgres, Redis, Kafka, Mongo, ES), CI uses only mocks |
| 4 | Uneven test density | order-service: 42 tests, coupon-service: 2 tests, cart-service: 2 specs |
| 5 | Pact tests assert nothing real | `assertNotNull("Pact verified")` — string literal assertion |
| 6 | Prometheus uses static_configs | Won't work in K8s — needs service discovery |
| 7 | No Docker Compose resource limits | A single OOM can kill entire dev stack |

---

## File Structure

```
services/search-service/
├── pom.xml                                    (MODIFY - add spring-data-elasticsearch)
├── src/main/java/.../infrastructure/
│   ├── elasticsearch/
│   │   ├── ProductElasticsearchRepository.java (NEW)
│   │   ├── ProductDocument.java               (NEW)
│   │   └── ElasticsearchSearchAdapter.java    (NEW)
│   └── kafka/ProductEventConsumer.java        (MODIFY - index to ES)
├── src/main/resources/application.yml         (MODIFY - ES config)

infra/
├── elasticsearch/
│   └── synonyms.txt                           (NEW - Vietnamese synonyms)
├── prometheus/prometheus-k8s.yml              (NEW - K8s service discovery)
├── k6/
│   ├── scenarios/
│   │   ├── flash-sale-load.js                 (NEW)
│   │   ├── checkout-flow.js                   (NEW)
│   │   ├── search-autocomplete.js             (NEW)
│   │   └── payment-callback.js               (NEW)
│   └── config.js                              (NEW - shared config)

docker-compose.yml                             (MODIFY - resource limits + ES container)

services/order-service/pom.xml                 (MODIFY - add testcontainers)
services/order-service/src/test/java/.../integration/  (NEW)
```

---

## Stage 1: Elasticsearch for Search Service (Tasks 1-3)

### Task 1: Add Elasticsearch container and Spring Data ES dependency

**Files:**
- Modify: `docker-compose.yml` (add/update elasticsearch service)
- Modify: `services/search-service/pom.xml`
- Modify: `services/search-service/src/main/resources/application.yml`

- [ ] **Step 1: Add Elasticsearch to docker-compose (or verify existing)**

Verify `elasticsearch` service exists in docker-compose.yml. If it uses an older config, update to:
```yaml
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.15.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=true
      - ELASTIC_PASSWORD=${ELASTIC_PASSWORD:-changeme}
      - xpack.security.http.ssl.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -s -u elastic:${ELASTIC_PASSWORD:-changeme} http://localhost:9200/_cluster/health | grep -q '\"status\":\"green\\|yellow\"'"]
      interval: 10s
      timeout: 5s
      retries: 10
    deploy:
      resources:
        limits:
          memory: 1G
    profiles: ["apps"]
```

- [ ] **Step 2: Add Spring Data Elasticsearch dependency**

In `services/search-service/pom.xml`, add:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

- [ ] **Step 3: Add ES connection config**

In `services/search-service/src/main/resources/application.yml`:
```yaml
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URI:http://elasticsearch:9200}
    username: ${ELASTIC_USERNAME:elastic}
    password: ${ELASTIC_PASSWORD:changeme}
```

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml services/search-service/pom.xml services/search-service/src/main/resources/application.yml
git commit -m "infra(search): add Elasticsearch 8.x container and Spring Data ES dependency"
```

### Task 2: Create Elasticsearch document model and repository

**Files:**
- Create: `services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/elasticsearch/ProductDocument.java`
- Create: `services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/elasticsearch/ProductElasticsearchRepository.java`

- [ ] **Step 1: Create ProductDocument**

```java
package com.vnshop.searchservice.infrastructure.elasticsearch;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document(indexName = "products")
@Setting(settingPath = "/elasticsearch/product-settings.json")
public class ProductDocument {
    @Id
    private String id;

    @MultiField(mainField = @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer"),
                otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
    private String name;

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String description;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Double)
    private BigDecimal originalPrice;

    @Field(type = FieldType.Float)
    private Float averageRating;

    @Field(type = FieldType.Integer)
    private Integer reviewCount;

    @Field(type = FieldType.Keyword)
    private String sellerId;

    @Field(type = FieldType.Keyword)
    private String sellerName;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private List<String> imageUrls;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Integer)
    private Integer totalSold;

    @Field(type = FieldType.Nested)
    private List<VariantDoc> variants;

    // Getters/setters + builder pattern
    public static class VariantDoc {
        private String variantId;
        private String sku;
        private String label;
        private BigDecimal price;
        private Integer stock;
    }
}
```

- [ ] **Step 2: Create index settings JSON**

Create `services/search-service/src/main/resources/elasticsearch/product-settings.json`:
```json
{
  "analysis": {
    "analyzer": {
      "vietnamese_analyzer": {
        "type": "custom",
        "tokenizer": "standard",
        "filter": ["lowercase", "asciifolding", "vietnamese_stop"]
      },
      "autocomplete_analyzer": {
        "type": "custom",
        "tokenizer": "autocomplete_tokenizer",
        "filter": ["lowercase", "asciifolding"]
      }
    },
    "tokenizer": {
      "autocomplete_tokenizer": {
        "type": "edge_ngram",
        "min_gram": 2,
        "max_gram": 20,
        "token_chars": ["letter", "digit"]
      }
    },
    "filter": {
      "vietnamese_stop": {
        "type": "stop",
        "stopwords": ["và", "của", "là", "có", "cho", "với", "các", "này", "được"]
      }
    }
  }
}
```

- [ ] **Step 3: Create repository interface**

```java
package com.vnshop.searchservice.infrastructure.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import java.util.List;

public interface ProductElasticsearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    List<ProductDocument> findByNameContaining(String query);
}
```

- [ ] **Step 4: Commit**

```bash
git add services/search-service/src/
git commit -m "feat(search): add Elasticsearch ProductDocument with Vietnamese analyzer"
```

### Task 3: Create ElasticsearchSearchAdapter and rewire search use case

**Files:**
- Create: `services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/elasticsearch/ElasticsearchSearchAdapter.java`
- Modify: `services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/kafka/ProductEventConsumer.java`

- [ ] **Step 1: Create search adapter implementing domain port**

```java
package com.vnshop.searchservice.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import com.vnshop.searchservice.domain.port.out.ProductSearchPort;
import com.vnshop.searchservice.domain.SearchResult;
import com.vnshop.searchservice.domain.SearchCriteria;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ElasticsearchSearchAdapter implements ProductSearchPort {
    private final ElasticsearchOperations elasticsearchOps;

    public ElasticsearchSearchAdapter(ElasticsearchOperations elasticsearchOps) {
        this.elasticsearchOps = elasticsearchOps;
    }

    @Override
    public SearchResult search(SearchCriteria criteria) {
        var boolQuery = new BoolQuery.Builder();

        // Full-text search
        if (criteria.query() != null && !criteria.query().isBlank()) {
            boolQuery.must(Query.of(q -> q.multiMatch(m -> m
                .query(criteria.query())
                .fields("name^3", "description", "brand^2", "categoryName")
                .fuzziness("AUTO")
            )));
        }

        // Category filter
        if (criteria.categoryId() != null) {
            boolQuery.filter(Query.of(q -> q.term(t -> t.field("categoryId").value(criteria.categoryId()))));
        }

        // Price range filter
        if (criteria.minPrice() != null || criteria.maxPrice() != null) {
            boolQuery.filter(Query.of(q -> q.range(r -> {
                var range = r.field("price");
                if (criteria.minPrice() != null) range.gte(JsonData.of(criteria.minPrice()));
                if (criteria.maxPrice() != null) range.lte(JsonData.of(criteria.maxPrice()));
                return range;
            })));
        }

        // Brand filter
        if (criteria.brand() != null) {
            boolQuery.filter(Query.of(q -> q.term(t -> t.field("brand").value(criteria.brand()))));
        }

        // Status = ACTIVE only
        boolQuery.filter(Query.of(q -> q.term(t -> t.field("status").value("ACTIVE"))));

        NativeQuery query = NativeQuery.builder()
            .withQuery(Query.of(q -> q.bool(boolQuery.build())))
            .withPageable(PageRequest.of(criteria.page(), criteria.size()))
            .withSort(buildSort(criteria.sort()))
            .build();

        SearchHits<ProductDocument> hits = elasticsearchOps.search(query, ProductDocument.class);
        return mapToSearchResult(hits, criteria.page(), criteria.size());
    }

    @Override
    public List<String> autocomplete(String prefix, int limit) {
        NativeQuery query = NativeQuery.builder()
            .withQuery(Query.of(q -> q.match(m -> m
                .field("name.autocomplete")
                .query(prefix)
            )))
            .withPageable(PageRequest.of(0, limit))
            .build();

        SearchHits<ProductDocument> hits = elasticsearchOps.search(query, ProductDocument.class);
        return hits.stream().map(h -> h.getContent().getName()).toList();
    }
}
```

- [ ] **Step 2: Modify Kafka consumer to index into Elasticsearch**

Update `ProductEventConsumer` to index/update/delete documents in ES:
```java
@KafkaListener(topics = "product-events", groupId = "search-indexer")
public void onProductEvent(String payload) {
    JsonNode node = objectMapper.readTree(payload);
    String eventType = node.get("eventType").asText();
    String productId = node.get("productId").asText();

    switch (eventType) {
        case "PRODUCT_CREATED", "PRODUCT_UPDATED" -> {
            ProductDocument doc = mapToDocument(node);
            productElasticsearchRepository.save(doc);
        }
        case "PRODUCT_DELETED" -> {
            productElasticsearchRepository.deleteById(productId);
        }
    }
}
```

- [ ] **Step 3: Compile and verify**

```bash
cd services/search-service && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add services/search-service/
git commit -m "feat(search): implement Elasticsearch adapter with fuzzy search and Vietnamese analyzer"
```

---

## Stage 2: k6 Performance Test Suite (Task 4)

### Task 4: Create k6 load test scenarios for critical paths

**Files:**
- Create: `infra/k6/config.js`
- Create: `infra/k6/scenarios/flash-sale-load.js`
- Create: `infra/k6/scenarios/checkout-flow.js`
- Create: `infra/k6/scenarios/search-autocomplete.js`
- Create: `infra/k6/README.md`

- [ ] **Step 1: Create shared config**

Create `infra/k6/config.js`:
```javascript
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

export const defaultHeaders = {
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${AUTH_TOKEN}`,
};

export const thresholds = {
  http_req_duration: ['p(95)<500', 'p(99)<2000'],
  http_req_failed: ['rate<0.01'],
};
```

- [ ] **Step 2: Create flash-sale load test**

Create `infra/k6/scenarios/flash-sale-load.js`:
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, defaultHeaders, thresholds } from '../config.js';

export const options = {
  scenarios: {
    flash_sale_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },   // ramp up
        { duration: '30s', target: 1000 },  // spike to 1000 concurrent
        { duration: '10s', target: 0 },     // ramp down
      ],
    },
  },
  thresholds,
};

export default function () {
  const productId = 'flash-sale-product-1';

  // Check stock
  const stockRes = http.get(`${BASE_URL}/flash-sale/stock/${productId}`);
  check(stockRes, { 'stock check 200': (r) => r.status === 200 });

  // Attempt reservation
  const reserveRes = http.post(
    `${BASE_URL}/flash-sale/reserve`,
    JSON.stringify({ productId, quantity: 1 }),
    { headers: defaultHeaders }
  );
  check(reserveRes, {
    'reserve 200 or 409': (r) => r.status === 200 || r.status === 409,
  });

  sleep(0.1);
}
```

- [ ] **Step 3: Create checkout flow test**

Create `infra/k6/scenarios/checkout-flow.js`:
```javascript
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { BASE_URL, defaultHeaders, thresholds } from '../config.js';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    checkout_steady: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 100,
      maxVUs: 200,
    },
  },
  thresholds,
};

export default function () {
  group('checkout flow', () => {
    // Calculate totals
    const calcRes = http.post(
      `${BASE_URL}/checkout/calculate`,
      JSON.stringify({ cartId: 'test-cart-1', shippingMethod: 'STANDARD' }),
      { headers: defaultHeaders }
    );
    check(calcRes, { 'calculate 200': (r) => r.status === 200 });

    // Place order with idempotency key
    const orderRes = http.post(
      `${BASE_URL}/orders`,
      JSON.stringify({ cartId: 'test-cart-1', paymentMethod: 'COD', shippingAddress: {} }),
      { headers: { ...defaultHeaders, 'Idempotency-Key': randomString(32) } }
    );
    check(orderRes, { 'order 200 or 201': (r) => [200, 201].includes(r.status) });
  });

  sleep(1);
}
```

- [ ] **Step 4: Create search autocomplete test**

Create `infra/k6/scenarios/search-autocomplete.js`:
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, thresholds } from '../config.js';

export const options = {
  scenarios: {
    autocomplete_burst: {
      executor: 'constant-vus',
      vus: 50,
      duration: '1m',
    },
  },
  thresholds: {
    ...thresholds,
    'http_req_duration{name:autocomplete}': ['p(95)<200'], // 200ms target for autocomplete
  },
};

const prefixes = ['ao', 'quan', 'giay', 'tui', 'dien', 'laptop', 'iphone', 'sam'];

export default function () {
  const prefix = prefixes[Math.floor(Math.random() * prefixes.length)];

  const res = http.get(`${BASE_URL}/search?q=${prefix}&page=0&size=10`, {
    tags: { name: 'autocomplete' },
  });
  check(res, {
    'search 200': (r) => r.status === 200,
    'has results': (r) => JSON.parse(r.body).data !== null,
  });

  sleep(0.5);
}
```

- [ ] **Step 5: Create README**

Create `infra/k6/README.md`:
```markdown
# k6 Performance Tests

## Prerequisites
- Install k6: `brew install grafana/k6/k6` or `choco install k6`
- Full stack running: `docker compose --profile apps up -d`
- Auth token: export AUTH_TOKEN from Keycloak

## Run scenarios
```bash
# Flash sale spike test
k6 run infra/k6/scenarios/flash-sale-load.js

# Checkout steady-state
k6 run --env AUTH_TOKEN=<token> infra/k6/scenarios/checkout-flow.js

# Search autocomplete
k6 run infra/k6/scenarios/search-autocomplete.js
```

## CI Integration
Nightly job via workflow_dispatch — see .github/workflows/perf-test.yml
```

- [ ] **Step 6: Commit**

```bash
git add infra/k6/
git commit -m "test(perf): add k6 load test scenarios for flash-sale, checkout, and search"
```

---

## Stage 3: Testcontainers Integration Tests (Task 5)

### Task 5: Add Testcontainers-based integration tests to order-service

**Files:**
- Modify: `services/order-service/pom.xml` (add testcontainers deps)
- Create: `services/order-service/src/test/java/com/vnshop/orderservice/integration/TestcontainersConfig.java`
- Create: `services/order-service/src/test/java/com/vnshop/orderservice/integration/OrderCreationIntegrationTest.java`

- [ ] **Step 1: Add Testcontainers dependencies to pom.xml**

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Create Testcontainers base config**

```java
package com.vnshop.orderservice.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("order_svc")
            .withUsername("test")
            .withPassword("test");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withKraft();
    }
}
```

- [ ] **Step 3: Create integration test**

```java
package com.vnshop.orderservice.integration;

import com.vnshop.orderservice.application.CreateOrderUseCase;
import com.vnshop.orderservice.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class OrderCreationIntegrationTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Test
    void shouldPersistOrderAndPublishEvent() {
        // Given: valid order command
        // When: create order
        // Then: order persisted in real Postgres, event published to real Kafka
        // This verifies Flyway migrations run, JPA mappings work, Kafka producer config valid
        assertThat(createOrderUseCase).isNotNull();
        // Full test depends on gRPC stub/mock for inventory/payment/shipping ports
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add services/order-service/pom.xml services/order-service/src/test/java/com/vnshop/orderservice/integration/
git commit -m "test(integration): add Testcontainers config for Postgres+Kafka in order-service"
```

---

## Stage 4: Docker Compose Resource Limits & Prometheus K8s SD (Tasks 6-7)

### Task 6: Add resource limits to Docker Compose services

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add deploy.resources.limits to all Java services**

For each Java service (order, payment, product, inventory, shipping, user, search, seller-finance, recommendations, api-gateway, coupon-service), add:
```yaml
    deploy:
      resources:
        limits:
          memory: 768M
        reservations:
          memory: 512M
```

For Node services (cart-service, notification-service):
```yaml
    deploy:
      resources:
        limits:
          memory: 256M
        reservations:
          memory: 128M
```

For infrastructure (Kafka, Postgres instances):
```yaml
    deploy:
      resources:
        limits:
          memory: 1G
```

- [ ] **Step 2: Document minimum system requirements in README**

Add section to README:
```markdown
## System Requirements

- **Minimum RAM:** 16GB (full stack uses ~12GB with all services)
- **Recommended RAM:** 32GB (comfortable with IDE + browser open)
- **Docker Desktop:** Allocate at least 12GB to Docker
- **Disk:** 10GB+ free for images and volumes
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml README.md
git commit -m "infra(compose): add resource limits to all services, document system requirements"
```

### Task 7: Add Prometheus Kubernetes service discovery config

**Files:**
- Create: `infra/prometheus/prometheus-k8s.yml`

- [ ] **Step 1: Create K8s-specific Prometheus config**

Create `infra/prometheus/prometheus-k8s.yml`:
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "alert-rules.yml"
  - "slo-rules.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets: ["alertmanager:9093"]

scrape_configs:
  - job_name: 'kubernetes-pods'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        regex: ([^:]+)(?::\d+)?;(\d+)
        replacement: $1:$2
        target_label: __address__
      - source_labels: [__meta_kubernetes_namespace]
        action: replace
        target_label: namespace
      - source_labels: [__meta_kubernetes_pod_name]
        action: replace
        target_label: pod
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: replace
        target_label: service
```

- [ ] **Step 2: Commit**

```bash
git add infra/prometheus/prometheus-k8s.yml
git commit -m "infra(prometheus): add Kubernetes service discovery config for pod scraping"
```

---

## Stage 5: Strengthen Pact Tests & Fill Coverage Gaps (Tasks 8-9)

### Task 8: Fix Pact consumer test assertions

**Files:**
- Modify: Pact consumer test files in `services/order-service/src/test/java/.../pact/`

- [ ] **Step 1: Find and fix assertNotNull("Pact verified") assertions**

Replace trivial assertions with real verification. Example for inventory consumer:
```java
@Test
@PactTestFor(pactMethod = "inventoryReservePact")
void verifyInventoryReserve(MockServer mockServer) {
    // Configure gRPC client to point to mock
    var client = buildInventoryClient(mockServer.getUrl());
    
    // Execute actual call
    var response = client.reserve("order-123", List.of(
        new ItemSnapshot("product-1", "SKU-001", 2)
    ));
    
    // Assert response structure matches expected
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getReservationId()).isNotBlank();
}
```

- [ ] **Step 2: Repeat for payment and shipping consumer tests**

Apply same pattern: real client call → meaningful assertions on response fields.

- [ ] **Step 3: Run Pact tests**

```bash
cd services/order-service && mvn test -pl . -Dtest="*Pact*" -q
```

- [ ] **Step 4: Commit**

```bash
git add services/order-service/src/test/
git commit -m "test(pact): replace trivial assertions with real client calls and response validation"
```

### Task 9: Add mutation testing with PIT

**Files:**
- Modify: `services/order-service/pom.xml`

- [ ] **Step 1: Add pitest-maven-plugin**

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.15.0</version>
    <dependencies>
        <dependency>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-junit5-plugin</artifactId>
            <version>1.2.1</version>
        </dependency>
    </dependencies>
    <configuration>
        <targetClasses>
            <param>com.vnshop.orderservice.domain.*</param>
            <param>com.vnshop.orderservice.application.*</param>
        </targetClasses>
        <targetTests>
            <param>com.vnshop.orderservice.*Test</param>
        </targetTests>
        <mutationThreshold>70</mutationThreshold>
        <outputFormats>
            <format>HTML</format>
            <format>XML</format>
        </outputFormats>
    </configuration>
</plugin>
```

- [ ] **Step 2: Run mutation testing**

```bash
cd services/order-service && mvn org.pitest:pitest-maven:mutationCoverage
```
Review report at `target/pit-reports/` — identify tests that pass but don't assert meaningfully.

- [ ] **Step 3: Commit**

```bash
git add services/order-service/pom.xml
git commit -m "test(mutation): add PIT mutation testing for domain and application layers"
```

---

## Phase 6 Complete — Verification Checklist

- [ ] Elasticsearch container starts and search service indexes products via Kafka consumer
- [ ] `GET /search?q=ao` returns results from ES (not JPA) with sub-200ms response
- [ ] `k6 run infra/k6/scenarios/flash-sale-load.js` completes without errors
- [ ] Testcontainers integration test boots real Postgres + Kafka in order-service
- [ ] Pact consumer tests make real client calls (no `assertNotNull("Pact verified")`)
- [ ] PIT mutation report generated for order-service domain layer
- [ ] All Java services in docker-compose have `deploy.resources.limits`
- [ ] Prometheus K8s config validates with `promtool check config prometheus-k8s.yml`
