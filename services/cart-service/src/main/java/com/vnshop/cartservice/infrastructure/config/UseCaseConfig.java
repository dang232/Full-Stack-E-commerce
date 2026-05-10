package com.vnshop.cartservice.infrastructure.config;

import com.vnshop.cartservice.application.AddToCartUseCase;
import com.vnshop.cartservice.application.ClearCartUseCase;
import com.vnshop.cartservice.application.RemoveItemUseCase;
import com.vnshop.cartservice.application.UpdateQuantityUseCase;
import com.vnshop.cartservice.application.ViewCartUseCase;
import com.vnshop.cartservice.domain.port.out.CartRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    AddToCartUseCase addToCartUseCase(CartRepositoryPort cartRepositoryPort) {
        return new AddToCartUseCase(cartRepositoryPort);
    }

    @Bean
    ViewCartUseCase viewCartUseCase(CartRepositoryPort cartRepositoryPort) {
        return new ViewCartUseCase(cartRepositoryPort);
    }

    @Bean
    UpdateQuantityUseCase updateQuantityUseCase(CartRepositoryPort cartRepositoryPort) {
        return new UpdateQuantityUseCase(cartRepositoryPort);
    }

    @Bean
    RemoveItemUseCase removeItemUseCase(CartRepositoryPort cartRepositoryPort) {
        return new RemoveItemUseCase(cartRepositoryPort);
    }

    @Bean
    ClearCartUseCase clearCartUseCase(CartRepositoryPort cartRepositoryPort) {
        return new ClearCartUseCase(cartRepositoryPort);
    }
}
