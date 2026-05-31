package com.vnshop.orderservice.domain.saga;

import java.time.Instant;

public record SagaState(
    String sagaId,
    String orderId,
    SagaStatus currentStep,
    Instant createdAt,
    Instant updatedAt
) {}