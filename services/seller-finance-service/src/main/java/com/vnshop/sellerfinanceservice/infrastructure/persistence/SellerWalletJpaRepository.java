package com.vnshop.sellerfinanceservice.infrastructure.persistence;

import com.vnshop.sellerfinanceservice.domain.SellerWallet;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
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

    interface SellerWalletSpringDataRepository extends JpaRepository<SellerWalletJpaEntity, String> {
    }
}
