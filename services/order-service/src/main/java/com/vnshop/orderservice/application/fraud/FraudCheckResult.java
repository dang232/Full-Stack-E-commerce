package com.vnshop.orderservice.application.fraud;

import java.util.List;

/**
 * Immutable result of a fraud evaluation.
 * A {@code flagged} result means the order must be held for admin review;
 * payment processing will not proceed.
 */
public record FraudCheckResult(boolean flagged, List<String> reasons) {

    public static FraudCheckResult clean() {
        return new FraudCheckResult(false, List.of());
    }

    public static FraudCheckResult flagged(List<String> reasons) {
        return new FraudCheckResult(true, List.copyOf(reasons));
    }
}
