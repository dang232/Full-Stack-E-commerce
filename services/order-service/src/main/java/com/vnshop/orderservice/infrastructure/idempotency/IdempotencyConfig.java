package com.vnshop.orderservice.infrastructure.idempotency;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class IdempotencyConfig implements WebMvcConfigurer {
    private final IdempotencyFilter idempotencyFilter;

    public IdempotencyConfig(IdempotencyFilter idempotencyFilter) {
        this.idempotencyFilter = idempotencyFilter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyFilter)
                .addPathPatterns("/orders/**", "/checkout/**");
    }
}
