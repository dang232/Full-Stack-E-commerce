package com.vnshop.apigateway.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.GET, "/products/**", "/categories/**", "/search/**", "/health").permitAll()
                .pathMatchers("/auth/**", "/payment/*/callback", "/payment/*/ipn").permitAll()
                .pathMatchers("/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated()
            )
            // The SPA acquires tokens directly from Keycloak (PKCE) and sends them as Bearer.
            // No oauth2Login (no client registration) — gateway is purely a JWT-validating proxy.
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .build();
    }

    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return new ReactiveJwtAuthenticationConverterAdapter(jwt -> {
            Collection<GrantedAuthority> authorities = Stream.concat(
                    realmRoles(jwt).stream(),
                    scopeAuthorities(jwt).stream()
                )
                .collect(Collectors.toSet());
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        });
    }

    private Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toSet());
    }

    private Collection<GrantedAuthority> scopeAuthorities(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return Stream.of(scope.split(" "))
            .filter(value -> !value.isBlank())
            .map(value -> new SimpleGrantedAuthority("SCOPE_" + value))
            .collect(Collectors.toSet());
    }
}
