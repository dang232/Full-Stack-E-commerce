package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.checkout.CartSnapshot;

public interface CartRepositoryPort {
    CartSnapshot findByCartId(String cartId);
}
