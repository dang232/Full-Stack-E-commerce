package com.vnshop.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.application.projection.OrderProjector;
import com.vnshop.orderservice.application.query.OrderQueryHandler;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import com.vnshop.orderservice.domain.coupon.CouponUsageRepository;
import com.vnshop.orderservice.domain.finance.port.out.PayoutRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerTransactionRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerWalletRepositoryPort;
import com.vnshop.orderservice.domain.port.out.DashboardAnalyticsPort;
import com.vnshop.orderservice.domain.port.out.DisputeRepositoryPort;
import com.vnshop.orderservice.domain.port.out.InventoryReservationPort;
import com.vnshop.orderservice.domain.port.out.InvoicePdfRendererPort;
import com.vnshop.orderservice.domain.port.out.InvoiceRepositoryPort;
import com.vnshop.orderservice.domain.port.out.InvoiceStoragePort;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.PaymentRequestPort;
import com.vnshop.orderservice.domain.port.out.RefundRequestPort;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;
import com.vnshop.orderservice.domain.port.out.ShippingRequestPort;
import com.vnshop.orderservice.infrastructure.idempotency.ProcessedEventRepository;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEventRepository;
import com.vnshop.orderservice.infrastructure.persistence.OrderJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
})
class OrderServiceApplicationTests {

	@MockitoBean
	private OrderProjector orderProjector;

	@MockitoBean
	private OrderQueryHandler orderQueryHandler;

	@MockitoBean
	private OrderJpaRepository orderJpaRepository;

	@MockitoBean
	private DashboardAnalyticsPort dashboardAnalyticsPort;

	@MockitoBean
	private DisputeRepositoryPort disputeRepositoryPort;

	@MockitoBean
	private ReturnRepositoryPort returnRepositoryPort;

	@MockitoBean
	private InvoiceRepositoryPort invoiceRepositoryPort;

	@MockitoBean
	private InvoiceStoragePort invoiceStoragePort;

	@MockitoBean
	private InvoicePdfRendererPort invoicePdfRendererPort;

	@MockitoBean
	private InventoryReservationPort inventoryReservationPort;

	@MockitoBean
	private PaymentRequestPort paymentRequestPort;

	@MockitoBean
	private ShippingRequestPort shippingRequestPort;

	@MockitoBean
	private OrderEventPublisherPort orderEventPublisherPort;

	@MockitoBean
	private RefundRequestPort refundRequestPort;

	@MockitoBean
	private SellerWalletRepositoryPort sellerWalletRepositoryPort;

	@MockitoBean
	private SellerTransactionRepositoryPort sellerTransactionRepositoryPort;

	@MockitoBean
	private PayoutRepositoryPort payoutRepositoryPort;

	@MockitoBean
	private ProcessedEventRepository processedEventRepository;

	@MockitoBean
	private OutboxEventRepository outboxEventRepository;

	@MockitoBean
	private CouponRepository couponRepository;

	@MockitoBean
	private CouponUsageRepository couponUsageRepository;

	@MockitoBean
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

}
