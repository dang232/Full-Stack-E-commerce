package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import com.vnshop.paymentservice.infrastructure.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LivePaymentGatewayWiringTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LiveGatewayTestConfig.class)
            .withPropertyValues(
                    "payment.mode=live",
                    "payment.vnpay.tmn-code=TESTTMN",
                    "payment.vnpay.hash-secret=vnpay-secret",
                    "payment.vnpay.return-url=https://shop.example/payment/vnpay/return",
                    "payment.vnpay.ipn-url=https://shop.example/payment/vnpay/ipn",
                    "payment.momo.partner-code=MOMOTEST",
                    "payment.momo.access-key=access-key",
                    "payment.momo.secret-key=secret-key",
                    "payment.momo.redirect-url=https://shop.example/payment/momo/return",
                    "payment.momo.ipn-url=https://shop.example/payment/momo/ipn");

    @Test
    void liveModeExposesOnlyLiveRouterAsPaymentGatewayPort() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PaymentGatewayPort.class);
            assertThat(context).hasSingleBean(LivePaymentGateway.class);
            assertThat(context).hasSingleBean(VnpayGateway.class);
            assertThat(context).hasSingleBean(MomoGateway.class);
            assertThat(context.getBean(PaymentGatewayPort.class)).isInstanceOf(LivePaymentGateway.class);
        });
    }

    @Configuration
    @EnableConfigurationProperties({VnpayProperties.class, MomoProperties.class})
    @Import({LivePaymentGateway.class, VnpayGateway.class, MomoGateway.class})
    static class LiveGatewayTestConfig {
        @Bean
        LedgerService ledgerService() {
            return new LedgerService(new NoopLedgerRepository());
        }

        @Bean
        MomoClient momoClient() {
            return new NoopMomoClient();
        }
    }

    private static final class NoopMomoClient implements MomoClient {
        @Override
        public MomoCreateResponse create(MomoCreateRequest request) {
            return new MomoCreateResponse(request.partnerCode(), request.requestId(), request.orderId(), request.amount(), 1777777777777L, "Success", 0, "https://pay.example/" + request.orderId(), "momo://pay/" + request.orderId(), "qr");
        }

        @Override
        public MomoQueryDrResponse query(MomoQueryDrRequest request) {
            return new MomoQueryDrResponse(request.partnerCode(), request.requestId(), request.orderId(), 120000L, 2812345678L, 0, "Success", 1777777777777L);
        }
    }

    private static final class NoopLedgerRepository implements LedgerRepositoryPort {
        @Override
        public LedgerEntry save(LedgerEntry ledgerEntry) {
            return ledgerEntry;
        }

        @Override
        public List<LedgerEntry> findByOrderId(String orderId) {
            return List.of();
        }
    }
}
