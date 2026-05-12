package com.vnshop.inventoryservice.application;

import com.vnshop.inventoryservice.domain.FlashSaleReservation;
import com.vnshop.inventoryservice.domain.FlashSaleReservation.Status;
import com.vnshop.inventoryservice.domain.port.out.FlashSaleReservationPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

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

	public ReserveFlashSaleResult reserve(ReserveFlashSaleCommand command) {
		FlashSaleReservation reservation = reserveReservation(command);
		String reservationId = reservation.getReservationId() == null ? null : reservation.getReservationId().toString();
		return new ReserveFlashSaleResult(
				reservationId,
				reservation.getStatus().name(),
				reservation.getExpiresAt().toString());
	}

	FlashSaleReservation reserveReservation(ReserveFlashSaleCommand command) {
		Instant reservedAt = clock.instant();
		UUID reservationId = UUID.randomUUID();
		boolean reserved = reservationPort.reserve(command.productId(), command.buyerId(), command.quantity(), reservationId);
		FlashSaleReservation reservation = new FlashSaleReservation(
				reserved ? reservationId : null,
				command.productId(),
				command.buyerId(),
				command.quantity(),
				reserved ? Status.RESERVED : Status.REJECTED,
				reservedAt,
				reservedAt.plus(RESERVATION_TTL));
		if (reserved) {
			reservationPort.save(reservation);
		}
		return reservation;
	}

	public void release(UUID reservationId) {
		reservationPort.release(reservationId);
	}

	public long getStock(String productId) {
		return reservationPort.getStock(productId);
	}
}
