package com.vnshop.inventoryservice.infrastructure.persistence;

import com.vnshop.inventoryservice.domain.StockReservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "inventory_svc", name = "stock_reservations")
public class StockReservationJpaEntity {

    @Id
    @Column(name = "reservation_id", columnDefinition = "uuid")
    private UUID reservationId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "variant")
    private String variant;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StockReservation.Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected StockReservationJpaEntity() {
    }

    public static StockReservationJpaEntity fromDomain(StockReservation reservation) {
        StockReservationJpaEntity entity = new StockReservationJpaEntity();
        entity.reservationId = reservation.reservationId();
        entity.orderId = reservation.orderId();
        entity.productId = reservation.productId();
        entity.variant = reservation.variant();
        entity.quantity = reservation.quantity();
        entity.status = reservation.status();
        entity.createdAt = reservation.createdAt();
        entity.releasedAt = reservation.releasedAt();
        return entity;
    }

    public StockReservation toDomain() {
        return new StockReservation(reservationId, orderId, productId, variant,
                quantity, status, createdAt, releasedAt);
    }

    public UUID getReservationId() { return reservationId; }
    public String getOrderId() { return orderId; }
    public StockReservation.Status getStatus() { return status; }

    public void setStatus(StockReservation.Status status) { this.status = status; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
}
