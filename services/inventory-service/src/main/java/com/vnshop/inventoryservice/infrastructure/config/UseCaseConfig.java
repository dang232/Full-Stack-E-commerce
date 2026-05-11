package com.vnshop.inventoryservice.infrastructure.config;

import com.vnshop.inventoryservice.application.ReserveFlashSaleUseCase;
import com.vnshop.inventoryservice.domain.port.out.FlashSaleReservationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    ReserveFlashSaleUseCase reserveFlashSaleUseCase(FlashSaleReservationPort reservationPort) {
        return new ReserveFlashSaleUseCase(reservationPort);
    }
}
