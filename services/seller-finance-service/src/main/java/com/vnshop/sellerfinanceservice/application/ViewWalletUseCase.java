package com.vnshop.sellerfinanceservice.application;

import com.vnshop.sellerfinanceservice.domain.SellerWallet;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;

public class ViewWalletUseCase {

    private final SellerWalletRepositoryPort walletRepository;

    public ViewWalletUseCase(SellerWalletRepositoryPort walletRepository) {
        this.walletRepository = walletRepository;
    }

    public SellerWallet view(String sellerId) {
        return walletRepository.findBySellerId(sellerId)
                .orElseGet(() -> new SellerWallet(sellerId));
    }
}
