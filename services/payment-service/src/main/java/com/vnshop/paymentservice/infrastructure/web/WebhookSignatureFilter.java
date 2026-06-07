package com.vnshop.paymentservice.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.paymentservice.infrastructure.gateway.MomoSigner;
import com.vnshop.paymentservice.infrastructure.gateway.VnpaySigner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP-layer webhook signature gate for VNPay and MoMo IPN endpoints.
 *
 * <p>Runs before the Spring Security filter chain on {@code /payment/vnpay/ipn}
 * and {@code /payment/momo/ipn}. Rejects requests with a missing or invalid
 * signature with HTTP 401 so the controller layer never sees unauthenticated
 * callbacks.
 *
 * <p>Stripe signature verification is handled inside
 * {@link StripeWebhookController} via the Stripe SDK's
 * {@code Webhook.constructEvent()} and is not duplicated here.
 *
 * <p>PayPal uses a buyer-initiated capture flow (JWT-authenticated), so no
 * inbound webhook signature gate is required.
 *
 * <p>SePay/VietQR arrive via a server-side poller or admin-confirm endpoint;
 * neither exposes a public IPN path that needs a signature gate here.
 */
public class WebhookSignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureFilter.class);

    private static final String VNPAY_IPN_PATH = "/payment/vnpay/ipn";
    private static final String MOMO_IPN_PATH  = "/payment/momo/ipn";

    private final Optional<VnpaySigner> vnpaySigner;
    private final Optional<MomoSigner>  momoSigner;
    private final ObjectMapper          objectMapper;

    public WebhookSignatureFilter(Optional<VnpaySigner> vnpaySigner,
                                   Optional<MomoSigner> momoSigner,
                                   ObjectMapper objectMapper) {
        this.vnpaySigner  = vnpaySigner;
        this.momoSigner   = momoSigner;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (VNPAY_IPN_PATH.equals(path)) {
            if (!verifyVnpay(request, response)) {
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        if (MOMO_IPN_PATH.equals(path)) {
            // Read body once, verify signature, then wrap so @RequestBody can re-read.
            byte[] bodyBytes = request.getInputStream().readAllBytes();
            if (!verifyMomo(bodyBytes, request.getRemoteAddr(), response)) {
                return;
            }
            chain.doFilter(new ReplayableRequestWrapper(request, bodyBytes), response);
            return;
        }

        chain.doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // VNPay — HMAC-SHA512 over sorted query parameters, field vnp_SecureHash
    // -------------------------------------------------------------------------

    private boolean verifyVnpay(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (vnpaySigner.isEmpty()) {
            // VNPay is disabled — pass through (controller will 404/503 anyway).
            return true;
        }
        Map<String, String> params = queryParams(request);
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            rejectUnauthorized(response, "VNPAY", request.getRemoteAddr(), "missing vnp_SecureHash");
            return false;
        }
        if (!vnpaySigner.get().verify(params, receivedHash)) {
            rejectUnauthorized(response, "VNPAY", request.getRemoteAddr(), "invalid vnp_SecureHash");
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // MoMo — HMAC-SHA256, signature field inside JSON body
    // -------------------------------------------------------------------------

    private boolean verifyMomo(byte[] bodyBytes, String remoteAddr, HttpServletResponse response)
            throws IOException {
        if (momoSigner.isEmpty()) {
            // MoMo is disabled — pass through.
            return true;
        }

        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, Object> json = objectMapper.readValue(body.isEmpty() ? "{}" : body, Map.class);

        String receivedSignature = json.get("signature") instanceof String s ? s : null;
        if (receivedSignature == null || receivedSignature.isBlank()) {
            rejectUnauthorized(response, "MOMO", remoteAddr, "missing signature field");
            return false;
        }

        Map<String, String> orderedParams = momoIpnParams(json);
        if (!momoSigner.get().verify(orderedParams, receivedSignature)) {
            rejectUnauthorized(response, "MOMO", remoteAddr, "invalid signature");
            return false;
        }
        return true;
    }

    /**
     * Builds the ordered IPN parameter map that MoMo signs, matching the order
     * defined in {@link MomoSigner#orderedIpnParameters}.
     */
    private static Map<String, String> momoIpnParams(Map<String, Object> json) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, json, "accessKey");
        putIfPresent(params, json, "amount");
        putIfPresent(params, json, "extraData");
        putIfPresent(params, json, "message");
        putIfPresent(params, json, "orderId");
        putIfPresent(params, json, "orderInfo");
        putIfPresent(params, json, "orderType");
        putIfPresent(params, json, "partnerCode");
        putIfPresent(params, json, "payType");
        putIfPresent(params, json, "requestId");
        putIfPresent(params, json, "responseTime");
        putIfPresent(params, json, "resultCode");
        putIfPresent(params, json, "transId");
        return params;
    }

    private static void putIfPresent(Map<String, String> target,
                                     Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, String.valueOf(value));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Map<String, String> queryParams(HttpServletRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            if (entry.getValue() != null && entry.getValue().length > 0) {
                map.put(entry.getKey(), entry.getValue()[0]);
            }
        }
        return map;
    }

    private void rejectUnauthorized(HttpServletResponse response,
                                    String provider, String remoteAddr,
                                    String reason) throws IOException {
        log.warn("webhook-signature-rejected provider={} ip={} reason={} timestamp={}",
                provider, remoteAddr, reason, Instant.now());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"unauthorized\",\"code\":\"INVALID_SIGNATURE\"}");
    }

    /**
     * Wraps a request and replays pre-read body bytes for downstream consumers
     * (e.g. Spring's {@code @RequestBody} deserializer).
     */
    private static final class ReplayableRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        ReplayableRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady()    { return true; }
                @Override public void setReadListener(ReadListener listener) { }
                @Override public int read() { return bais.read(); }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
