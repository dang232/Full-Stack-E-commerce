package com.vnshop.inventoryservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vnshop.inventoryservice.domain.FlashSaleReservation;
import com.vnshop.inventoryservice.domain.port.out.FlashSaleReservationPort;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ReserveFlashSaleUseCaseTest {

	@Test
	void returnsReservationWhenRedisReservesStock() {
		var port = new InMemoryFlashSaleReservationPort(true, 5);
		var useCase = new ReserveFlashSaleUseCase(port);

		FlashSaleReservation reservation = useCase.reserve("product-1", "buyer-1", 2);

		assertThat(reservation.getStatus()).isEqualTo(FlashSaleReservation.Status.RESERVED);
		assertThat(reservation.getProductId()).isEqualTo("product-1");
		assertThat(reservation.getBuyerId()).isEqualTo("buyer-1");
		assertThat(reservation.getQuantity()).isEqualTo(2);
		assertThat(reservation.getReservationId()).isNotNull();
		assertThat(reservation.getExpiresAt()).isAfter(reservation.getReservedAt());
	}

	@Test
	void returnsRejectedReservationWhenRedisRejectsStock() {
		var port = new InMemoryFlashSaleReservationPort(false, 0);
		var useCase = new ReserveFlashSaleUseCase(port);

		FlashSaleReservation reservation = useCase.reserve("product-1", "buyer-1", 2);

		assertThat(reservation.getStatus()).isEqualTo(FlashSaleReservation.Status.REJECTED);
		assertThat(reservation.getReservationId()).isNull();
	}

	private static final class InMemoryFlashSaleReservationPort implements FlashSaleReservationPort {
		private final boolean reserveResult;
		private final long stock;
		private final Map<UUID, FlashSaleReservation> reservations = new ConcurrentHashMap<>();

		private InMemoryFlashSaleReservationPort(boolean reserveResult, long stock) {
			this.reserveResult = reserveResult;
			this.stock = stock;
		}

		@Override
		public boolean reserve(String productId, String buyerId, int quantity, UUID reservationId) {
			return reserveResult;
		}

		@Override
		public void save(FlashSaleReservation reservation) {
			reservations.put(reservation.getReservationId(), reservation);
		}

		@Override
		public Optional<FlashSaleReservation> findById(UUID reservationId) {
			return Optional.ofNullable(reservations.get(reservationId));
		}

		@Override
		public void release(UUID reservationId) {
		}

		@Override
		public long getStock(String productId) {
			return stock;
		}
	}
}
