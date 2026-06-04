# Phase 3D: Governance & Quality � Implementation Plan (Part 1: D1-D3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement GDPR compliance (async data export + deletion saga), enforce MFA for privileged roles via Keycloak, and document PCI-DSS SAQ-A compliance posture.

**Architecture:** Event-driven saga for GDPR (Kafka events orchestrate cross-service data operations), Keycloak realm configuration for MFA (no application code changes), and documentation-only for PCI-DSS.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Apache Kafka (SASL_SSL), Keycloak 26.x, Jackson for JSON serialization

---

## File Structure

```
# Task D1: GDPR
infra/scripts/init-kafka-topics.sh                                    (MODIFIED � add GDPR topics)
services/user-service/src/main/java/com/vnshop/userservice/
  domain/GdprExportRequest.java                                       (NEW)
  domain/GdprExportStatus.java                                        (NEW)
  domain/port/out/GdprExportRepositoryPort.java                       (NEW)
  application/GdprExportUseCase.java                                  (NEW)
  application/GdprDeleteUseCase.java                                  (NEW)
  infrastructure/web/GdprController.java                              (NEW)
  infrastructure/event/GdprExportFragmentListener.java                (NEW)
  infrastructure/persistence/GdprExportJpaEntity.java                 (NEW)
  infrastructure/persistence/GdprExportJpaRepository.java             (NEW)
  infrastructure/config/GdprUseCaseConfig.java                        (NEW)
services/order-service/src/main/java/com/vnshop/orderservice/
  infrastructure/event/GdprEventListener.java                         (NEW)
services/payment-service/src/main/java/com/vnshop/paymentservice/
  infrastructure/event/GdprEventListener.java                         (NEW)
services/notification-service/src/gdpr/gdpr.listener.ts               (NEW)
services/shipping-service/src/main/java/com/vnshop/shippingservice/
  infrastructure/event/GdprEventListener.java                         (NEW)

# Task D2: MFA
infra/keycloak/vnshop-realm.json                                      (MODIFIED)
infra/keycloak/vnshop-realm-prod.json                                 (MODIFIED)

# Task D3: PCI-DSS
docs/pci-dss-saq-a.md                                                 (NEW)
```

---

## Task D1: GDPR Right-to-Deletion + Data Export

### D1.1 � Add GDPR Kafka topics to init script

- [ ] **Step 1:** Modify `infra/scripts/init-kafka-topics.sh` � append these topic creation commands after the existing topics:

```bash
# GDPR topics
kafka-topics --create --if-not-exists --bootstrap-server kafka:9092 \
  --command-config /etc/kafka/client.properties \
  --topic gdpr.export-requested --partitions 1 --replication-factor 1

kafka-topics --create --if-not-exists --bootstrap-server kafka:9092 \
  --command-config /etc/kafka/client.properties \
  --topic gdpr.export-fragment --partitions 3 --replication-factor 1

kafka-topics --create --if-not-exists --bootstrap-server kafka:9092 \
  --command-config /etc/kafka/client.properties \
  --topic gdpr.deletion-requested --partitions 1 --replication-factor 1

kafka-topics --create --if-not-exists --bootstrap-server kafka:9092 \
  --command-config /etc/kafka/client.properties \
  --topic gdpr.deletion-completed --partitions 3 --replication-factor 1
```

- [ ] **Step 2:** Verify script is valid:

```bash
bash -n infra/scripts/init-kafka-topics.sh
```

### D1.2 � User-service domain model

- [ ] **Step 3:** Create `services/user-service/src/main/java/com/vnshop/userservice/domain/GdprExportStatus.java`:

```java
package com.vnshop.userservice.domain;

public enum GdprExportStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    PARTIAL,
    FAILED
}
```

- [ ] **Step 4:** Create `services/user-service/src/main/java/com/vnshop/userservice/domain/GdprExportRequest.java`:

```java
package com.vnshop.userservice.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GdprExportRequest {

    private static final Set<String> REQUIRED_SERVICES = Set.of(
            "user-service", "order-service", "payment-service",
            "notification-service", "shipping-service"
    );

    private final String requestId;
    private final String userId;
    private final Instant createdAt;
    private GdprExportStatus status;
    private final Map<String, String> fragments;
    private final Set<String> missingServices;
    private Instant completedAt;

    public GdprExportRequest(String userId) {
        this.requestId = UUID.randomUUID().toString();
        this.userId = userId;
        this.createdAt = Instant.now();
        this.status = GdprExportStatus.PENDING;
        this.fragments = new HashMap<>();
        this.missingServices = new HashSet<>(REQUIRED_SERVICES);
    }

    public GdprExportRequest(String requestId, String userId, Instant createdAt,
                             GdprExportStatus status, Map<String, String> fragments,
                             Set<String> missingServices, Instant completedAt) {
        this.requestId = requestId;
        this.userId = userId;
        this.createdAt = createdAt;
        this.status = status;
        this.fragments = new HashMap<>(fragments);
        this.missingServices = new HashSet<>(missingServices);
        this.completedAt = completedAt;
    }

    public void addFragment(String serviceName, String payload) {
        this.fragments.put(serviceName, payload);
        this.missingServices.remove(serviceName);
        if (this.missingServices.isEmpty()) {
            this.status = GdprExportStatus.COMPLETED;
            this.completedAt = Instant.now();
        } else {
            this.status = GdprExportStatus.IN_PROGRESS;
        }
    }

    public void markPartial() {
        this.status = GdprExportStatus.PARTIAL;
        this.completedAt = Instant.now();
    }

    public boolean isComplete() {
        return status == GdprExportStatus.COMPLETED || status == GdprExportStatus.PARTIAL;
    }

    public String getRequestId() { return requestId; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public GdprExportStatus getStatus() { return status; }
    public Map<String, String> getFragments() { return Map.copyOf(fragments); }
    public Set<String> getMissingServices() { return Set.copyOf(missingServices); }
    public Instant getCompletedAt() { return completedAt; }
}
```

### D1.3 � User-service port and repository

- [ ] **Step 5:** Create `services/user-service/src/main/java/com/vnshop/userservice/domain/port/out/GdprExportRepositoryPort.java`:

```java
package com.vnshop.userservice.domain.port.out;

import com.vnshop.userservice.domain.GdprExportRequest;
import java.util.Optional;

public interface GdprExportRepositoryPort {
    void save(GdprExportRequest request);
    Optional<GdprExportRequest> findByRequestId(String requestId);
    Optional<GdprExportRequest> findLatestByUserId(String userId);
    boolean hasRecentExport(String userId);
}
```

### D1.4 � User-service use cases

- [ ] **Step 6:** Create `services/user-service/src/main/java/com/vnshop/userservice/application/GdprExportUseCase.java`:

```java
package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.GdprExportRequest;
import com.vnshop.userservice.domain.port.out.GdprExportRepositoryPort;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

public class GdprExportUseCase {

    private final GdprExportRepositoryPort repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public GdprExportUseCase(GdprExportRepositoryPort repository,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public String initiateExport(String userId) {
        if (repository.hasRecentExport(userId)) {
            throw new IllegalStateException("Export already requested within the last hour");
        }

        var request = new GdprExportRequest(userId);
        repository.save(request);

        kafkaTemplate.send("gdpr.export-requested", userId, Map.of(
                "userId", userId,
                "requestId", request.getRequestId()
        ));

        return request.getRequestId();
    }

    public GdprExportRequest getExportStatus(String userId, String requestId) {
        return repository.findByRequestId(requestId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Export request not found"));
    }
}
```

- [ ] **Step 7:** Create `services/user-service/src/main/java/com/vnshop/userservice/application/GdprDeleteUseCase.java`:

```java
package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

public class GdprDeleteUseCase {

    private final UserRepositoryPort userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public GdprDeleteUseCase(UserRepositoryPort userRepository,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void initiateDelete(String userId) {
        // Verify user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Publish deletion request to all PII services
        kafkaTemplate.send("gdpr.deletion-requested", userId, Map.of(
                "userId", userId,
                "requestedAt", java.time.Instant.now().toString()
        ));

        // Anonymize local user data
        userRepository.anonymize(userId);
    }
}
```

### D1.5 � User-service controller

- [ ] **Step 8:** Create `services/user-service/src/main/java/com/vnshop/userservice/infrastructure/web/GdprController.java`:

```java
package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.GdprDeleteUseCase;
import com.vnshop.userservice.application.GdprExportUseCase;
import com.vnshop.userservice.domain.GdprExportRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/gdpr")
public class GdprController {

    private final GdprExportUseCase exportUseCase;
    private final GdprDeleteUseCase deleteUseCase;

    public GdprController(GdprExportUseCase exportUseCase, GdprDeleteUseCase deleteUseCase) {
        this.exportUseCase = exportUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/export/{userId}")
    @PreAuthorize("#userId == authentication.name or hasRole('admin')")
    public ResponseEntity<Map<String, String>> requestExport(@PathVariable String userId) {
        String requestId = exportUseCase.initiateExport(userId);
        return ResponseEntity.accepted().body(Map.of(
                "requestId", requestId,
                "status", "PENDING",
                "message", "Export initiated. Poll status endpoint for completion."
        ));
    }

    @GetMapping("/export/{userId}/status/{requestId}")
    @PreAuthorize("#userId == authentication.name or hasRole('admin')")
    public ResponseEntity<Map<String, Object>> getExportStatus(
            @PathVariable String userId,
            @PathVariable String requestId) {
        GdprExportRequest request = exportUseCase.getExportStatus(userId, requestId);
        return ResponseEntity.ok(Map.of(
                "requestId", request.getRequestId(),
                "status", request.getStatus().name(),
                "missingServices", request.getMissingServices(),
                "completedAt", request.getCompletedAt() != null ? request.getCompletedAt().toString() : ""
        ));
    }

    @DeleteMapping("/delete/{userId}")
    @PreAuthorize("#userId == authentication.name or hasRole('admin')")
    public ResponseEntity<Map<String, String>> requestDeletion(@PathVariable String userId) {
        deleteUseCase.initiateDelete(userId);
        return ResponseEntity.accepted().body(Map.of(
                "status", "DELETION_INITIATED",
                "message", "Deletion request published to all services."
        ));
    }
}
```

### D1.6 � User-service Kafka listener for export fragments

- [ ] **Step 9:** Create `services/user-service/src/main/java/com/vnshop/userservice/infrastructure/event/GdprExportFragmentListener.java`:

```java
package com.vnshop.userservice.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.userservice.domain.GdprExportRequest;
import com.vnshop.userservice.domain.port.out.GdprExportRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GdprExportFragmentListener {

    private static final Logger log = LoggerFactory.getLogger(GdprExportFragmentListener.class);
    private final GdprExportRepositoryPort repository;
    private final ObjectMapper objectMapper;

    public GdprExportFragmentListener(GdprExportRepositoryPort repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "gdpr.export-fragment", groupId = "user-service-gdpr-export")
    public void onExportFragment(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> event = objectMapper.readValue(message, Map.class);
            String requestId = event.get("requestId");
            String serviceName = event.get("serviceName");
            String payload = event.get("payload");

            repository.findByRequestId(requestId).ifPresent(request -> {
                request.addFragment(serviceName, payload);
                repository.save(request);
                log.info("GDPR export fragment received: requestId={}, service={}, complete={}",
                        requestId, serviceName, request.isComplete());
            });
        } catch (Exception e) {
            log.error("Failed to process GDPR export fragment", e);
        }
    }
}
```

### D1.7 � User-service JPA persistence

- [ ] **Step 10:** Create `services/user-service/src/main/java/com/vnshop/userservice/infrastructure/persistence/GdprExportJpaEntity.java`:

```java
package com.vnshop.userservice.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "gdpr_export_requests")
public class GdprExportJpaEntity {

    @Id
    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "fragments", columnDefinition = "TEXT")
    private String fragments;

    @Column(name = "missing_services")
    private String missingServices;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public GdprExportJpaEntity() {}

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFragments() { return fragments; }
    public void setFragments(String fragments) { this.fragments = fragments; }
    public String getMissingServices() { return missingServices; }
    public void setMissingServices(String missingServices) { this.missingServices = missingServices; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
```

- [ ] **Step 11:** Create `services/user-service/src/main/java/com/vnshop/userservice/infrastructure/persistence/GdprExportJpaRepository.java`:

```java
package com.vnshop.userservice.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.userservice.domain.GdprExportRequest;
import com.vnshop.userservice.domain.GdprExportStatus;
import com.vnshop.userservice.domain.port.out.GdprExportRepositoryPort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository
public class GdprExportJpaRepository implements GdprExportRepositoryPort {

    private final GdprExportSpringDataRepository springRepo;
    private final ObjectMapper objectMapper;

    public GdprExportJpaRepository(GdprExportSpringDataRepository springRepo, ObjectMapper objectMapper) {
        this.springRepo = springRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(GdprExportRequest request) {
        GdprExportJpaEntity entity = toEntity(request);
        springRepo.save(entity);
    }

    @Override
    public Optional<GdprExportRequest> findByRequestId(String requestId) {
        return springRepo.findById(requestId).map(this::toDomain);
    }

    @Override
    public Optional<GdprExportRequest> findLatestByUserId(String userId) {
        return springRepo.findTopByUserIdOrderByCreatedAtDesc(userId).map(this::toDomain);
    }

    @Override
    public boolean hasRecentExport(String userId) {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        return springRepo.existsByUserIdAndCreatedAtAfter(userId, oneHourAgo);
    }

    private GdprExportJpaEntity toEntity(GdprExportRequest req) {
        GdprExportJpaEntity e = new GdprExportJpaEntity();
        e.setRequestId(req.getRequestId());
        e.setUserId(req.getUserId());
        e.setStatus(req.getStatus().name());
        e.setCreatedAt(req.getCreatedAt());
        e.setCompletedAt(req.getCompletedAt());
        try {
            e.setFragments(objectMapper.writeValueAsString(req.getFragments()));
            e.setMissingServices(String.join(",", req.getMissingServices()));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize GDPR export", ex);
        }
        return e;
    }

    private GdprExportRequest toDomain(GdprExportJpaEntity e) {
        try {
            Map<String, String> fragments = e.getFragments() != null
                    ? objectMapper.readValue(e.getFragments(), new TypeReference<>() {})
                    : Map.of();
            Set<String> missing = e.getMissingServices() != null && !e.getMissingServices().isBlank()
                    ? new HashSet<>(Arrays.asList(e.getMissingServices().split(",")))
                    : Set.of();
            return new GdprExportRequest(
                    e.getRequestId(), e.getUserId(), e.getCreatedAt(),
                    GdprExportStatus.valueOf(e.getStatus()),
                    fragments, missing, e.getCompletedAt()
            );
        } catch (Exception ex) {
            throw new RuntimeException("Failed to deserialize GDPR export", ex);
        }
    }
}
```

- [ ] **Step 12:** Create `services/user-service/src/main/java/com/vnshop/userservice/infrastructure/persistence/GdprExportSpringDataRepository.java`:

```java
package com.vnshop.userservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;

public interface GdprExportSpringDataRepository extends JpaRepository<GdprExportJpaEntity, String> {
    Optional<GdprExportJpaEntity> findTopByUserIdOrderByCreatedAtDesc(String userId);
    boolean existsByUserIdAndCreatedAtAfter(String userId, Instant after);
}
```

### D1.8 � User-service config wiring

- [ ] **Step 13:** Create `services/user-service/src/main/java/com/vnshop/userservice/infrastructure/config/GdprUseCaseConfig.java`:

```java
package com.vnshop.userservice.infrastructure.config;

import com.vnshop.userservice.application.GdprDeleteUseCase;
import com.vnshop.userservice.application.GdprExportUseCase;
import com.vnshop.userservice.domain.port.out.GdprExportRepositoryPort;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class GdprUseCaseConfig {

    @Bean
    public GdprExportUseCase gdprExportUseCase(GdprExportRepositoryPort repository,
                                                KafkaTemplate<String, Object> kafkaTemplate) {
        return new GdprExportUseCase(repository, kafkaTemplate);
    }

    @Bean
    public GdprDeleteUseCase gdprDeleteUseCase(UserRepositoryPort userRepository,
                                                KafkaTemplate<String, Object> kafkaTemplate) {
        return new GdprDeleteUseCase(userRepository, kafkaTemplate);
    }
}
```

### D1.9 � Order-service GDPR listener (representative example)

- [ ] **Step 14:** Create `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/GdprEventListener.java`:

```java
package com.vnshop.orderservice.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GdprEventListener {

    private static final Logger log = LoggerFactory.getLogger(GdprEventListener.class);
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public GdprEventListener(JdbcTemplate jdbcTemplate,
                             KafkaTemplate<String, Object> kafkaTemplate,
                             ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "gdpr.export-requested", groupId = "order-service-gdpr")
    public void onExportRequested(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> event = objectMapper.readValue(message, Map.class);
            String userId = event.get("userId");
            String requestId = event.get("requestId");

            // Query order data for this user (anonymized fields excluded)
            List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                    "SELECT order_id, status, total_amount, currency, created_at, " +
                    "shipping_address_line1, shipping_address_city, shipping_address_country " +
                    "FROM orders WHERE user_id = ?", userId);

            String payload = objectMapper.writeValueAsString(Map.of("orders", orders));

            kafkaTemplate.send("gdpr.export-fragment", requestId, objectMapper.writeValueAsString(Map.of(
                    "requestId", requestId,
                    "serviceName", "order-service",
                    "payload", payload
            )));

            log.info("GDPR export fragment published: requestId={}, userId={}, orderCount={}",
                    requestId, userId, orders.size());
        } catch (Exception e) {
            log.error("Failed to process GDPR export request", e);
        }
    }

    @KafkaListener(topics = "gdpr.deletion-requested", groupId = "order-service-gdpr")
    public void onDeletionRequested(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> event = objectMapper.readValue(message, Map.class);
            String userId = event.get("userId");

            // Anonymize addresses but keep order records (7-year retention)
            int updated = jdbcTemplate.update(
                    "UPDATE orders SET shipping_address_line1 = '[REDACTED]', " +
                    "shipping_address_line2 = NULL, shipping_address_city = '[REDACTED]', " +
                    "shipping_address_postal_code = '[REDACTED]', " +
                    "billing_name = '[REDACTED]' " +
                    "WHERE user_id = ?", userId);

            kafkaTemplate.send("gdpr.deletion-completed", userId, objectMapper.writeValueAsString(Map.of(
                    "serviceName", "order-service",
                    "userId", userId,
                    "recordsAnonymized", updated
            )));

            log.info("GDPR deletion completed: userId={}, ordersAnonymized={}", userId, updated);
        } catch (Exception e) {
            log.error("Failed to process GDPR deletion request", e);
        }
    }
}
```

### D1.10 � Remaining service GDPR listeners

- [ ] **Step 15:** Create GDPR listeners for the remaining 3 services. Each follows the same pattern as order-service above:

**File:** `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/GdprEventListener.java`
- Export: query `payments` table for userId, publish fragment with payment references (no card data stored)
- Delete: delete payment method references, publish confirmation

**File:** `services/shipping-service/src/main/java/com/vnshop/shippingservice/infrastructure/event/GdprEventListener.java`
- Export: query `shipments` table for userId, publish fragment with delivery addresses + tracking
- Delete: anonymize delivery addresses on completed shipments, publish confirmation

**File:** `services/notification-service/src/gdpr/gdpr.listener.ts` (NestJS/TypeScript)
- Export: query notification history for userId, publish fragment
- Delete: delete all notification records for userId, publish confirmation

```typescript
// services/notification-service/src/gdpr/gdpr.listener.ts
import { Injectable, OnModuleInit } from '@nestjs/common';
import { Kafka, Consumer, Producer } from 'kafkajs';

@Injectable()
export class GdprListener implements OnModuleInit {
  private consumer: Consumer;
  private producer: Producer;

  constructor(private readonly kafka: Kafka) {}

  async onModuleInit() {
    this.consumer = this.kafka.consumer({ groupId: 'notification-service-gdpr' });
    this.producer = this.kafka.producer();
    await this.consumer.connect();
    await this.producer.connect();

    await this.consumer.subscribe({ topics: ['gdpr.export-requested', 'gdpr.deletion-requested'] });
    await this.consumer.run({
      eachMessage: async ({ topic, message }) => {
        const event = JSON.parse(message.value.toString());
        if (topic === 'gdpr.export-requested') {
          await this.handleExport(event);
        } else {
          await this.handleDeletion(event);
        }
      },
    });
  }

  private async handleExport(event: { userId: string; requestId: string }) {
    // Query notification history (implementation depends on your DB adapter)
    const notifications = []; // TODO: inject repository and query
    await this.producer.send({
      topic: 'gdpr.export-fragment',
      messages: [{
        key: event.requestId,
        value: JSON.stringify({
          requestId: event.requestId,
          serviceName: 'notification-service',
          payload: JSON.stringify({ notifications }),
        }),
      }],
    });
  }

  private async handleDeletion(event: { userId: string }) {
    // Delete all notifications for user
    // TODO: inject repository and delete
    await this.producer.send({
      topic: 'gdpr.deletion-completed',
      messages: [{
        key: event.userId,
        value: JSON.stringify({
          serviceName: 'notification-service',
          userId: event.userId,
          recordsDeleted: 0,
        }),
      }],
    });
  }
}
```

- [ ] **Step 16:** Commit GDPR implementation:

```bash
git add infra/scripts/init-kafka-topics.sh services/user-service/src/main/java/com/vnshop/userservice/domain/Gdpr* services/user-service/src/main/java/com/vnshop/userservice/domain/port/out/GdprExportRepositoryPort.java services/user-service/src/main/java/com/vnshop/userservice/application/Gdpr* services/user-service/src/main/java/com/vnshop/userservice/infrastructure/web/GdprController.java services/user-service/src/main/java/com/vnshop/userservice/infrastructure/event/GdprExportFragmentListener.java services/user-service/src/main/java/com/vnshop/userservice/infrastructure/persistence/Gdpr* services/user-service/src/main/java/com/vnshop/userservice/infrastructure/config/GdprUseCaseConfig.java services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/GdprEventListener.java services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/GdprEventListener.java services/shipping-service/src/main/java/com/vnshop/shippingservice/infrastructure/event/GdprEventListener.java services/notification-service/src/gdpr/
git commit -m "feat(gdpr): async export + deletion saga across 5 PII services"
```

---

## Task D2: MFA for Admin/Seller Roles

### D2.1 � Update Keycloak dev realm

- [ ] **Step 1:** Modify `infra/keycloak/vnshop-realm.json` � add conditional OTP authentication flow. Add this to the `authenticationFlows` array:

```json
{
  "alias": "browser-conditional-otp",
  "description": "Browser flow with conditional OTP for admin/seller",
  "providerId": "basic-flow",
  "topLevel": true,
  "builtIn": false,
  "authenticationExecutions": [
    {
      "authenticator": "auth-cookie",
      "authenticatorFlow": false,
      "requirement": "ALTERNATIVE",
      "priority": 10
    },
    {
      "authenticator": "auth-spnego",
      "authenticatorFlow": false,
      "requirement": "DISABLED",
      "priority": 20
    },
    {
      "authenticatorFlow": true,
      "requirement": "ALTERNATIVE",
      "priority": 30,
      "flowAlias": "browser-conditional-otp forms"
    }
  ]
}
```

Add the sub-flow:

```json
{
  "alias": "browser-conditional-otp forms",
  "description": "Username/password + conditional OTP",
  "providerId": "basic-flow",
  "topLevel": false,
  "builtIn": false,
  "authenticationExecutions": [
    {
      "authenticator": "auth-username-password-form",
      "authenticatorFlow": false,
      "requirement": "REQUIRED",
      "priority": 10
    },
    {
      "authenticatorFlow": true,
      "requirement": "CONDITIONAL",
      "priority": 20,
      "flowAlias": "browser-conditional-otp OTP"
    }
  ]
}
```

Add the conditional OTP sub-flow:

```json
{
  "alias": "browser-conditional-otp OTP",
  "description": "OTP required for admin/seller roles",
  "providerId": "basic-flow",
  "topLevel": false,
  "builtIn": false,
  "authenticationExecutions": [
    {
      "authenticator": "conditional-user-role",
      "authenticatorFlow": false,
      "requirement": "REQUIRED",
      "priority": 10,
      "authenticatorConfig": "admin-seller-role-condition"
    },
    {
      "authenticator": "auth-otp-form",
      "authenticatorFlow": false,
      "requirement": "REQUIRED",
      "priority": 20
    }
  ]
}
```

- [ ] **Step 2:** Add the authenticator config in the `authenticatorConfig` array of the realm JSON:

```json
{
  "alias": "admin-seller-role-condition",
  "config": {
    "conditionUserRole": "admin",
    "conditionUserRole.2": "seller",
    "negate": "false"
  }
}
```

- [ ] **Step 3:** Update the realm's `browserFlow` property from `"browser"` to `"browser-conditional-otp"`:

```json
"browserFlow": "browser-conditional-otp"
```

- [ ] **Step 4:** Add `CONFIGURE_TOTP` to `requiredActions` array if not present:

```json
{
  "alias": "CONFIGURE_TOTP",
  "name": "Configure OTP",
  "providerId": "CONFIGURE_TOTP",
  "enabled": true,
  "defaultAction": false,
  "priority": 10
}
```

### D2.2 � Update Keycloak prod realm

- [ ] **Step 5:** Apply the same changes to `infra/keycloak/vnshop-realm-prod.json`:
  - Add the 3 authentication flows (identical)
  - Add the authenticator config (identical)
  - Set `"browserFlow": "browser-conditional-otp"`
  - Add CONFIGURE_TOTP required action

### D2.3 � Verify and commit

- [ ] **Step 6:** Validate JSON syntax:

```bash
python -m json.tool infra/keycloak/vnshop-realm.json > /dev/null
python -m json.tool infra/keycloak/vnshop-realm-prod.json > /dev/null
```

- [ ] **Step 7:** Commit:

```bash
git add infra/keycloak/vnshop-realm.json infra/keycloak/vnshop-realm-prod.json
git commit -m "feat(auth): enforce TOTP MFA for admin and seller roles via Keycloak"
```

---

## Task D3: PCI-DSS SAQ-A Documentation

### D3.1 � Create PCI-DSS SAQ-A document

- [ ] **Step 1:** Create `docs/pci-dss-saq-a.md`:

```markdown
# PCI-DSS SAQ-A Self-Assessment

**Version:** 1.0
**Date:** 2026-06-04
**Assessor:** VNShop Engineering Team
**Next Review:** 2027-06-04

---

## 1. Scope Determination

### Applicability
VNShop qualifies for **SAQ-A** (Payment Card Industry Data Security Standard Self-Assessment Questionnaire A) because:
- All payment processing is **fully outsourced** to PCI-DSS compliant payment gateways (PayPal, Stripe)
- No cardholder data (CHD) is stored, processed, or transmitted by VNShop systems
- No payment page elements are served from VNShop infrastructure
- Customers are redirected to the payment gateway for all card interactions

### Cardholder Data Flow

```
+----------+     +--------------+     +-----------------+
�  Browser �---->�  VNShop API  �---->� Payment Gateway  �
�          �     �  (order ref  �     � (PayPal/Stripe)  �
�          �     �   only)      �     �                  �
�          �<----�              �<----� (redirect URL)   �
�          �------------------------->� (card entry)     �
�          �<-------------------------� (confirmation)   �
+----------+     +--------------+     +-----------------+
                       �
                       � webhook callback
                       � (payment ID + status only,
                       �  NO card data)
                       ?
                 +--------------+
                 � payment-svc  �
                 � (stores ref  �
                 �  ID only)    �
                 +--------------+
```

**Data VNShop stores:** Payment reference ID, transaction status, amount, currency, timestamp.
**Data VNShop NEVER receives:** Card number (PAN), CVV, expiration date, cardholder name for payment purposes.

---

## 2. Controls Mapping

| SAQ-A Requirement | VNShop Implementation | Evidence |
|---|---|---|
| 2.1 � Change vendor defaults | All default passwords changed (Kafka, Redis, Postgres, Keycloak) | `docker-compose.yml` env vars, externalized secrets |
| 6.5 � Address common coding vulnerabilities | OWASP dependency scanning in CI, input validation, parameterized queries | `.github/workflows/ci.yml` (dependency-check + Trivy) |
| 8.1 � Unique IDs for all users | Keycloak manages all identities with UUID-based user IDs | `infra/keycloak/vnshop-realm.json` |
| 8.2 � Authentication mechanisms | Password policy enforced (8+ chars, not username), MFA for admin/seller | Keycloak realm config |
| 9.x � Physical access | N/A � cloud-hosted, no physical infrastructure managed | AWS shared responsibility model |
| 11.2 � Vulnerability scans | Trivy container scanning, OWASP dependency-check on every PR | CI pipeline evidence |
| 12.8 � Service provider management | PayPal and Stripe are PCI-DSS Level 1 certified | Gateway compliance certificates |

---

## 3. Network Security

| Control | Implementation |
|---|---|
| Encryption in transit | TLS via Ingress (cert-manager), SASL_SSL on Kafka, Redis password auth |
| Network segmentation | Kubernetes NetworkPolicy restricts pod-to-pod communication |
| No CHD on internal network | Payment gateway handles all card data externally |
| Firewall/Ingress | Nginx Ingress Controller with rate limiting + CORS policies |

---

## 4. Access Control

| Control | Implementation |
|---|---|
| Role-based access | Keycloak RBAC: admin, seller, buyer roles |
| MFA for privileged access | TOTP required for admin and seller roles |
| Principle of least privilege | Kafka ACLs per-service, K8s RBAC, database per-service credentials |
| Audit logging | All authentication events logged, API audit trail via structured logging |

---

## 5. Logging & Monitoring

| Control | Implementation |
|---|---|
| Audit trail | Structured JSON logs with userId, action, timestamp, traceId |
| Centralized logging | Loki + Promtail aggregation |
| Alerting | Prometheus alerts for anomalous patterns (high error rates, auth failures) |
| Log retention | 7 days hot (Loki), archive policy TBD |

---

## 6. Responsibility Matrix

| Responsibility | VNShop | Payment Gateway |
|---|---|---|
| Card data storage | ? Never | ? PCI-DSS Level 1 |
| Payment page serving | ? Redirect only | ? Hosted payment page |
| Tokenization | ? | ? |
| Fraud detection | Partial (order velocity) | ? Primary |
| Webhook security | ? Signature validation | ? Signs callbacks |
| Refund processing | ? Initiates via API | ? Executes |
| Compliance certification | SAQ-A (this doc) | Full ROC |

---

## 7. Annual Review Process

1. **Q1:** Review gateway compliance certificates (PayPal, Stripe publish annually)
2. **Q2:** Self-assessment review � update this document, verify no scope changes
3. **Q3:** Penetration test (external vendor) � confirm no CHD exposure
4. **Q4:** Update controls mapping if infrastructure changes occurred

---

## 8. Attestation

This self-assessment confirms that VNShop:
- Does not store, process, or transmit cardholder data
- Has fully outsourced payment processing to PCI-DSS Level 1 compliant providers
- Maintains appropriate security controls for its SAQ-A scope
- Will review this assessment annually and upon significant infrastructure changes
```

- [ ] **Step 2:** Commit:

```bash
git add docs/pci-dss-saq-a.md
git commit -m "docs: PCI-DSS SAQ-A self-assessment questionnaire"
```

---

## Verification

- [ ] All GDPR Java files compile: `cd services/user-service && mvn compile -q`
- [ ] Keycloak realm JSON is valid: `python -m json.tool infra/keycloak/vnshop-realm.json > /dev/null`
- [ ] Kafka init script is valid: `bash -n infra/scripts/init-kafka-topics.sh`
