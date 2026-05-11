package com.vnshop.searchservice.infrastructure.config;

import com.vnshop.searchservice.application.SearchProductsUseCase;
import com.vnshop.searchservice.infrastructure.persistence.ProductReadModelRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    SearchProductsUseCase searchProductsUseCase(ProductReadModelRepository productReadModelRepository) {
        return new SearchProductsUseCase(productReadModelRepository);
    }
}
