package com.vnshop.orderservice.domain.finance.port.out;

import com.vnshop.orderservice.domain.finance.SellerWallet;

import java.util.Optional;

public interface SellerWalletRepositoryPort {
    Optional<SellerWallet> findBySellerId(String sellerId);

    SellerWallet save(SellerWallet wallet);
}
