package com.vnshop.inventoryservice.infrastructure.flash;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class WaitingRoomService {
	private final StringRedisTemplate redisTemplate;
	private final Clock clock;

	@Autowired
	public WaitingRoomService(StringRedisTemplate redisTemplate) {
		this(redisTemplate, Clock.systemUTC());
	}

	WaitingRoomService(StringRedisTemplate redisTemplate, Clock clock) {
		this.redisTemplate = redisTemplate;
		this.clock = clock;
	}

	public void join(String productId, String buyerId) {
		redisTemplate.opsForZSet().add(waitingKey(productId), buyerId, clock.millis());
	}

	public Long rank(String productId, String buyerId) {
		return redisTemplate.opsForZSet().rank(waitingKey(productId), buyerId);
	}

	public void leave(String productId, String buyerId) {
		redisTemplate.opsForZSet().remove(waitingKey(productId), buyerId);
	}

	private String waitingKey(String productId) {
		return "flash:waiting:" + productId;
	}
}
