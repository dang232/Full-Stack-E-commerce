package com.vnshop.orderservice.infrastructure.config;

import com.vnshop.orderservice.application.AcceptOrderUseCase;
import com.vnshop.orderservice.application.ApproveReturnUseCase;
import com.vnshop.orderservice.application.CalculateCheckoutUseCase;
import com.vnshop.orderservice.application.CancelOrderUseCase;
import com.vnshop.orderservice.application.CompleteReturnUseCase;
import com.vnshop.orderservice.application.CreateOrderUseCase;
import com.vnshop.orderservice.application.DisputeQueryUseCase;
import com.vnshop.orderservice.application.DisputeUseCase;
import com.vnshop.orderservice.application.GetDashboardUseCase;
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
import com.vnshop.orderservice.application.coupon.ApplyCouponUseCase;
import com.vnshop.orderservice.application.coupon.CreateCouponUseCase;
import com.vnshop.orderservice.application.coupon.ListActiveCouponsUseCase;
import com.vnshop.orderservice.application.coupon.ReleaseCouponUsageUseCase;
import com.vnshop.orderservice.application.coupon.ValidateCouponUseCase;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import com.vnshop.orderservice.domain.coupon.CouponUsageRepository;
import com.vnshop.orderservice.domain.coupon.CouponValidator;
import com.vnshop.orderservice.domain.port.out.CartRepositoryPort;
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
import com.vnshop.orderservice.application.finance.CreditWalletUseCase;
import com.vnshop.orderservice.application.finance.ListPayoutsUseCase;
import com.vnshop.orderservice.application.finance.ProcessPayoutUseCase;
import com.vnshop.orderservice.application.finance.RequestPayoutUseCase;
import com.vnshop.orderservice.application.finance.ViewWalletUseCase;
import com.vnshop.orderservice.domain.finance.CommissionCalculator;
import com.vnshop.orderservice.domain.finance.port.out.PayoutRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerTransactionRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerWalletRepositoryPort;
import java.time.Clock;
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
    CalculateCheckoutUseCase calculateCheckoutUseCase(CartRepositoryPort cartRepositoryPort) {
        return new CalculateCheckoutUseCase(cartRepositoryPort);
    }

    @Bean
    CouponValidator couponValidator(CouponRepository couponRepository, CouponUsageRepository couponUsageRepository, Clock clock) {
        return new CouponValidator(couponRepository, couponUsageRepository, clock);
    }

    @Bean
    CreateCouponUseCase createCouponUseCase(CouponRepository couponRepository) {
        return new CreateCouponUseCase(couponRepository);
    }

    @Bean
    ValidateCouponUseCase validateCouponUseCase(CouponValidator couponValidator) {
        return new ValidateCouponUseCase(couponValidator);
    }

    @Bean
    ApplyCouponUseCase applyCouponUseCase(CouponValidator couponValidator, CouponRepository couponRepository, CouponUsageRepository couponUsageRepository) {
        return new ApplyCouponUseCase(couponValidator, couponRepository, couponUsageRepository);
    }

    @Bean
    ListActiveCouponsUseCase listActiveCouponsUseCase(CouponRepository couponRepository) {
        return new ListActiveCouponsUseCase(couponRepository);
    }

    @Bean
    ReleaseCouponUsageUseCase releaseCouponUsageUseCase(CouponRepository couponRepository, CouponUsageRepository couponUsageRepository) {
        return new ReleaseCouponUsageUseCase(couponRepository, couponUsageRepository);
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
            OrderEventPublisherPort orderEventPublisherPort,
            ReleaseCouponUsageUseCase releaseCouponUsageUseCase
    ) {
        return new CancelOrderUseCase(orderRepositoryPort, inventoryReservationPort, orderEventPublisherPort, releaseCouponUsageUseCase);
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
    CommissionCalculator commissionCalculator(CommissionCalculator.RateProvider rateProvider) {
        return new CommissionCalculator(rateProvider);
    }

    @Bean
    CreditWalletUseCase creditWalletUseCase(
            SellerWalletRepositoryPort walletRepositoryPort,
            SellerTransactionRepositoryPort transactionRepositoryPort,
            CommissionCalculator commissionCalculator
    ) {
        return new CreditWalletUseCase(walletRepositoryPort, transactionRepositoryPort, commissionCalculator);
    }

    @Bean
    ViewWalletUseCase viewWalletUseCase(SellerWalletRepositoryPort walletRepositoryPort) {
        return new ViewWalletUseCase(walletRepositoryPort);
    }

    @Bean
    RequestPayoutUseCase requestPayoutUseCase(SellerWalletRepositoryPort walletRepositoryPort, PayoutRepositoryPort payoutRepositoryPort) {
        return new RequestPayoutUseCase(walletRepositoryPort, payoutRepositoryPort);
    }

    @Bean
    ListPayoutsUseCase listPayoutsUseCase(PayoutRepositoryPort payoutRepositoryPort) {
        return new ListPayoutsUseCase(payoutRepositoryPort);
    }

    @Bean
    ProcessPayoutUseCase processPayoutUseCase(
            SellerWalletRepositoryPort walletRepositoryPort,
            PayoutRepositoryPort payoutRepositoryPort,
            SellerTransactionRepositoryPort transactionRepositoryPort
    ) {
        return new ProcessPayoutUseCase(walletRepositoryPort, payoutRepositoryPort, transactionRepositoryPort);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
