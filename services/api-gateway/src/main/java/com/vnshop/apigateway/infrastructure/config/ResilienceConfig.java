package com.vnshop.apigateway.infrastructure.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.security.Principal;

@Configuration
public class ResilienceConfig {

    /**
     * Per-user rate-limit key. Falls back to the request's remote IP for anonymous
     * traffic (permitAll routes like /products). Reactor's RateLimiter throws NPE
     * if any operator in the chain produces null, so we use flatMap (which lets us
     * return Mono.empty() for null names) instead of map (which chokes on null).
     */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .flatMap(principal -> {
                String name = principal.getName();
                return (name != null && !name.isBlank()) ? Mono.just(name) : Mono.empty();
            })
            .defaultIfEmpty(remoteAddress(exchange));
    }

    private static String remoteAddress(org.springframework.web.server.ServerWebExchange exchange) {
        var addr = exchange.getRequest().getRemoteAddress();
        if (addr == null || addr.getAddress() == null) {
            return "anonymous";
        }
        var host = addr.getAddress().getHostAddress();
        return host != null ? host : "anonymous";
    }
}
