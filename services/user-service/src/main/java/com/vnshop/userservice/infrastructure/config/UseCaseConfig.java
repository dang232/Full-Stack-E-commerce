package com.vnshop.userservice.infrastructure.config;

import com.vnshop.userservice.application.ApproveSellerUseCase;
import com.vnshop.userservice.application.ManageAddressUseCase;
import com.vnshop.userservice.application.RegisterBuyerUseCase;
import com.vnshop.userservice.application.RegisterSellerUseCase;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    RegisterBuyerUseCase registerBuyerUseCase(UserRepositoryPort userRepositoryPort) {
        return new RegisterBuyerUseCase(userRepositoryPort);
    }

    @Bean
    ManageAddressUseCase manageAddressUseCase(UserRepositoryPort userRepositoryPort) {
        return new ManageAddressUseCase(userRepositoryPort);
    }

    @Bean
    RegisterSellerUseCase registerSellerUseCase(UserRepositoryPort userRepositoryPort) {
        return new RegisterSellerUseCase(userRepositoryPort);
    }

    @Bean
    ApproveSellerUseCase approveSellerUseCase(UserRepositoryPort userRepositoryPort) {
        return new ApproveSellerUseCase(userRepositoryPort);
    }
}
