package com.vnshop.userservice.infrastructure.featureflag;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.util.UnleashConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnleashConfig {

    @Value("${unleash.api-url:http://unleash:4242/api}")
    private String apiUrl;

    @Value("${unleash.api-key:default:development.unleash-insecure-client-token}")
    private String apiKey;

    @Value("${unleash.app-name:user-service}")
    private String appName;

    @Bean
    public Unleash unleash() {
        var config = UnleashConfig.builder()
                .appName(appName)
                .unleashAPI(apiUrl)
                .apiKey(apiKey)
                .build();
        return new DefaultUnleash(config);
    }
}
