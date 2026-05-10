package com.vnshop.cartservice.application;

import com.vnshop.cartservice.domain.Cart;
import com.vnshop.cartservice.domain.port.out.CartRepositoryPort;

public class UpdateQuantityUseCase {
    private final CartRepositoryPort cartRepositoryPort;

    public UpdateQuantityUseCase(CartRepositoryPort cartRepositoryPort) {
        this.cartRepositoryPort = cartRepositoryPort;
    }

    public Cart update(String buyerId, String productId, String variantSku, int quantity) {
        if (quantity < 0 || quantity > 99) {
            throw new IllegalArgumentException("quantity must be between 0 and 99");
        }
        Cart cart = cartRepositoryPort.findByBuyerId(buyerId).orElseGet(() -> new Cart(buyerId));
        cart.updateQuantity(productId, variantSku, quantity);
        return cartRepositoryPort.save(cart);
    }
}
