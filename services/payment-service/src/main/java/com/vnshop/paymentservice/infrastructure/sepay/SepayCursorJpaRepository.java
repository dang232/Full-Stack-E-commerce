package com.vnshop.paymentservice.infrastructure.sepay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "payment.sepay.enabled", havingValue = "true")
public class SepayCursorJpaRepository implements SepayCursorRepository {
    private final SepayCursorSpringData repository;

    public SepayCursorJpaRepository(SepayCursorSpringData repository) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
    }

    @Override
    public Optional<String> readCursor() {
        return repository.findById(1).map(SepayCursorJpaEntity::getLastTxId);
    }

    @Override
    public void writeCursor(String lastTxId) {
        SepayCursorJpaEntity entity = repository.findById(1).orElseGet(SepayCursorJpaEntity::new);
        entity.setLastTxId(lastTxId);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    interface SepayCursorSpringData extends JpaRepository<SepayCursorJpaEntity, Integer> {
    }
}
