package com.vnshop.user_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The application package (`com.vnshop.user_service`, with underscore) does not match
 * the package the JPA entities live in (`com.vnshop.userservice`, no underscore), so
 * Spring's default same-package entity scan misses every entity. Pin both scans to
 * `com.vnshop` so EntityManagerFactory and Spring Data both see the JPA layer.
 */
@SpringBootApplication(scanBasePackages = "com.vnshop")
@EntityScan(basePackages = "com.vnshop")
@EnableJpaRepositories(basePackages = "com.vnshop")
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
