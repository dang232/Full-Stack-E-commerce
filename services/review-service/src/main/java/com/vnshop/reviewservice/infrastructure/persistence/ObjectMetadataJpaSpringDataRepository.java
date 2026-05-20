package com.vnshop.reviewservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Top-level Spring Data JPA repository interface. Was nested inside
 * {@link ObjectMetadataJpaRepository} but Spring Boot 4's repository
 * scanner doesn't reliably pick up package-private inner interfaces
 * inside {@code @Repository} classes — it boots fine on cached
 * dev-time classpaths and fails on fresh container rebuilds.
 * Extracted to a top-level file so the scanner finds it deterministically.
 */
public interface ObjectMetadataJpaSpringDataRepository extends JpaRepository<ObjectMetadataJpaEntity, String> {
}
