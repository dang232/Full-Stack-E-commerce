package com.vnshop.recommendationsservice.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.recommendationsservice.application.CoPurchaseAggregator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OrderEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesEnvelopedPayloadAndForwardsItemsToAggregator() throws Exception {
        CoPurchaseAggregator aggregator = Mockito.mock(CoPurchaseAggregator.class);
        OrderEventListener listener = new OrderEventListener(aggregator, objectMapper);

        // Mirrors the production envelope: outer object contains a `payload`
        // field whose value is a JSON string. See OutboxPublisher#publishEvent
        // and OutboxEvent#payload.
        String inner = """
                {"eventType":"ORDER_CREATED","orderId":"o-1","items":[{"productId":"a"},{"productId":"b"}]}
                """.strip();
        String outer = objectMapper.writeValueAsString(java.util.Map.of(
                "aggregateType", "Order",
                "aggregateId", "o-1",
                "eventType", "ORDER_CREATED",
                "payload", inner
        ));

        listener.onOrderCreated(outer);

        ArgumentCaptor<List<String>> productIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(aggregator).recordOrder(eq("o-1"), productIdsCaptor.capture());
        assertThat(productIdsCaptor.getValue()).containsExactly("a", "b");
    }

    @Test
    void parsesBarePayloadWithoutEnvelope() {
        CoPurchaseAggregator aggregator = Mockito.mock(CoPurchaseAggregator.class);
        OrderEventListener listener = new OrderEventListener(aggregator, objectMapper);

        String bare = """
                {"orderId":"o-2","items":[{"productId":"x"},{"productId":"y"}]}
                """.strip();

        listener.onOrderCreated(bare);

        verify(aggregator).recordOrder(eq("o-2"), org.mockito.ArgumentMatchers.argThat(list ->
                list.size() == 2 && list.contains("x") && list.contains("y")));
    }

    @Test
    void skipsEventsWithoutOrderId() {
        CoPurchaseAggregator aggregator = Mockito.mock(CoPurchaseAggregator.class);
        OrderEventListener listener = new OrderEventListener(aggregator, objectMapper);

        listener.onOrderCreated("{\"items\":[{\"productId\":\"a\"}]}");

        verify(aggregator, never()).recordOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void emptyItemsListYieldsNoCallToAggregatorIfOrderIdMissing() {
        CoPurchaseAggregator aggregator = Mockito.mock(CoPurchaseAggregator.class);
        OrderEventListener listener = new OrderEventListener(aggregator, objectMapper);

        listener.onOrderCreated("{}");

        verify(aggregator, never()).recordOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidJsonIsSwallowedQuietly() {
        CoPurchaseAggregator aggregator = Mockito.mock(CoPurchaseAggregator.class);
        OrderEventListener listener = new OrderEventListener(aggregator, objectMapper);

        listener.onOrderCreated("not-json");

        verify(aggregator, never()).recordOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void itemsWithoutProductIdAreSkipped() {
        CoPurchaseAggregator aggregator = Mockito.mock(CoPurchaseAggregator.class);
        OrderEventListener listener = new OrderEventListener(aggregator, objectMapper);

        String payload = """
                {"orderId":"o-3","items":[{"productId":"a"},{"sellerId":"s"},{"productId":""},{"productId":"c"}]}
                """.strip();

        listener.onOrderCreated(payload);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(aggregator).recordOrder(eq("o-3"), captor.capture());
        assertThat(captor.getValue()).containsExactly("a", "c");
    }

    @Test
    void emptyItemsArrayStillCallsAggregatorWithEmptyList() {
        CoPurchaseAggregator aggregator = Mockito.mock(CoPurchaseAggregator.class);
        OrderEventListener listener = new OrderEventListener(aggregator, objectMapper);

        listener.onOrderCreated("{\"orderId\":\"o-4\",\"items\":[]}");

        verify(aggregator).recordOrder(eq("o-4"), eq(new ArrayList<>()));
    }
}
