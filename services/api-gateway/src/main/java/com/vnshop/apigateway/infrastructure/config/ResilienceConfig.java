package com.vnshop.apigateway.infrastructure.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.security.Principal;

@Configuration
public class ResilienceConfig {

    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .map(Principal::getName)
            .switchIfEmpty(Mono.just(exchange.getRequest().getRemoteAddress() == null
                ? "anonymous"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()));
    }
}
