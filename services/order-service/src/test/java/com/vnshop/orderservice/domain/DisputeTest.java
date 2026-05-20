package com.vnshop.orderservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisputeTest {
    private static final UUID DISPUTE_ID = UUID.randomUUID();
    private static final String RETURN_ID = UUID.randomUUID().toString();

    @Test
    void newDisputeStartsOpenWithNoAdminFields() {
        Dispute dispute = new Dispute(DISPUTE_ID, RETURN_ID, "wrong size", null);

        assertThat(dispute.disputeId()).isEqualTo(DISPUTE_ID);
        assertThat(dispute.returnId()).isEqualTo(RETURN_ID);
        assertThat(dispute.buyerReason()).isEqualTo("wrong size");
        assertThat(dispute.sellerResponse()).isNull();
        assertThat(dispute.adminResolution()).isNull();
        assertThat(dispute.resolvedBy()).isNull();
        assertThat(dispute.status()).isEqualTo(DisputeStatus.OPEN);
    }

    @Test
    void resolveRecordsBothResolutionAndAdminId() {
        Dispute dispute = new Dispute(DISPUTE_ID, RETURN_ID, "wrong size", null);

        dispute.resolve("partial refund issued", "admin-42");

        assertThat(dispute.adminResolution()).isEqualTo("partial refund issued");
        assertThat(dispute.resolvedBy()).isEqualTo("admin-42");
        assertThat(dispute.status()).isEqualTo(DisputeStatus.RESOLVED);
    }

    @Test
    void resolveRejectsBlankAdminResolution() {
        Dispute dispute = new Dispute(DISPUTE_ID, RETURN_ID, "wrong size", null);

        assertThatThrownBy(() -> dispute.resolve("  ", "admin-42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adminResolution");
        assertThat(dispute.status()).isEqualTo(DisputeStatus.OPEN);
        assertThat(dispute.resolvedBy()).isNull();
    }

    @Test
    void resolveRejectsBlankResolvedBy() {
        // Pt19 audit: resolvedBy is required so the audit log can answer
        // "who closed this dispute". A blank value defeats the point.
        Dispute dispute = new Dispute(DISPUTE_ID, RETURN_ID, "wrong size", null);

        assertThatThrownBy(() -> dispute.resolve("partial refund issued", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolvedBy");
        assertThat(dispute.status()).isEqualTo(DisputeStatus.OPEN);
        assertThat(dispute.adminResolution()).isNull();
    }

    @Test
    void resolveCannotRunTwice() {
        Dispute dispute = new Dispute(DISPUTE_ID, RETURN_ID, "wrong size", null);
        dispute.resolve("partial refund issued", "admin-42");

        assertThatThrownBy(() -> dispute.resolve("retroactive change", "admin-43"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESOLVED");
        // Original resolution persists — the second call neither overwrites
        // the resolution text nor the resolvedBy field.
        assertThat(dispute.adminResolution()).isEqualTo("partial refund issued");
        assertThat(dispute.resolvedBy()).isEqualTo("admin-42");
    }

    @Test
    void constructorRejectsNullDisputeId() {
        assertThatThrownBy(() -> new Dispute(null, RETURN_ID, "wrong size", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("disputeId");
    }

    @Test
    void constructorRejectsBlankReturnId() {
        assertThatThrownBy(() -> new Dispute(DISPUTE_ID, "", "wrong size", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("returnId");
    }

    @Test
    void constructorRejectsBlankBuyerReason() {
        assertThatThrownBy(() -> new Dispute(DISPUTE_ID, RETURN_ID, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("buyerReason");
    }

    @Test
    void rehydrationConstructorPreservesResolvedFields() {
        // Persistence path: DisputeJpaEntity.toDomain calls the 7-arg constructor
        // to rehydrate. Verifies resolvedBy survives the round-trip.
        Dispute rehydrated = new Dispute(DISPUTE_ID, RETURN_ID, "wrong size", "ship was correct",
                "kept the order", "admin-42", DisputeStatus.RESOLVED);

        assertThat(rehydrated.adminResolution()).isEqualTo("kept the order");
        assertThat(rehydrated.resolvedBy()).isEqualTo("admin-42");
        assertThat(rehydrated.status()).isEqualTo(DisputeStatus.RESOLVED);
    }
}
