package com.vnshop.productservice.domain.review.port.out;

import java.util.List;
import java.util.Map;

/**
 * Resolves buyer keycloakIds to public display info (name + avatar) so
 * downstream callers can render a real name next to a UUID — primarily
 * the Reviews tab on the product detail page, where every review row
 * carries a buyerId from the BE that the FE was previously rendering as
 * a raw UUID.
 *
 * <p>The lookup is HTTP-backed against user-service. Failure semantics
 * (network, 4xx, 5xx, partial response) MUST resolve to an empty map
 * for the missing ids; callers degrade by showing an anonymous label
 * rather than the UUID. Never throw on a degraded user-service.</p>
 */
public interface BuyerProfileLookupPort {
    /**
     * Resolve a batch of buyer keycloakIds to display info. Missing or
     * unresolved ids are silently dropped from the result map. Order
     * is not significant; callers index by id.
     */
    Map<String, BuyerPublicProfile> lookup(List<String> keycloakIds);

    record BuyerPublicProfile(String userId, String displayName, String avatarUrl) {
    }
}
