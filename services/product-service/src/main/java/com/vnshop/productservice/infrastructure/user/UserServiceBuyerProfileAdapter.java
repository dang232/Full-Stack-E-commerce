package com.vnshop.productservice.infrastructure.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.productservice.domain.review.port.out.BuyerProfileLookupPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP-backed adapter that batches buyer keycloakIds against
 * user-service's {@code GET /users/public-profiles?ids=...} endpoint
 * and returns a map keyed by userId. Used by the Reviews tab so each
 * row can render the buyer's display name instead of a UUID.
 *
 * <p>Failure semantics: any transport error, non-2xx response, or parse
 * failure resolves to an empty map. Callers (currently
 * {@code GetProductReviewsUseCase}) treat missing entries as anonymous
 * — the FE renders the {@code product.reviews.anonGuest} fallback for
 * the affected rows. This matches the {@code CouponServiceAdapter}
 * pattern: degrade silently, never throw across the service boundary.</p>
 */
public class UserServiceBuyerProfileAdapter implements BuyerProfileLookupPort {
    private static final Logger log = LoggerFactory.getLogger(UserServiceBuyerProfileAdapter.class);

    private final UserServiceHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public UserServiceBuyerProfileAdapter(UserServiceHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, BuyerPublicProfile> lookup(List<String> keycloakIds) {
        if (keycloakIds == null || keycloakIds.isEmpty()) {
            return Map.of();
        }
        try {
            String body = httpClient.list(keycloakIds);
            if (body == null || body.isBlank()) return Map.of();
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (!data.isArray()) return Map.of();
            Map<String, BuyerPublicProfile> out = new HashMap<>();
            for (JsonNode node : data) {
                String userId = node.path("userId").asText(null);
                if (userId == null || userId.isBlank()) continue;
                String displayName = node.path("displayName").isNull() ? null : node.path("displayName").asText(null);
                String avatarUrl = node.path("avatarUrl").isNull() ? null : node.path("avatarUrl").asText(null);
                out.put(userId, new BuyerPublicProfile(userId, displayName, avatarUrl));
            }
            return out;
        } catch (Exception ex) {
            // user-service down, parse failure, etc. The Reviews tab
            // degrades to anonymous labels rather than UUIDs; never
            // throw across the service boundary.
            log.warn("user-service public-profiles lookup failed (idsCount={}): {}", keycloakIds.size(), ex.getMessage());
            return Map.of();
        }
    }
}
