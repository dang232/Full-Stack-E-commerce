package com.vnshop.sellerfinanceservice.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 dropped Jackson auto-config from {@code spring-boot-starter-webmvc};
 * publish an {@link ObjectMapper} explicitly so adapter-layer collaborators
 * (Kafka outbox, external HTTP clients) can autowire it.
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
