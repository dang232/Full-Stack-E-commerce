package com.vnshop.paymentservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

@MappedSuperclass
public abstract class BaseJpaEntity {
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Lets subclasses' fromDomain() seed createdAt from the domain object on
     * an UPDATE save. @PrePersist only fires on INSERT; on merge() of a
     * detached entity that fromDomain() built fresh, the returned managed
     * entity's createdAt reads null because nothing populated it. Without
     * this setter, toDomain() then NPEs on the next read. Setting it here
     * is safe because the column is updatable=false at the JDBC layer —
     * the DB row is never overwritten.
     */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
