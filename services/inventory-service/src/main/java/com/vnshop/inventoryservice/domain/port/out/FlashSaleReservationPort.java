package com.vnshop.inventoryservice.domain.port.out;

import com.vnshop.inventoryservice.domain.FlashSaleReservation;
import java.util.Optional;
import java.util.UUID;

public interface FlashSaleReservationPort {
	default boolean reserve(String productId, String buyerId, int quantity) {
		return reserve(productId, buyerId, quantity, UUID.randomUUID().toString());
	}

	boolean reserve(String productId, String buyerId, int quantity, String reservationId);

	void save(FlashSaleReservation reservation);

	Optional<FlashSaleReservation> findById(String reservationId);

	void release(String reservationId);

	long getStock(String productId);
}
