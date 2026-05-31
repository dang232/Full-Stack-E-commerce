package com.vnshop.orderservice.infrastructure.persistence.coupon;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponJpaSpringDataRepository extends JpaRepository<CouponJpaEntity, UUID> {
    Optional<CouponJpaEntity> findByCode(String code);

    boolean existsByCode(String code);

    List<CouponJpaEntity> findByActiveTrueAndValidFromLessThanEqualAndValidUntilGreaterThanEqual(LocalDateTime validFrom, LocalDateTime validUntil);
}
