package com.vnshop.paymentservice.infrastructure.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MomoSigner {
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String secretKey;

    public MomoSigner(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("MoMo secret key is required");
        }
        this.secretKey = secretKey;
    }

    public String sign(Map<String, String> orderedParameters) {
        Objects.requireNonNull(orderedParameters, "orderedParameters is required");
        return hmacSha256(rawSignature(orderedParameters));
    }

    public boolean verify(Map<String, String> orderedParameters, String receivedSignature) {
        if (receivedSignature == null || receivedSignature.isBlank()) {
            return false;
        }
        return constantTimeEquals(sign(orderedParameters), receivedSignature);
    }

    public String rawSignature(Map<String, String> orderedParameters) {
        return orderedParameters.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public static Map<String, String> orderedCreateParameters(MomoCreateRequest request) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("accessKey", request.accessKey());
        parameters.put("amount", String.valueOf(request.amount()));
        parameters.put("extraData", request.extraData());
        parameters.put("ipnUrl", request.ipnUrl());
        parameters.put("orderId", request.orderId());
        parameters.put("orderInfo", request.orderInfo());
        parameters.put("partnerCode", request.partnerCode());
        parameters.put("redirectUrl", request.redirectUrl());
        parameters.put("requestId", request.requestId());
        parameters.put("requestType", request.requestType());
        return parameters;
    }

    public static Map<String, String> orderedIpnParameters(MomoIpnRequest request) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("accessKey", request.accessKey());
        parameters.put("amount", String.valueOf(request.amount()));
        parameters.put("extraData", request.extraData());
        parameters.put("message", request.message());
        parameters.put("orderId", request.orderId());
        parameters.put("orderInfo", request.orderInfo());
        parameters.put("orderType", request.orderType());
        parameters.put("partnerCode", request.partnerCode());
        parameters.put("payType", request.payType());
        parameters.put("requestId", request.requestId());
        parameters.put("responseTime", String.valueOf(request.responseTime()));
        parameters.put("resultCode", String.valueOf(request.resultCode()));
        parameters.put("transId", String.valueOf(request.transId()));
        return parameters;
    }

    public static Map<String, String> orderedQueryDrParameters(MomoQueryDrRequest request) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("accessKey", request.accessKey());
        parameters.put("orderId", request.orderId());
        parameters.put("partnerCode", request.partnerCode());
        parameters.put("requestId", request.requestId());
        return parameters;
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign MoMo payload", exception);
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
}
