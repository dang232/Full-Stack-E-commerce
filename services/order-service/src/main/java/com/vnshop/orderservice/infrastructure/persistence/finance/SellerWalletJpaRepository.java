package com.vnshop.orderservice.infrastructure.persistence.finance;

import com.vnshop.orderservice.domain.finance.SellerWallet;
import com.vnshop.orderservice.domain.finance.port.out.SellerWalletRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SellerWalletJpaRepository implements SellerWalletRepositoryPort {
    private final SellerWalletSpringDataRepository repository;

    public SellerWalletJpaRepository(SellerWalletSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SellerWallet> findBySellerId(String sellerId) {
        return repository.findById(sellerId).map(SellerWalletJpaEntity::toDomain);
    }

    @Override
    public SellerWallet save(SellerWallet wallet) {
        return repository.save(SellerWalletJpaEntity.fromDomain(wallet)).toDomain();
    }
}
