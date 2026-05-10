package com.vnshop.orderservice.infrastructure.config;

import com.vnshop.orderservice.application.AcceptOrderUseCase;
import com.vnshop.orderservice.application.ApproveReturnUseCase;
import com.vnshop.orderservice.application.CancelOrderUseCase;
import com.vnshop.orderservice.application.CompleteReturnUseCase;
import com.vnshop.orderservice.application.CreateOrderUseCase;
import com.vnshop.orderservice.application.DisputeUseCase;
import com.vnshop.orderservice.application.RejectOrderUseCase;
import com.vnshop.orderservice.application.RejectReturnUseCase;
import com.vnshop.orderservice.application.RequestReturnUseCase;
import com.vnshop.orderservice.application.ShipOrderUseCase;
import com.vnshop.orderservice.domain.port.out.DisputeRepositoryPort;
import com.vnshop.orderservice.domain.port.out.InventoryReservationPort;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import com.vnshop.orderservice.domain.port.out.PaymentRequestPort;
import com.vnshop.orderservice.domain.port.out.RefundRequestPort;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;
import com.vnshop.orderservice.domain.port.out.ShippingRequestPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    CreateOrderUseCase createOrderUseCase(
            OrderRepositoryPort orderRepositoryPort,
            InventoryReservationPort inventoryReservationPort,
            PaymentRequestPort paymentRequestPort,
            ShippingRequestPort shippingRequestPort,
            OrderEventPublisherPort orderEventPublisherPort
    ) {
        return new CreateOrderUseCase(orderRepositoryPort, inventoryReservationPort, paymentRequestPort, shippingRequestPort, orderEventPublisherPort);
    }

    @Bean
    CancelOrderUseCase cancelOrderUseCase(
            OrderRepositoryPort orderRepositoryPort,
            InventoryReservationPort inventoryReservationPort,
            OrderEventPublisherPort orderEventPublisherPort
    ) {
        return new CancelOrderUseCase(orderRepositoryPort, inventoryReservationPort, orderEventPublisherPort);
    }

    @Bean
    AcceptOrderUseCase acceptOrderUseCase(OrderRepositoryPort orderRepositoryPort, OrderEventPublisherPort orderEventPublisherPort) {
        return new AcceptOrderUseCase(orderRepositoryPort, orderEventPublisherPort);
    }

    @Bean
    RejectOrderUseCase rejectOrderUseCase(
            OrderRepositoryPort orderRepositoryPort,
            InventoryReservationPort inventoryReservationPort,
            OrderEventPublisherPort orderEventPublisherPort
    ) {
        return new RejectOrderUseCase(orderRepositoryPort, inventoryReservationPort, orderEventPublisherPort);
    }

    @Bean
    ShipOrderUseCase shipOrderUseCase(OrderRepositoryPort orderRepositoryPort, OrderEventPublisherPort orderEventPublisherPort) {
        return new ShipOrderUseCase(orderRepositoryPort, orderEventPublisherPort);
    }

    @Bean
    RequestReturnUseCase requestReturnUseCase(OrderRepositoryPort orderRepositoryPort, ReturnRepositoryPort returnRepositoryPort) {
        return new RequestReturnUseCase(orderRepositoryPort, returnRepositoryPort);
    }

    @Bean
    ApproveReturnUseCase approveReturnUseCase(ReturnRepositoryPort returnRepositoryPort) {
        return new ApproveReturnUseCase(returnRepositoryPort);
    }

    @Bean
    RejectReturnUseCase rejectReturnUseCase(ReturnRepositoryPort returnRepositoryPort) {
        return new RejectReturnUseCase(returnRepositoryPort);
    }

    @Bean
    CompleteReturnUseCase completeReturnUseCase(
            ReturnRepositoryPort returnRepositoryPort,
            OrderRepositoryPort orderRepositoryPort,
            RefundRequestPort refundRequestPort
    ) {
        return new CompleteReturnUseCase(returnRepositoryPort, orderRepositoryPort, refundRequestPort);
    }

    @Bean
    DisputeUseCase disputeUseCase(ReturnRepositoryPort returnRepositoryPort, DisputeRepositoryPort disputeRepositoryPort) {
        return new DisputeUseCase(returnRepositoryPort, disputeRepositoryPort);
    }
}
