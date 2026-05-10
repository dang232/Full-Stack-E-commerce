package com.vnshop.paymentservice.infrastructure.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class VnpaySigner {
    private static final String HMAC_SHA512 = "HmacSHA512";

    private final String hashSecret;

    public VnpaySigner(String hashSecret) {
        if (hashSecret == null || hashSecret.isBlank()) {
            throw new IllegalArgumentException("VNPay hash secret is required");
        }
        this.hashSecret = hashSecret;
    }

    public String sign(Map<String, String> parameters) {
        return hmacSha512(canonicalQuery(parameters, true));
    }

    public boolean verify(Map<String, String> parameters, String receivedHash) {
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        return constantTimeEquals(sign(parameters), receivedHash);
    }

    public String canonicalQuery(Map<String, String> parameters, boolean excludeHashFields) {
        Objects.requireNonNull(parameters, "parameters is required");
        return new TreeMap<>(parameters).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> !excludeHashFields || !isHashField(entry.getKey()))
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static boolean isHashField(String key) {
        return "vnp_SecureHash".equalsIgnoreCase(key) || "vnp_SecureHashType".equalsIgnoreCase(key);
    }

    private String hmacSha512(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA512);
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign VNPay payload", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.toLowerCase().getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }
        int diff = 0;
        for (int index = 0; index < expectedBytes.length; index++) {
            diff |= expectedBytes[index] ^ actualBytes[index];
        }
        return diff == 0;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
