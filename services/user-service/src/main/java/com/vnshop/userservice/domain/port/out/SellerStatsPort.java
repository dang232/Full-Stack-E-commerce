package com.vnshop.userservice.domain.port.out;

import java.util.Map;
import java.util.Set;

public interface SellerStatsPort {

    record SellerStats(Double ratingAvg, long ratingCount) {
        public static SellerStats empty() {
            return new SellerStats(null, 0L);
        }
    }

    SellerStats sellerStats(String sellerId);

    long productCount(String sellerId);

    /**
     * Batch lookup. Returned map MUST contain an entry for every requested
     * sellerId; missing data is filled with {@link SellerStats#empty()} so the
     * caller can iterate without null checks.
     */
    Map<String, SellerStats> sellerStatsBatch(Set<String> sellerIds);

    /**
     * Batch lookup. Returned map MUST contain an entry for every requested
     * sellerId; missing data is filled with 0L.
     */
    Map<String, Long> productCountBatch(Set<String> sellerIds);
}
