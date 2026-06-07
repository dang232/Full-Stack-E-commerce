package com.vnshop.orderservice.domain.port.out;

import java.time.Instant;

/**
 * Port for querying recent order counts — used by the fraud velocity rule.
 */
public interface FraudOrderCountPort {
    /**
     * Counts non-cancelled, non-flagged orders placed by {@code buyerId}
     * whose creation timestamp is on or after {@code since}.
     *
     * @param buyerId the buyer's identifier
     * @param since   lower bound (inclusive) of the time window
     * @return number of qualifying orders
     */
    long countRecentOrders(String buyerId, Instant since);
}
