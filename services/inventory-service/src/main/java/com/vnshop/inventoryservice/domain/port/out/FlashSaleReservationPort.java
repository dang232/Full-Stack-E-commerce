package com.vnshop.inventoryservice.domain.port.out;

import com.vnshop.inventoryservice.domain.FlashSaleReservation;
import java.util.Optional;
import java.util.UUID;

public interface FlashSaleReservationPort {
	default boolean reserve(String productId, String buyerId, int quantity) {
		return reserve(productId, buyerId, quantity, UUID.randomUUID());
	}

	boolean reserve(String productId, String buyerId, int quantity, UUID reservationId);

	void save(FlashSaleReservation reservation);

	Optional<FlashSaleReservation> findById(UUID reservationId);

	void release(UUID reservationId);

	long getStock(String productId);
}
