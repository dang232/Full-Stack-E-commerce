package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.infrastructure.gateway.PaymentCallbackOutbox;
import com.vnshop.paymentservice.infrastructure.gateway.PaymentCallbackOutboxRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public List<PaymentCallbackOutboxRecord> findUnpublished(int limit) {
        return springDataRepository
                .findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, limit))
                .stream()
                .map(PaymentCallbackOutboxJpaEntity::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(Long id) {
        springDataRepository.findById(id).ifPresent(entity -> {
            if (!entity.isPublished()) {
                entity.markPublished(Instant.now());
                springDataRepository.save(entity);
            }
        });
    }
}
