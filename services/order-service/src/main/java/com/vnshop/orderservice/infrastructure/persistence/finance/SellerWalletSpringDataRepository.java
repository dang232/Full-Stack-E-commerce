package com.vnshop.orderservice.infrastructure.persistence.finance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerWalletSpringDataRepository extends JpaRepository<SellerWalletJpaEntity, String> {
}
