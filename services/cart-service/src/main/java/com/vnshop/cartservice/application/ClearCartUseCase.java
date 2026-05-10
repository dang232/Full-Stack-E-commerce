package com.vnshop.cartservice.application;

import com.vnshop.cartservice.domain.Cart;
import com.vnshop.cartservice.domain.port.out.CartRepositoryPort;

public class ClearCartUseCase {
    private final CartRepositoryPort cartRepositoryPort;

    public ClearCartUseCase(CartRepositoryPort cartRepositoryPort) {
        this.cartRepositoryPort = cartRepositoryPort;
    }

    public Cart clear(String buyerId) {
        cartRepositoryPort.delete(buyerId);
        return new Cart(buyerId);
    }
}
