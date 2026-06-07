package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.port.out.FraudOrderCountPort;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * JPA adapter for the fraud velocity check — counts recent non-cancelled orders
 * for a given buyer within a sliding time window.
 */
@Repository
public class FraudOrderCountJpaAdapter implements FraudOrderCountPort {

    private final OrderJpaSpringDataRepository springDataRepository;

    public FraudOrderCountJpaAdapter(OrderJpaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public long countRecentOrders(String buyerId, Instant since) {
        return springDataRepository.countByBuyerIdAndCreatedAtAfterAndPaymentStatusNot(
                buyerId, since, com.vnshop.orderservice.domain.PaymentStatus.FLAGGED);
    }
}
