package com.vnshop.inventoryservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLevelJpaSpringDataRepository
        extends JpaRepository<StockLevelJpaEntity, String> {

    /**
     * Atomically decrement the projected stock for a product, but only if the
     * row exists AND has at least the requested quantity. Returns the number
     * of rows affected (0 means caller must distinguish "no row" from
     * "insufficient stock" by re-reading).
     */
    @Modifying
    @Query(value = "UPDATE inventory_svc.stock_levels SET available_quantity = available_quantity - :qty, updated_at = NOW() "
            + "WHERE product_id = :productId AND available_quantity >= :qty", nativeQuery = true)
    int conditionallyDecrement(@Param("productId") String productId, @Param("qty") int quantity);

    /**
     * Idempotent increment used by Release. Inserts a row at zero stock if
     * none exists, then adds back. Postgres-specific upsert.
     */
    @Modifying
    @Query(value = "INSERT INTO inventory_svc.stock_levels (product_id, available_quantity, updated_at) "
            + "VALUES (:productId, :qty, NOW()) "
            + "ON CONFLICT (product_id) DO UPDATE SET "
            + "available_quantity = inventory_svc.stock_levels.available_quantity + EXCLUDED.available_quantity, "
            + "updated_at = NOW()", nativeQuery = true)
    int upsertIncrement(@Param("productId") String productId, @Param("qty") int quantity);
}
