package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.CommissionTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionTierJpaAdapterTest {
    @Mock SellerCommissionTierRepository repository;
    @InjectMocks CommissionTierJpaAdapter adapter;

    @Test
    void returnsConfiguredTier() {
        var entity = new SellerCommissionTierJpaEntity("seller-1", CommissionTier.PREFERRED);
        when(repository.findById("seller-1")).thenReturn(Optional.of(entity));
        assertThat(adapter.findBySellerId("seller-1")).isEqualTo(CommissionTier.PREFERRED);
    }

    @Test
    void returnsStandardWhenNotConfigured() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());
        assertThat(adapter.findBySellerId("unknown")).isEqualTo(CommissionTier.STANDARD);
    }
}
