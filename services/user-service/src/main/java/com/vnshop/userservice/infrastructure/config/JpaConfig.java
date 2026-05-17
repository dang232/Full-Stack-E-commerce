package com.vnshop.userservice.infrastructure.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The application package (`com.vnshop.user_service`, with underscore) does not match
 * the package the JPA entities live in (`com.vnshop.userservice`, no underscore), so
 * Spring's default same-package entity scan misses every entity. Pin both scans to
 * `com.vnshop` so EntityManagerFactory and Spring Data both see the JPA layer.
 *
 * <p>Guarded by {@link ConditionalOnBean} on {@link EntityManagerFactory} so that
 * test contexts which exclude JPA autoconfiguration do not pull in Spring Data JPA
 * infrastructure (jpaSharedEM_entityManagerFactory, jpaMappingContext) that would
 * otherwise fail to wire without an EntityManagerFactory bean.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(EntityManagerFactory.class)
@EntityScan(basePackages = "com.vnshop")
@EnableJpaRepositories(basePackages = "com.vnshop")
public class JpaConfig {
}
