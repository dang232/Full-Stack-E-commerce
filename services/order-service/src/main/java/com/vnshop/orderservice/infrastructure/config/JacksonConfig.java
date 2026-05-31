package com.vnshop.orderservice.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 dropped Jackson auto-config from the {@code spring-boot-starter-webmvc}
 * starter, so services that need an {@link ObjectMapper} for non-controller
 * serialisation (Kafka outbox, external HTTP DTOs) must publish one themselves.
 *
 * <p>{@link ObjectMapper#findAndRegisterModules()} discovers any Jackson modules
 * on the classpath (jsr310, etc.) at runtime — keeps the bean configuration
 * minimal while still picking up date-time support if the module is added later.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
}
