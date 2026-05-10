package com.vnshop.paymentservice.infrastructure.config;

import com.vnshop.paymentservice.application.GetPaymentStatusUseCase;
import com.vnshop.paymentservice.application.HandleVnpayIpnUseCase;
import com.vnshop.paymentservice.application.ProcessPaymentUseCase;
import com.vnshop.paymentservice.application.ledger.LedgerService;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.infrastructure.gateway.MomoProperties;
import com.vnshop.paymentservice.infrastructure.gateway.VnpayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({VnpayProperties.class, MomoProperties.class})
public class UseCaseConfig {
    @Bean
    LedgerService ledgerService(LedgerRepositoryPort ledgerRepositoryPort) {
        return new LedgerService(ledgerRepositoryPort);
    }

    @Bean
    ProcessPaymentUseCase processPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort, PaymentGatewayPort paymentGatewayPort, LedgerService ledgerService) {
        return new ProcessPaymentUseCase(paymentRepositoryPort, paymentGatewayPort, ledgerService);
    }

    @Bean
    GetPaymentStatusUseCase getPaymentStatusUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        return new GetPaymentStatusUseCase(paymentRepositoryPort);
    }

    @Bean
    HandleVnpayIpnUseCase handleVnpayIpnUseCase(PaymentRepositoryPort paymentRepositoryPort, LedgerService ledgerService) {
        return new HandleVnpayIpnUseCase(paymentRepositoryPort, ledgerService);
    }
}
