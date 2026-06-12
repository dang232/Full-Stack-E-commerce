package com.vnshop.apigateway.infrastructure.filter;

import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    public static final String USER_ROLES_HEADER = "x-user-roles";

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        // First, strip any client-supplied x-user-id and x-user-roles to prevent spoofing
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
            .headers(h -> {
                h.remove(USER_ID_HEADER);
                h.remove(USER_ROLES_HEADER);
            })
            .build();
        exchange = exchange.mutate().request(sanitized).build();

        final ServerWebExchange sanitizedExchange = exchange;
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
                String roles = extractRoles(jwtAuth.getToken());
                return Mono.just(new String[]{sub, roles});
            })
            .map(subAndRoles -> {
                ServerHttpRequest.Builder builder = sanitizedExchange.getRequest()
                    .mutate()
                    .header(USER_ID_HEADER, subAndRoles[0]);
                if (!subAndRoles[1].isEmpty()) {
                    builder.header(USER_ROLES_HEADER, subAndRoles[1]);
                }
                return sanitizedExchange.mutate().request(builder.build()).build();
            })
            .defaultIfEmpty(sanitizedExchange)
            .flatMap(chain::filter);
    }

    @SuppressWarnings("unchecked")
    private String extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return "";
        Object rolesObj = realmAccess.get("roles");
        if (rolesObj instanceof Collection<?> roles) {
            return String.join(",", (Collection<String>) roles);
        }
        return "";
    }

    @Override
    public int getOrder() {
        // Run before the routing filter (which has order Integer.MAX_VALUE - 1) so
        // the mutated headers are visible to the upstream HTTP call.
        return -1;
    }
}
