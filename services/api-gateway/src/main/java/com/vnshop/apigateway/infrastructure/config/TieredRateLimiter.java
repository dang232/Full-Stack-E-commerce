package com.vnshop.apigateway.infrastructure.config;

import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.validation.Validator;
import reactor.core.publisher.Mono;

/**
 * A {@link org.springframework.cloud.gateway.filter.ratelimit.RateLimiter} that
 * delegates to one of two underlying {@link RedisRateLimiter} instances based on
 * the prefix of the resolved key.
 *
 * <p>Keys produced by {@link TieredKeyResolver} are prefixed with either
 * {@value TieredKeyResolver#USER_PREFIX} for authenticated users or
 * {@value TieredKeyResolver#ANON_PREFIX} for anonymous requests.  This wrapper
 * routes each call to the appropriate limiter so authenticated users receive
 * a higher allowance while anonymous users are throttled more aggressively.
 *
 * <p>The key is forwarded as-is to the delegate; Redis namespaces the bucket
 * under {@code request_rate_limiter.{routeId}.{key}.*}, so the prefix remains
 * part of the bucket name and keeps the two tiers separated in Redis.
 */
public class TieredRateLimiter extends AbstractRateLimiter<RedisRateLimiter.Config> {

    private static final String PROPERTY_NAME = "tiered-rate-limiter";

    private final RedisRateLimiter anonLimiter;
    private final RedisRateLimiter authLimiter;

    public TieredRateLimiter(RedisRateLimiter anonLimiter, RedisRateLimiter authLimiter) {
        // Validator is injected by AbstractRateLimiter.setApplicationContext at context refresh;
        // passing null here is safe — it is declared @Nullable in the parent constructor.
        super(RedisRateLimiter.Config.class, PROPERTY_NAME, null);
        this.anonLimiter = anonLimiter;
        this.authLimiter = authLimiter;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        if (id.startsWith(TieredKeyResolver.USER_PREFIX)) {
            return authLimiter.isAllowed(routeId, id);
        }
        return anonLimiter.isAllowed(routeId, id);
    }
}
