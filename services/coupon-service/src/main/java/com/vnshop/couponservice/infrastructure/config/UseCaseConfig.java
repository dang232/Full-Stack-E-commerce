package com.vnshop.couponservice.infrastructure.config;

import com.vnshop.couponservice.application.ApplyCouponUseCase;
import com.vnshop.couponservice.application.DeactivateCouponUseCase;
import com.vnshop.couponservice.application.IssueCouponUseCase;
import com.vnshop.couponservice.application.ListCouponsUseCase;
import com.vnshop.couponservice.application.UpdateCouponUseCase;
import com.vnshop.couponservice.application.ValidateCouponUseCase;
import com.vnshop.couponservice.domain.port.out.CouponRepository;
import com.vnshop.couponservice.domain.port.out.CouponUsagePort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the application use cases. Mirrors the pattern used in
 * {@code orderservice.infrastructure.config.UseCaseConfig} — keeps the
 * application layer free of Spring annotations and the wiring discoverable
 * in one place.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public IssueCouponUseCase issueCouponUseCase(CouponRepository repository, Clock clock) {
        return new IssueCouponUseCase(repository, clock);
    }

    @Bean
    public UpdateCouponUseCase updateCouponUseCase(CouponRepository repository) {
        return new UpdateCouponUseCase(repository);
    }

    @Bean
    public DeactivateCouponUseCase deactivateCouponUseCase(CouponRepository repository) {
        return new DeactivateCouponUseCase(repository);
    }

    @Bean
    public ValidateCouponUseCase validateCouponUseCase(CouponRepository repository) {
        return new ValidateCouponUseCase(repository);
    }

    @Bean
    public ApplyCouponUseCase applyCouponUseCase(CouponRepository repository, CouponUsagePort usagePort) {
        return new ApplyCouponUseCase(repository, usagePort);
    }

    @Bean
    public ListCouponsUseCase listCouponsUseCase(CouponRepository repository) {
        return new ListCouponsUseCase(repository);
    }
}
