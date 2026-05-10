package com.vnshop.cartservice.application;

import com.vnshop.cartservice.domain.Cart;
import com.vnshop.cartservice.domain.port.out.CartRepositoryPort;

public class RemoveItemUseCase {
    private final CartRepositoryPort cartRepositoryPort;

    public RemoveItemUseCase(CartRepositoryPort cartRepositoryPort) {
        this.cartRepositoryPort = cartRepositoryPort;
    }

    public Cart remove(String buyerId, String productId, String variantSku) {
        Cart cart = cartRepositoryPort.findByBuyerId(buyerId).orElseGet(() -> new Cart(buyerId));
        cart.removeItem(productId, variantSku);
        return cartRepositoryPort.save(cart);
    }
}
