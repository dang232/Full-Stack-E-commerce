package com.vnshop.cartservice.application;

import com.vnshop.cartservice.domain.Cart;
import com.vnshop.cartservice.domain.port.out.CartRepositoryPort;

public class ViewCartUseCase {
    private final CartRepositoryPort cartRepositoryPort;

    public ViewCartUseCase(CartRepositoryPort cartRepositoryPort) {
        this.cartRepositoryPort = cartRepositoryPort;
    }

    public Cart view(String buyerId) {
        return cartRepositoryPort.findByBuyerId(buyerId).orElseGet(() -> new Cart(buyerId));
    }
}
