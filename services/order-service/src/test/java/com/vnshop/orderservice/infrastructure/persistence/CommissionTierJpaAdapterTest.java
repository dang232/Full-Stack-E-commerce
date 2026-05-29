package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.CommissionTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionTierJpaAdapterTest {
    @Mock SellerCommissionTierRepository repository;
    @InjectMocks CommissionTierJpaAdapter adapter;

    @Test
    void returnsConfiguredTier() {
        var entity = new SellerCommissionTierJpaEntity("seller-1", CommissionTier.PREFERRED);
        when(repository.findAllById(Set.of("seller-1"))).thenReturn(List.of(entity));
        assertThat(adapter.findBySellerId("seller-1")).isEqualTo(CommissionTier.PREFERRED);
    }

    @Test
    void returnsStandardWhenNotConfigured() {
        when(repository.findAllById(Set.of("unknown"))).thenReturn(List.of());
        assertThat(adapter.findBySellerId("unknown")).isEqualTo(CommissionTier.STANDARD);
    }

    @Test
    void batchLookupReturnsTiersForKnownSellers() {
        var entity1 = new SellerCommissionTierJpaEntity("seller-1", CommissionTier.PREFERRED);
        var entity2 = new SellerCommissionTierJpaEntity("seller-2", CommissionTier.MALL);
        when(repository.findAllById(Set.of("seller-1", "seller-2")))
                .thenReturn(List.of(entity1, entity2));

        Map<String, CommissionTier> result = adapter.findBySellerIds(Set.of("seller-1", "seller-2"));

        assertThat(result).containsEntry("seller-1", CommissionTier.PREFERRED)
                          .containsEntry("seller-2", CommissionTier.MALL);
    }

    @Test
    void batchLookupOmitsMissingSellers() {
        var entity1 = new SellerCommissionTierJpaEntity("seller-1", CommissionTier.VERIFIED);
        when(repository.findAllById(Set.of("seller-1", "seller-unknown")))
                .thenReturn(List.of(entity1));

        Map<String, CommissionTier> result = adapter.findBySellerIds(Set.of("seller-1", "seller-unknown"));

        assertThat(result).containsOnlyKeys("seller-1");
        assertThat(result.getOrDefault("seller-unknown", CommissionTier.STANDARD))
                .isEqualTo(CommissionTier.STANDARD);
    }

    @Test
    void batchLookupReturnsEmptyMapForEmptyInput() {
        when(repository.findAllById(Set.of())).thenReturn(List.of());

        Map<String, CommissionTier> result = adapter.findBySellerIds(Set.of());

        assertThat(result).isEmpty();
    }
}
