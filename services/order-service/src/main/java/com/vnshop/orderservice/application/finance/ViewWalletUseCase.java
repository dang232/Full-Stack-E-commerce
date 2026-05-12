package com.vnshop.orderservice.application.finance;

import com.vnshop.orderservice.domain.finance.SellerWallet;
import com.vnshop.orderservice.domain.finance.port.out.SellerWalletRepositoryPort;

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
