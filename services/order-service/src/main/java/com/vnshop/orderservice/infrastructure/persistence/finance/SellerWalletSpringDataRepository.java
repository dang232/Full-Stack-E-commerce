package com.vnshop.orderservice.infrastructure.persistence.finance;

import org.springframework.data.jpa.repository.JpaRepository;

interface SellerWalletSpringDataRepository extends JpaRepository<SellerWalletJpaEntity, String> {
}
