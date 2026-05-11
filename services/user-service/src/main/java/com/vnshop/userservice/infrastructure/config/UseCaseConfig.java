package com.vnshop.userservice.infrastructure.config;

import com.vnshop.userservice.application.ApproveSellerUseCase;
import com.vnshop.userservice.application.ListPendingSellersUseCase;
import com.vnshop.userservice.application.ManageAddressUseCase;
import com.vnshop.userservice.application.RegisterBuyerUseCase;
import com.vnshop.userservice.application.RegisterSellerUseCase;
import com.vnshop.userservice.application.UpsertBuyerProfileUseCase;
import com.vnshop.userservice.application.ViewBuyerProfileUseCase;
import com.vnshop.userservice.application.ViewSellerProfileUseCase;
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

    @Bean
    ListPendingSellersUseCase listPendingSellersUseCase(UserRepositoryPort userRepositoryPort) {
        return new ListPendingSellersUseCase(userRepositoryPort);
    }

    @Bean
    UpsertBuyerProfileUseCase upsertBuyerProfileUseCase(UserRepositoryPort userRepositoryPort, RegisterBuyerUseCase registerBuyerUseCase) {
        return new UpsertBuyerProfileUseCase(userRepositoryPort, registerBuyerUseCase);
    }

    @Bean
    ViewBuyerProfileUseCase viewBuyerProfileUseCase(UserRepositoryPort userRepositoryPort) {
        return new ViewBuyerProfileUseCase(userRepositoryPort);
    }

    @Bean
    ViewSellerProfileUseCase viewSellerProfileUseCase(UserRepositoryPort userRepositoryPort) {
        return new ViewSellerProfileUseCase(userRepositoryPort);
    }
}
