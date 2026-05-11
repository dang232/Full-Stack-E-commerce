package com.vnshop.inventoryservice.infrastructure.flash;

import com.vnshop.inventoryservice.domain.FlashSaleReservation;
import com.vnshop.inventoryservice.domain.port.out.FlashSaleReservationPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RedisLuaFlashSaleGateway implements FlashSaleReservationPort {
	private static final Duration RESERVATION_TTL = Duration.ofMinutes(15);
	private static final String EXPIRATION_INDEX = "flash:reservation:expires";

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<Long> flashReserveScript;
	private final RedisScript<Long> flashReleaseScript;
	private final WaitingRoomService waitingRoomService;

	public RedisLuaFlashSaleGateway(StringRedisTemplate redisTemplate, RedisScript<Long> flashReserveScript,
			RedisScript<Long> flashReleaseScript, WaitingRoomService waitingRoomService) {
		this.redisTemplate = redisTemplate;
		this.flashReserveScript = flashReserveScript;
		this.flashReleaseScript = flashReleaseScript;
		this.waitingRoomService = waitingRoomService;
	}

	@Override
	public boolean reserve(String productId, String buyerId, int quantity, UUID reservationId) {
		Long result = redisTemplate.execute(
				flashReserveScript,
				List.of(stockKey(productId), waitingSetKey(productId)),
				Integer.toString(quantity),
				buyerId);
		if (Long.valueOf(1).equals(result)) {
			waitingRoomService.join(productId, buyerId);
			return true;
		}
		return false;
	}

	@Override
	public void save(FlashSaleReservation reservation) {
		String reservationId = reservation.getReservationId().toString();
		String key = reservationKey(reservation.getReservationId());
		redisTemplate.opsForHash().put(key, "reservationId", reservationId);
		redisTemplate.opsForHash().put(key, "productId", reservation.getProductId());
		redisTemplate.opsForHash().put(key, "buyerId", reservation.getBuyerId());
		redisTemplate.opsForHash().put(key, "quantity", Integer.toString(reservation.getQuantity()));
		redisTemplate.opsForHash().put(key, "status", reservation.getStatus().name());
		redisTemplate.opsForHash().put(key, "reservedAt", reservation.getReservedAt().toString());
		redisTemplate.opsForHash().put(key, "expiresAt", reservation.getExpiresAt().toString());
		redisTemplate.expire(key, RESERVATION_TTL);
		redisTemplate.opsForZSet().add(EXPIRATION_INDEX, reservationId, reservation.getExpiresAt().toEpochMilli());
	}

	@Override
	public Optional<FlashSaleReservation> findById(UUID reservationId) {
		String key = reservationKey(reservationId);
		if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
			return Optional.empty();
		}
		String productId = hashValue(key, "productId");
		String buyerId = hashValue(key, "buyerId");
		String quantity = hashValue(key, "quantity");
		String status = hashValue(key, "status");
		String reservedAt = hashValue(key, "reservedAt");
		String expiresAt = hashValue(key, "expiresAt");
		if (productId == null || buyerId == null || quantity == null || status == null || reservedAt == null || expiresAt == null) {
			return Optional.empty();
		}
		return Optional.of(new FlashSaleReservation(
				reservationId,
				productId,
				buyerId,
				Integer.parseInt(quantity),
				FlashSaleReservation.Status.valueOf(status),
				Instant.parse(reservedAt),
				Instant.parse(expiresAt)));
	}

	@Override
	public void release(UUID reservationId) {
		findById(reservationId).ifPresent(reservation -> {
			String reservationIdValue = reservationId.toString();
			redisTemplate.execute(
					flashReleaseScript,
					List.of(stockKey(reservation.getProductId()), reservationKey(reservationId)),
					Integer.toString(reservation.getQuantity()));
			redisTemplate.opsForZSet().remove(EXPIRATION_INDEX, reservationIdValue);
			waitingRoomService.leave(reservation.getProductId(), reservation.getBuyerId());
		});
	}

	@Override
	public long getStock(String productId) {
		String stock = redisTemplate.opsForValue().get(stockKey(productId));
		return stock == null ? 0 : Long.parseLong(stock);
	}

	@Scheduled(fixedDelayString = "PT1M")
	public void releaseExpiredReservations() {
		var dueReservations = redisTemplate.opsForZSet().rangeByScore(EXPIRATION_INDEX, 0, Instant.now().toEpochMilli());
		if (dueReservations == null || dueReservations.isEmpty()) {
			return;
		}
		dueReservations.stream()
				.map(UUID::fromString)
				.forEach(this::release);
	}

	private String hashValue(String key, String field) {
		Object value = redisTemplate.opsForHash().get(key, field);
		return value == null ? null : value.toString();
	}

	private String stockKey(String productId) {
		return "flash:stock:" + productId;
	}

	private String waitingSetKey(String productId) {
		return "flash:waiting:" + productId;
	}

	private String reservationKey(UUID reservationId) {
		return "flash:reservation:" + reservationId;
	}
}
