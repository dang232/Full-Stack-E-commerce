package com.vnshop.sellerfinanceservice.infrastructure.config;

import com.vnshop.sellerfinanceservice.application.CreditWalletUseCase;
import com.vnshop.sellerfinanceservice.application.ProcessPayoutUseCase;
import com.vnshop.sellerfinanceservice.application.RequestPayoutUseCase;
import com.vnshop.sellerfinanceservice.domain.CommissionCalculator;
import com.vnshop.sellerfinanceservice.domain.port.out.PayoutRepositoryPort;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    CommissionCalculator commissionCalculator() {
        return new CommissionCalculator();
    }

    @Bean
    CreditWalletUseCase creditWalletUseCase(SellerWalletRepositoryPort walletRepositoryPort, CommissionCalculator commissionCalculator) {
        return new CreditWalletUseCase(walletRepositoryPort, commissionCalculator);
    }

    @Bean
    RequestPayoutUseCase requestPayoutUseCase(SellerWalletRepositoryPort walletRepositoryPort, PayoutRepositoryPort payoutRepositoryPort) {
        return new RequestPayoutUseCase(walletRepositoryPort, payoutRepositoryPort);
    }

    @Bean
    ProcessPayoutUseCase processPayoutUseCase(SellerWalletRepositoryPort walletRepositoryPort, PayoutRepositoryPort payoutRepositoryPort) {
        return new ProcessPayoutUseCase(walletRepositoryPort, payoutRepositoryPort);
    }
}
