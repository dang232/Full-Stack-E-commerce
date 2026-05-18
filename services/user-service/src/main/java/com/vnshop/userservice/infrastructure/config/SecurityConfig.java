package com.vnshop.userservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        // Public read endpoints for the storefront SellerShowcase + SellerDetailPage.
                        // /sellers/{id} matches a single path segment by default, which is what
                        // we want — /sellers/me/products and friends fall through to authenticated().
                        // Bank details only flow through /sellers/me + /sellers/register, both
                        // of which fall through to .anyRequest().authenticated() below.
                        .requestMatchers(HttpMethod.GET, "/sellers", "/sellers/{id}").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
