package com.vnshop.sellerfinanceservice.infrastructure.config;

import com.vnshop.sellerfinanceservice.application.CreditWalletUseCase;
import com.vnshop.sellerfinanceservice.application.GetSellerPayoutsUseCase;
import com.vnshop.sellerfinanceservice.application.ListPayoutsUseCase;
import com.vnshop.sellerfinanceservice.application.ProcessPayoutUseCase;
import com.vnshop.sellerfinanceservice.application.RequestPayoutUseCase;
import com.vnshop.sellerfinanceservice.application.ViewWalletUseCase;
import com.vnshop.sellerfinanceservice.domain.CommissionCalculator;
import com.vnshop.sellerfinanceservice.domain.port.out.PayoutRepositoryPort;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    CommissionCalculator commissionCalculator(CommissionRateConfig rateConfig) {
        return new CommissionCalculator(rateConfig);
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
    ListPayoutsUseCase listPayoutsUseCase(PayoutRepositoryPort payoutRepositoryPort) {
        return new ListPayoutsUseCase(payoutRepositoryPort);
    }

    @Bean
    GetSellerPayoutsUseCase getSellerPayoutsUseCase(PayoutRepositoryPort payoutRepositoryPort) {
        return new GetSellerPayoutsUseCase(payoutRepositoryPort);
    }

    @Bean
    ViewWalletUseCase viewWalletUseCase(SellerWalletRepositoryPort walletRepositoryPort) {
        return new ViewWalletUseCase(walletRepositoryPort);
    }

    @Bean
    ProcessPayoutUseCase processPayoutUseCase(SellerWalletRepositoryPort walletRepositoryPort, PayoutRepositoryPort payoutRepositoryPort) {
        return new ProcessPayoutUseCase(walletRepositoryPort, payoutRepositoryPort);
    }
}
