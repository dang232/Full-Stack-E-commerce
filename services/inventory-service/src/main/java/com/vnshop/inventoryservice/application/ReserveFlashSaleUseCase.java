package com.vnshop.inventoryservice.application;

import com.vnshop.inventoryservice.domain.FlashSaleReservation;
import com.vnshop.inventoryservice.domain.FlashSaleReservation.Status;
import com.vnshop.inventoryservice.domain.port.out.FlashSaleReservationPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReserveFlashSaleUseCase {
	private static final Duration RESERVATION_TTL = Duration.ofMinutes(15);

	private final FlashSaleReservationPort reservationPort;
	private final Clock clock;

	public ReserveFlashSaleUseCase(FlashSaleReservationPort reservationPort) {
		this(reservationPort, Clock.systemUTC());
	}

	ReserveFlashSaleUseCase(FlashSaleReservationPort reservationPort, Clock clock) {
		this.reservationPort = reservationPort;
		this.clock = clock;
	}

	public FlashSaleReservation reserve(String productId, String buyerId, int quantity) {
		Instant reservedAt = clock.instant();
		String reservationId = UUID.randomUUID().toString();
		boolean reserved = reservationPort.reserve(productId, buyerId, quantity, reservationId);
		FlashSaleReservation reservation = new FlashSaleReservation(
				reserved ? reservationId : "SOLD_OUT",
				productId,
				buyerId,
				quantity,
				reserved ? Status.RESERVED : Status.REJECTED,
				reservedAt,
				reservedAt.plus(RESERVATION_TTL));
		if (reserved) {
			reservationPort.save(reservation);
		}
		return reservation;
	}

	public void release(String reservationId) {
		reservationPort.release(reservationId);
	}

	public long getStock(String productId) {
		return reservationPort.getStock(productId);
	}
}
