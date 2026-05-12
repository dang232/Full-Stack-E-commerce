package com.vnshop.orderservice.infrastructure.persistence.coupon;

import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponId;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CouponJpaRepository implements CouponRepository {
    private final CouponJpaSpringDataRepository springDataRepository;

    public CouponJpaRepository(CouponJpaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Coupon> findById(CouponId id) {
        return springDataRepository.findById(id.value()).map(CouponJpaEntity::toDomain);
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
        return springDataRepository.findByCode(Coupon.normalizeCode(code)).map(CouponJpaEntity::toDomain);
    }

    @Override
    public Coupon save(Coupon coupon) {
        return springDataRepository.save(CouponJpaEntity.fromDomain(coupon)).toDomain();
    }

    @Override
    public List<Coupon> findActive() {
        LocalDateTime now = LocalDateTime.now();
        return springDataRepository.findByActiveTrueAndValidFromLessThanEqualAndValidUntilGreaterThanEqual(now, now)
                .stream()
                .map(CouponJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Coupon> findAll() {
        return springDataRepository.findAll().stream().map(CouponJpaEntity::toDomain).toList();
    }

    @Override
    public boolean existsByCode(String code) {
        return springDataRepository.existsByCode(Coupon.normalizeCode(code));
    }
}
