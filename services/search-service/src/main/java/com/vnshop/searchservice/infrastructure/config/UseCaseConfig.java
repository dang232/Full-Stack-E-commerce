package com.vnshop.searchservice.infrastructure.config;

import com.vnshop.searchservice.application.SearchProductsUseCase;
import com.vnshop.searchservice.application.SearchRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    SearchProductsUseCase searchProductsUseCase(SearchRepository searchRepository) {
        return new SearchProductsUseCase(searchRepository);
    }
}
