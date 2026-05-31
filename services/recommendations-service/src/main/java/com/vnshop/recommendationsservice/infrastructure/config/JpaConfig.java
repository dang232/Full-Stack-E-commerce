package com.vnshop.recommendationsservice.infrastructure.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Gates Spring Data JPA repository scanning behind {@link EntityManagerFactory}.
 *
 * <p>Per the monorepo gotcha: {@code @SpringBootTest} context-load tests
 * exclude JPA autoconfig. Without this guard, Spring Data JPA infrastructure
 * (jpaSharedEM_entityManagerFactory, jpaMappingContext) would still try to
 * wire and fail because there is no EntityManagerFactory bean. See
 * {@code services/user-service/.../config/JpaConfig.java} for the original
 * pattern.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(EntityManagerFactory.class)
@EntityScan(basePackages = "com.vnshop.recommendationsservice")
@EnableJpaRepositories(basePackages = "com.vnshop.recommendationsservice")
public class JpaConfig {
}
