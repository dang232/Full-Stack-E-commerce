package com.vnshop.orderservice.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxRateSpringDataRepository extends JpaRepository<TaxRateJpaEntity, Long> {

    /**
     * Finds the active tax rate for the given category on the given date.
     * effective_to is nullable — a null effective_to means "still in effect".
     */
    @Query("""
            SELECT t FROM TaxRateJpaEntity t
            WHERE t.categoryCode = :categoryCode
              AND t.effectiveFrom <= :asOf
              AND (t.effectiveTo IS NULL OR t.effectiveTo >= :asOf)
            ORDER BY t.effectiveFrom DESC
            """)
    Optional<TaxRateJpaEntity> findActiveRate(
            @Param("categoryCode") String categoryCode,
            @Param("asOf") LocalDate asOf);
}
