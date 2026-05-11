package com.vnshop.orderservice.infrastructure.cart;

import com.vnshop.orderservice.domain.checkout.CartItemSnapshot;
import com.vnshop.orderservice.domain.checkout.CartSnapshot;
import com.vnshop.orderservice.domain.port.out.CartRepositoryPort;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StubCartRepositoryAdapter implements CartRepositoryPort {
    @Override
    public CartSnapshot findByCartId(String cartId) {
        return new CartSnapshot(cartId, List.of(
                new CartItemSnapshot("phase-1-product", "STANDARD", "Phase 1 checkout item", 1, BigDecimal.valueOf(100000))
        ));
    }
}
