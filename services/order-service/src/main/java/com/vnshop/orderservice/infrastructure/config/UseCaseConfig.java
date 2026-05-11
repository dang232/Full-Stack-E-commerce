package com.vnshop.orderservice.infrastructure.config;

import com.vnshop.orderservice.application.AcceptOrderUseCase;
import com.vnshop.orderservice.application.ApproveReturnUseCase;
import com.vnshop.orderservice.application.CancelOrderUseCase;
import com.vnshop.orderservice.application.CompleteReturnUseCase;
import com.vnshop.orderservice.application.CreateOrderUseCase;
import com.vnshop.orderservice.application.DisputeQueryUseCase;
import com.vnshop.orderservice.application.GetDashboardUseCase;
import com.vnshop.orderservice.application.DisputeUseCase;
import com.vnshop.orderservice.application.InvoiceUseCase;
import com.vnshop.orderservice.application.ListOpenDisputesUseCase;
import com.vnshop.orderservice.application.ListOrdersUseCase;
import com.vnshop.orderservice.application.ListPendingOrdersUseCase;
import com.vnshop.orderservice.application.ListReturnsUseCase;
import com.vnshop.orderservice.application.OrderQueryUseCase;
import com.vnshop.orderservice.application.RejectOrderUseCase;
import com.vnshop.orderservice.application.RejectReturnUseCase;
import com.vnshop.orderservice.application.RequestReturnUseCase;
import com.vnshop.orderservice.application.SellerOrderQueryUseCase;
import com.vnshop.orderservice.application.ShipOrderUseCase;
import com.vnshop.orderservice.application.ViewOrderUseCase;
import com.vnshop.orderservice.domain.port.out.DashboardAnalyticsPort;
import com.vnshop.orderservice.domain.port.out.DisputeRepositoryPort;
import com.vnshop.orderservice.domain.port.out.InventoryReservationPort;
import com.vnshop.orderservice.domain.port.out.InvoicePdfRendererPort;
import com.vnshop.orderservice.domain.port.out.InvoiceRepositoryPort;
import com.vnshop.orderservice.domain.port.out.InvoiceStoragePort;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import com.vnshop.orderservice.domain.port.out.PaymentRequestPort;
import com.vnshop.orderservice.domain.port.out.RefundRequestPort;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;
import com.vnshop.orderservice.domain.port.out.ShippingRequestPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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
    OrderQueryUseCase orderQueryUseCase(OrderRepositoryPort orderRepositoryPort) {
        return new OrderQueryUseCase(orderRepositoryPort);
    }

    @Bean
    SellerOrderQueryUseCase sellerOrderQueryUseCase(OrderRepositoryPort orderRepositoryPort) {
        return new SellerOrderQueryUseCase(orderRepositoryPort);
    }

    @Bean
    DisputeQueryUseCase disputeQueryUseCase(DisputeRepositoryPort disputeRepositoryPort) {
        return new DisputeQueryUseCase(disputeRepositoryPort);
    }

    @Bean
    GetDashboardUseCase getDashboardUseCase(DashboardAnalyticsPort analytics) {
        return new GetDashboardUseCase(analytics);
    }

    @Bean
    ListOpenDisputesUseCase listOpenDisputesUseCase(DisputeRepositoryPort disputeRepositoryPort) {
        return new ListOpenDisputesUseCase(disputeRepositoryPort);
    }

    @Bean
    ListOrdersUseCase listOrdersUseCase(OrderRepositoryPort orderRepositoryPort) {
        return new ListOrdersUseCase(orderRepositoryPort);
    }

    @Bean
    ListPendingOrdersUseCase listPendingOrdersUseCase(OrderRepositoryPort orderRepositoryPort) {
        return new ListPendingOrdersUseCase(orderRepositoryPort);
    }

    @Bean
    ListReturnsUseCase listReturnsUseCase(ReturnRepositoryPort returnRepositoryPort) {
        return new ListReturnsUseCase(returnRepositoryPort);
    }

    @Bean
    ViewOrderUseCase viewOrderUseCase(OrderRepositoryPort orderRepositoryPort) {
        return new ViewOrderUseCase(orderRepositoryPort);
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

    @Bean
    InvoiceUseCase invoiceUseCase(
            OrderRepositoryPort orderRepositoryPort,
            InvoiceRepositoryPort invoiceRepositoryPort,
            InvoiceStoragePort invoiceStoragePort,
            InvoicePdfRendererPort invoicePdfRendererPort,
            Clock clock
    ) {
        return new InvoiceUseCase(orderRepositoryPort, invoiceRepositoryPort, invoiceStoragePort, invoicePdfRendererPort, clock);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
