package com.vnshop.apigateway.infrastructure.filter;

import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Cart-service (and any other downstream service that scopes data per-user without
 * parsing the JWT itself) reads the user id from the {@code x-user-id} header.
 * The gateway is the trust boundary, so we inject this header from the validated
 * JWT's {@code sub} claim — downstream services can then trust it as authenticated.
 *
 * <p>Implemented as a {@link GlobalFilter} (Spring Cloud Gateway native) rather
 * than a generic {@code WebFilter}, because gateway routes use a separate request
 * pipeline; only header mutations applied through the gateway's request builder
 * propagate to the upstream HTTP call.
 */
@Component
public class UserIdHeaderFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "x-user-id";

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> {
                var auth = ctx.getAuthentication();
                if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
                    return Mono.empty();
                }
                String sub = jwtAuth.getToken().getSubject();
                if (sub == null || sub.isBlank()) {
                    return Mono.empty();
                }
                return Mono.just(sub);
            })
            .map(sub -> {
                ServerHttpRequest mutated = exchange.getRequest()
                    .mutate()
                    .header(USER_ID_HEADER, sub)
                    .build();
                return exchange.mutate().request(mutated).build();
            })
            .defaultIfEmpty(exchange)
            .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        // Run before the routing filter (which has order Integer.MAX_VALUE - 1) so
        // the mutated headers are visible to the upstream HTTP call.
        return -1;
    }
}
