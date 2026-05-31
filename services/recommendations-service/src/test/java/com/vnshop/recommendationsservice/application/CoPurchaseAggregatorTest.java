package com.vnshop.recommendationsservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vnshop.recommendationsservice.infrastructure.persistence.CoPurchaseJpaEntity;
import com.vnshop.recommendationsservice.infrastructure.persistence.CoPurchaseRepository;
import com.vnshop.recommendationsservice.infrastructure.persistence.ProcessedOrderJpaEntity;
import com.vnshop.recommendationsservice.infrastructure.persistence.ProcessedOrderRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CoPurchaseAggregatorTest {

    private CoPurchaseRepository coPurchaseRepository;
    private ProcessedOrderRepository processedOrderRepository;
    private InMemoryCoPurchaseStore store;

    @BeforeEach
    void setUp() {
        coPurchaseRepository = Mockito.mock(CoPurchaseRepository.class);
        processedOrderRepository = Mockito.mock(ProcessedOrderRepository.class);
        store = new InMemoryCoPurchaseStore();
        when(coPurchaseRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.getArgument(0))));
        when(coPurchaseRepository.save(any())).thenAnswer(inv -> {
            CoPurchaseJpaEntity entity = inv.getArgument(0);
            store.put(entity);
            return entity;
        });
    }

    @Test
    void recordsAllOrderedPairsBothWays() {
        when(processedOrderRepository.existsById("order-1")).thenReturn(false);

        new CoPurchaseAggregator(coPurchaseRepository, processedOrderRepository)
                .recordOrder("order-1", List.of("a", "b", "c"));

        // 3 distinct products -> 3 unordered pairs -> 6 directed inserts
        verify(coPurchaseRepository, times(6)).save(any());
        ArgumentCaptor<ProcessedOrderJpaEntity> processed = ArgumentCaptor.forClass(ProcessedOrderJpaEntity.class);
        verify(processedOrderRepository).save(processed.capture());
        assertThat(processed.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(store.get(new CoPurchaseJpaEntity.CoPurchaseId("a", "b")).getCoCount()).isEqualTo(1L);
        assertThat(store.get(new CoPurchaseJpaEntity.CoPurchaseId("b", "a")).getCoCount()).isEqualTo(1L);
    }

    @Test
    void incrementsExistingCounters() {
        store.put(new CoPurchaseJpaEntity("a", "b", 4L, Instant.EPOCH));
        store.put(new CoPurchaseJpaEntity("b", "a", 4L, Instant.EPOCH));
        when(processedOrderRepository.existsById("order-2")).thenReturn(false);

        new CoPurchaseAggregator(coPurchaseRepository, processedOrderRepository)
                .recordOrder("order-2", List.of("a", "b"));

        assertThat(store.get(new CoPurchaseJpaEntity.CoPurchaseId("a", "b")).getCoCount()).isEqualTo(5L);
        assertThat(store.get(new CoPurchaseJpaEntity.CoPurchaseId("b", "a")).getCoCount()).isEqualTo(5L);
    }

    @Test
    void deduplicatesProductIdsWithinOneOrder() {
        when(processedOrderRepository.existsById("order-3")).thenReturn(false);

        new CoPurchaseAggregator(coPurchaseRepository, processedOrderRepository)
                .recordOrder("order-3", Arrays.asList("a", "a", "b"));

        // After distinct -> 2 products -> 1 unordered pair -> 2 directed inserts
        verify(coPurchaseRepository, times(2)).save(any());
    }

    @Test
    void skipsAlreadyProcessedOrder() {
        when(processedOrderRepository.existsById("order-replay")).thenReturn(true);

        new CoPurchaseAggregator(coPurchaseRepository, processedOrderRepository)
                .recordOrder("order-replay", List.of("a", "b"));

        verifyNoInteractions(coPurchaseRepository);
    }

    @Test
    void singleItemOrderRecordsProcessedButNoCoPurchases() {
        when(processedOrderRepository.existsById("order-solo")).thenReturn(false);

        new CoPurchaseAggregator(coPurchaseRepository, processedOrderRepository)
                .recordOrder("order-solo", List.of("a"));

        verifyNoInteractions(coPurchaseRepository);
        verify(processedOrderRepository).save(any());
    }

    @Test
    void blankOrderIdIsIgnored() {
        new CoPurchaseAggregator(coPurchaseRepository, processedOrderRepository)
                .recordOrder("  ", List.of("a", "b"));

        verifyNoInteractions(coPurchaseRepository);
        verifyNoInteractions(processedOrderRepository);
    }

    @Test
    void nullProductsListIsIgnored() {
        new CoPurchaseAggregator(coPurchaseRepository, processedOrderRepository)
                .recordOrder("order-null", null);

        verifyNoInteractions(coPurchaseRepository);
        verify(processedOrderRepository).save(any());
    }

    @Test
    void filtersBlankAndNullProductIds() {
        when(processedOrderRepository.existsById("order-blanks")).thenReturn(false);

        new CoPurchaseAggregator(coPurchaseRepository, processedOrderRepository)
                .recordOrder("order-blanks", Arrays.asList(null, "", "a", " ", "b"));

        // After filter -> 2 distinct -> 2 directed inserts
        verify(coPurchaseRepository, times(2)).save(any());
    }

    private static final class InMemoryCoPurchaseStore {
        private final Map<CoPurchaseJpaEntity.CoPurchaseId, CoPurchaseJpaEntity> rows = new HashMap<>();

        CoPurchaseJpaEntity get(CoPurchaseJpaEntity.CoPurchaseId id) {
            return rows.get(id);
        }

        void put(CoPurchaseJpaEntity entity) {
            rows.put(entity.getId(), entity);
        }
    }
}
