package com.vnshop.userservice.infrastructure.config;

import com.vnshop.userservice.infrastructure.web.CsrfProtectionFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                // Double-submit CSRF protection for cookie-authenticated /auth endpoints.
                // Applied before the authentication filter so unauthenticated CSRF-invalid
                // requests are rejected early without touching the auth layer.
                .addFilterBefore(new CsrfProtectionFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        // Public read endpoints for the storefront SellerShowcase + SellerDetailPage.
                        // /sellers/{id} matches a single path segment by default, which is what
                        // we want — /sellers/me/products and friends fall through to authenticated().
                        // Bank details only flow through /sellers/me + /sellers/register, both
                        // of which fall through to .anyRequest().authenticated() below.
                        .requestMatchers(HttpMethod.GET, "/sellers", "/sellers/{id}").permitAll()
                        // Batch buyer public-profile lookup. Other services
                        // (e.g. product-service rendering reviewer names on
                        // the Reviews tab) call this with no JWT — the
                        // response only carries display name + avatar, the
                        // same surface that already shows publicly elsewhere.
                        .requestMatchers(HttpMethod.GET, "/users/public-profiles").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
