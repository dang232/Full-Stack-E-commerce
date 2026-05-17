package com.vnshop.recommendationsservice.infrastructure.config;

import com.vnshop.recommendationsservice.application.FrequentlyBoughtTogetherUseCase;
import com.vnshop.recommendationsservice.application.ProductServicePort;
import com.vnshop.recommendationsservice.application.YouMayAlsoLikeUseCase;
import com.vnshop.recommendationsservice.infrastructure.persistence.CoPurchaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class UseCaseConfig {

    @Bean
    FrequentlyBoughtTogetherUseCase frequentlyBoughtTogetherUseCase(
            CoPurchaseRepository coPurchaseRepository,
            ProductServicePort productServicePort
    ) {
        return new FrequentlyBoughtTogetherUseCase(coPurchaseRepository, productServicePort);
    }

    @Bean
    YouMayAlsoLikeUseCase youMayAlsoLikeUseCase(
            ProductServicePort productServicePort,
            @Value("${vnshop.recommendations.price-proximity-percent:30}") int priceProximityPercent,
            @Value("${vnshop.recommendations.you-may-also-like-candidate-pool:100}") int candidatePool
    ) {
        return new YouMayAlsoLikeUseCase(productServicePort, priceProximityPercent, candidatePool);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
