package com.vnshop.inventoryservice.domain;

import java.time.Instant;
import java.util.UUID;

public class FlashSaleReservation {
	public enum Status {
		RESERVED,
		REJECTED,
		EXPIRED
	}

	private UUID reservationId;
	private String productId;
	private String buyerId;
	private int quantity;
	private Status status;
	private Instant reservedAt;
	private Instant expiresAt;

	public FlashSaleReservation() {
	}

	public FlashSaleReservation(UUID reservationId, String productId, String buyerId, int quantity, Status status,
			Instant reservedAt, Instant expiresAt) {
		this.reservationId = reservationId;
		this.productId = productId;
		this.buyerId = buyerId;
		this.quantity = quantity;
		this.status = status;
		this.reservedAt = reservedAt;
		this.expiresAt = expiresAt;
	}

	public UUID getReservationId() {
		return reservationId;
	}

	public void setReservationId(UUID reservationId) {
		this.reservationId = reservationId;
	}

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public String getBuyerId() {
		return buyerId;
	}

	public void setBuyerId(String buyerId) {
		this.buyerId = buyerId;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public Instant getReservedAt() {
		return reservedAt;
	}

	public void setReservedAt(Instant reservedAt) {
		this.reservedAt = reservedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}
}
