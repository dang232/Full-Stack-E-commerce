package com.vnshop.cartservice.application;

import com.vnshop.cartservice.domain.Cart;
import com.vnshop.cartservice.domain.CartItem;
import com.vnshop.cartservice.domain.port.out.CartRepositoryPort;

public class AddToCartUseCase {
    private final CartRepositoryPort cartRepositoryPort;

    public AddToCartUseCase(CartRepositoryPort cartRepositoryPort) {
        this.cartRepositoryPort = cartRepositoryPort;
    }

    public Cart add(String buyerId, CartItem item) {
        validateProduct(item);
        Cart cart = cartRepositoryPort.findByBuyerId(buyerId).orElseGet(() -> new Cart(buyerId));
        cart.addOrIncrement(item);
        return cartRepositoryPort.save(cart);
    }

    private void validateProduct(CartItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item is required");
        }
    }
}
