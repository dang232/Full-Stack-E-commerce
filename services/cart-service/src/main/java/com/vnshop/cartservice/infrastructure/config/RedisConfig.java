package com.vnshop.cartservice.infrastructure.config;

import com.vnshop.cartservice.domain.Cart;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    RedisTemplate<String, Cart> cartRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        Jackson2JsonRedisSerializer<Cart> cartSerializer = new Jackson2JsonRedisSerializer<>(Cart.class);
        RedisTemplate<String, Cart> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(cartSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
