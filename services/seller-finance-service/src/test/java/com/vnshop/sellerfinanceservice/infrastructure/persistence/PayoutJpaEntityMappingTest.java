package com.vnshop.sellerfinanceservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.vnshop.sellerfinanceservice.domain.Payout;
import com.vnshop.sellerfinanceservice.domain.PayoutStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayoutJpaEntityMappingTest {

    @Test
    void fromDomainPreservesCreatedAtSoTheRoundTripDoesNotLoseTheTimestamp() {
        Instant createdAt = Instant.parse("2026-05-14T00:00:00Z");
        Payout pending = new Payout(
                UUID.randomUUID(),
                "seller-1",
                new BigDecimal("125.50"),
                PayoutStatus.PENDING,
                createdAt);

        PayoutJpaEntity entity = PayoutJpaEntity.fromDomain(pending);

        assertThat(entity.getCreatedAt())
                .as("entity must carry the domain createdAt — JPA save() on an existing row uses merge(), "
                        + "which copies field state from the detached entity. A null createdAt here makes "
                        + "the post-merge toDomain() call throw 'createdAt is required'.")
                .isEqualTo(createdAt);
    }

    @Test
    void fromDomainThenToDomainPreservesCompleteAuditFields() {
        UUID payoutId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-14T00:00:00Z");
        Instant completedAt = Instant.parse("2026-05-24T08:30:00Z");
        Payout completed = new Payout(
                payoutId,
                "seller-1",
                new BigDecimal("125.50"),
                PayoutStatus.COMPLETED,
                createdAt,
                "admin-42",
                completedAt);

        Payout roundTripped = PayoutJpaEntity.fromDomain(completed).toDomain();

        assertThat(roundTripped.payoutId()).isEqualTo(payoutId);
        assertThat(roundTripped.sellerId()).isEqualTo("seller-1");
        assertThat(roundTripped.amount()).isEqualByComparingTo("125.50");
        assertThat(roundTripped.status()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(roundTripped.createdAt()).isEqualTo(createdAt);
        assertThat(roundTripped.completedBy()).isEqualTo("admin-42");
        assertThat(roundTripped.completedAt()).isEqualTo(completedAt);
    }
}
