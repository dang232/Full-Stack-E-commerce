package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.saga.SagaState;
import java.util.Optional;

public interface SagaStateRepository {
    SagaState save(SagaState sagaState);
    Optional<SagaState> findBySagaId(String sagaId);
    Optional<SagaState> findByOrderId(String orderId);
}