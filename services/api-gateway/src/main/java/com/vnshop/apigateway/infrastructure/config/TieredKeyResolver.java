package com.vnshop.apigateway.infrastructure.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Resolves a rate-limit key from the incoming exchange.
 *
 * <p>If the request carries a validated JWT (already verified by the Spring
 * Security resource-server filter upstream), the key is {@code "user:<sub>"}
 * so that each authenticated user gets an independent bucket regardless of
 * their source IP.  This prevents CGNAT from conflating multiple users behind
 * a shared IP into one bucket.
 *
 * <p>Anonymous requests (no valid JWT) fall back to
 * {@code "anon:<client-ip>"}, preferring the first value of
 * {@code X-Forwarded-For} so the key is stable behind a load-balancer.
 */
public class TieredKeyResolver implements KeyResolver {

    static final String USER_PREFIX = "user:";
    static final String ANON_PREFIX = "anon:";

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> {
                var auth = ctx.getAuthentication();
                if (auth instanceof JwtAuthenticationToken jwtAuth
                        && Boolean.TRUE.equals(auth.isAuthenticated())) {
                    String sub = jwtAuth.getToken().getSubject();
                    if (sub != null && !sub.isBlank()) {
                        return Mono.just(USER_PREFIX + sub);
                    }
                }
                return Mono.empty();
            })
            .switchIfEmpty(Mono.fromCallable(() -> ANON_PREFIX + resolveClientIp(exchange)));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        List<String> forwarded = exchange.getRequest().getHeaders().get("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            String first = forwarded.get(0);
            // X-Forwarded-For may be "client, proxy1, proxy2" — take the leftmost value
            int comma = first.indexOf(',');
            return (comma > 0 ? first.substring(0, comma) : first).trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }
}
