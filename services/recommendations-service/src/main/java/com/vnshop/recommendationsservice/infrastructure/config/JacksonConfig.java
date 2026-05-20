package com.vnshop.recommendationsservice.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Recommendations-service hits a Spring Boot 4 quirk where
 * {@code JacksonAutoConfiguration} doesn't materialise an {@link ObjectMapper}
 * even though {@code spring-boot-starter-webmvc} is on the classpath. Without
 * one, {@code OrderEventListener} fails to wire and the whole context refuses
 * to start, taking the FE product detail page's recommendation widgets down
 * with it (503 from /recommendations/**).
 *
 * <p>Define a bean explicitly so the listener has something to inject. The
 * {@code @ConditionalOnMissingBean} guard makes this a no-op if the upstream
 * auto-config is ever fixed and starts producing one again.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
