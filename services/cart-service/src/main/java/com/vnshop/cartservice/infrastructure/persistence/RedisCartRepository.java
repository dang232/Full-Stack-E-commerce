package com.vnshop.cartservice.infrastructure.persistence;

import com.vnshop.cartservice.domain.Cart;
import com.vnshop.cartservice.domain.port.out.CartRepositoryPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class RedisCartRepository implements CartRepositoryPort {
    private static final Duration CART_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Cart> redisTemplate;

    public RedisCartRepository(RedisTemplate<String, Cart> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Cart> findByBuyerId(String buyerId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(buyerId)));
    }

    @Override
    public Cart save(Cart cart) {
        cart.touch();
        redisTemplate.opsForValue().set(key(cart.getBuyerId()), cart, CART_TTL);
        return cart;
    }

    @Override
    public void delete(String buyerId) {
        redisTemplate.delete(key(buyerId));
    }

    private String key(String buyerId) {
        return "cart:" + buyerId;
    }
}
