package com.vnshop.orderservice.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.port.out.RefundRequestPort;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEvent;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEventJpaEntity;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RefundRequestPublisherAdapter implements RefundRequestPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefundRequestPublisherAdapter.class);
    private static final String EVENT_TYPE = "payment.refund_requested";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public RefundRequestPublisherAdapter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void requestRefund(Return orderReturn, Money amount) {
        RefundRequestedEvent event = new RefundRequestedEvent(
                orderReturn.returnId(),
                orderReturn.orderId(),
                orderReturn.subOrderId(),
                orderReturn.buyerId(),
                amount.amount().toPlainString(),
                amount.currency()
        );
        repository.save(OutboxEventJpaEntity.fromDomain(OutboxEvent.pending("Return", orderReturn.returnId(), EVENT_TYPE, toJson(event))));
        LOGGER.info("Refund event staged for return {} order {}", orderReturn.returnId(), orderReturn.orderId());
    }

    private String toJson(RefundRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize refund event " + event.returnId(), exception);
        }
    }

    public record RefundRequestedEvent(String returnId, String orderId, Long subOrderId, String buyerId, String amount, String currency) {
    }
}
