package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.saga.SagaState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SagaStateRepository {
    SagaState save(SagaState sagaState);
    Optional<SagaState> findBySagaId(String sagaId);
    Optional<SagaState> findByOrderId(String orderId);
    List<SagaState> findCompensatingUpdatedBefore(Instant cutoff);
}
