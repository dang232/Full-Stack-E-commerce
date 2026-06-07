package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.port.out.TaxRateLookupPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TaxRateJpaAdapter implements TaxRateLookupPort {

    private final TaxRateSpringDataRepository repository;

    public TaxRateJpaAdapter(TaxRateSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BigDecimal> findRate(String categoryCode, LocalDate asOf) {
        return repository.findActiveRate(categoryCode, asOf)
                .map(TaxRateJpaEntity::getRate);
    }
}
