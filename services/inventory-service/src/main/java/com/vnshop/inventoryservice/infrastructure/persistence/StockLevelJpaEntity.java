package com.vnshop.inventoryservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "inventory_svc", name = "stock_levels")
public class StockLevelJpaEntity {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockLevelJpaEntity() {
    }

    public StockLevelJpaEntity(String productId, int availableQuantity, Instant updatedAt) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.updatedAt = updatedAt;
    }

    public String getProductId() { return productId; }
    public int getAvailableQuantity() { return availableQuantity; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
