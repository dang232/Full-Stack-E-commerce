package com.vnshop.cartservice.domain.port.out;

import com.vnshop.cartservice.domain.Cart;

import java.util.Optional;

public interface CartRepositoryPort {
    Optional<Cart> findByBuyerId(String buyerId);

    Cart save(Cart cart);

    void delete(String buyerId);
}
