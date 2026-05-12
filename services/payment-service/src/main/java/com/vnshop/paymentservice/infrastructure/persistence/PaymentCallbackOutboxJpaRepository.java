package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.infrastructure.gateway.PaymentCallbackOutbox;
import com.vnshop.paymentservice.infrastructure.gateway.PaymentCallbackOutboxRecord;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentCallbackOutboxJpaRepository implements PaymentCallbackOutbox {
    private final PaymentCallbackOutboxSpringDataRepository springDataRepository;

    public PaymentCallbackOutboxJpaRepository(PaymentCallbackOutboxSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public PaymentCallbackOutboxRecord save(PaymentCallbackOutboxRecord record) {
        return springDataRepository.save(PaymentCallbackOutboxJpaEntity.fromRecord(record)).toRecord();
    }
}
