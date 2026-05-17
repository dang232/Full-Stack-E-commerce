package com.vnshop.couponservice.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CouponRepository extends JpaRepository<CouponJpaEntity, Long> {
    Optional<CouponJpaEntity> findByCode(String code);

    List<CouponJpaEntity> findByActiveTrue();

    /**
     * Atomically validates remaining capacity and increments {@code currentUses}. The
     * predicate {@code current_uses < max_uses} is evaluated by the DB inside the same
     * statement that performs the increment, so two concurrent applies on the last seat
     * cannot both succeed — exactly one update wins, the other returns 0 rows affected.
     *
     * <p>The {@code @Transactional} declaration here keeps the sync point self-contained:
     * the controller stays non-transactional, and any caller — including tests invoking
     * the repository directly — gets a single-statement transaction without having to wrap
     * it themselves. Callers must treat a return of 0 as {@code COUPON_EXHAUSTED}.
     */
    @Modifying
    @Transactional
    @Query("update CouponJpaEntity c set c.currentUses = c.currentUses + 1 "
            + "where c.id = :id and c.currentUses < c.maxUses")
    int tryConsumeUsage(@Param("id") Long id);
}
