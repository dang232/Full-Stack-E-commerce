package com.vnshop.orderservice.application.fraud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves a client IP address to a 2-letter ISO country code.
 *
 * <p>TODO: replace this stub with a real MaxMind GeoLite2 or ip-api.com integration.
 * The stub always returns "VN" so the geo-anomaly rule never triggers false positives
 * in production until a real lookup is wired in.
 */
@Service
public class GeoIpService {

    private static final Logger LOG = LoggerFactory.getLogger(GeoIpService.class);
    private static final String DEFAULT_COUNTRY = "VN";

    /**
     * Returns the ISO-3166-1 alpha-2 country code for the given IP address.
     * Strips any port suffix and handles X-Forwarded-For comma-separated chains
     * by taking the first (client) IP.
     *
     * @param rawIp raw value from X-Forwarded-For or REMOTE_ADDR
     * @return ISO country code, or {@code "VN"} when lookup is unavailable
     */
    public String countryFor(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return DEFAULT_COUNTRY;
        }
        // X-Forwarded-For may contain a comma-separated list; first entry is the originating client.
        String clientIp = rawIp.split(",")[0].trim();
        LOG.debug("geo-ip lookup for {} (stub — returning {})", clientIp, DEFAULT_COUNTRY);
        // Stub: always VN until real geo-IP integration is implemented.
        return DEFAULT_COUNTRY;
    }
}
