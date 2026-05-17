package com.vnshop.inventoryservice.application;

import com.vnshop.inventoryservice.domain.StockReservation;
import com.vnshop.inventoryservice.domain.port.out.StockReservationPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases all stock previously reserved for an order. Idempotent: repeated
 * Release calls for the same order_id are no-ops once the reservations have
 * been moved to RELEASED.
 */
public class ReleaseStockUseCase {
    private static final Logger log = LoggerFactory.getLogger(ReleaseStockUseCase.class);

    private final StockReservationPort port;
    private final Clock clock;

    public ReleaseStockUseCase(StockReservationPort port) {
        this(port, Clock.systemUTC());
    }

    ReleaseStockUseCase(StockReservationPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    @Transactional
    public boolean release(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }

        List<StockReservation> active = port.findActiveReservationsByOrderId(orderId);
        if (active.isEmpty()) {
            log.info("Release no-op: no active reservations for orderId={}", orderId);
            return true;
        }

        Instant now = clock.instant();
        for (StockReservation reservation : active) {
            port.increment(reservation.productId(), reservation.quantity());
            port.markReleased(reservation.released(now));
        }
        log.info("Released {} reservations for orderId={}", active.size(), orderId);
        return true;
    }
}
