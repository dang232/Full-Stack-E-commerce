package com.vnshop.paymentservice.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.paymentservice.infrastructure.gateway.MomoSigner;
import com.vnshop.paymentservice.infrastructure.gateway.VnpaySigner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WebhookSignatureFilter}.
 *
 * <p>Covers VNPay and MoMo paths:
 * <ul>
 *   <li>Valid signature → filter passes (chain proceeds, 200 from stub chain)</li>
 *   <li>Tampered payload / wrong signature → 401</li>
 *   <li>Missing signature → 401</li>
 * </ul>
 */
class WebhookSignatureFilterTest {

    private static final String VNPAY_SECRET = "test-vnpay-secret";
    private static final String MOMO_SECRET  = "test-momo-secret";

    private VnpaySigner vnpaySigner;
    private MomoSigner  momoSigner;
    private ObjectMapper objectMapper;
    private WebhookSignatureFilter filter;

    @BeforeEach
    void setUp() {
        vnpaySigner  = new VnpaySigner(VNPAY_SECRET);
        momoSigner   = new MomoSigner(MOMO_SECRET);
        objectMapper = new ObjectMapper();
        filter = new WebhookSignatureFilter(
                Optional.of(vnpaySigner),
                Optional.of(momoSigner),
                objectMapper);
    }

    // =========================================================================
    // VNPay tests
    // =========================================================================

    @Test
    void vnpay_validSignature_passesThrough() throws Exception {
        Map<String, String> params = vnpayParams();
        params.put("vnp_SecureHash", vnpaySigner.sign(params));

        MockHttpServletRequest request = vnpayRequest(params);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();          // chain was invoked
        assertThat(response.getStatus()).isEqualTo(200);    // filter did not short-circuit
    }

    @Test
    void vnpay_tamperedPayload_returns401() throws Exception {
        Map<String, String> params = vnpayParams();
        params.put("vnp_SecureHash", vnpaySigner.sign(params));
        // Tamper: change amount after signing
        params.put("vnp_Amount", "9999999");

        MockHttpServletRequest request = vnpayRequest(params);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();             // chain was NOT invoked
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("INVALID_SIGNATURE");
    }

    @Test
    void vnpay_missingSignatureHeader_returns401() throws Exception {
        Map<String, String> params = vnpayParams();
        // No vnp_SecureHash added

        MockHttpServletRequest request = vnpayRequest(params);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    // =========================================================================
    // MoMo tests
    // =========================================================================

    @Test
    void momo_validSignature_passesThrough() throws Exception {
        Map<String, String> orderedParams = momoOrderedParams();
        String signature = momoSigner.sign(orderedParams);
        String body = momoBody(orderedParams, signature);

        MockHttpServletRequest request = momoRequest(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void momo_tamperedPayload_returns401() throws Exception {
        Map<String, String> orderedParams = momoOrderedParams();
        String signature = momoSigner.sign(orderedParams);
        // Tamper: change amount in the body but keep the original signature
        orderedParams.put("amount", "9999999");
        String body = momoBody(orderedParams, signature);

        MockHttpServletRequest request = momoRequest(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("INVALID_SIGNATURE");
    }

    @Test
    void momo_missingSignatureField_returns401() throws Exception {
        Map<String, String> orderedParams = momoOrderedParams();
        // Build body without a signature field
        String body = objectMapper.writeValueAsString(orderedParams);

        MockHttpServletRequest request = momoRequest(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void nonWebhookPath_isPassedThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/payment/status/ORDER-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void whenVnpayDisabled_ipnPassesThrough() throws Exception {
        WebhookSignatureFilter disabledFilter = new WebhookSignatureFilter(
                Optional.empty(), Optional.of(momoSigner), objectMapper);

        // No signature at all — should still pass because vnpay is disabled
        MockHttpServletRequest request = vnpayRequest(vnpayParams());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        disabledFilter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void whenMomoDisabled_ipnPassesThrough() throws Exception {
        WebhookSignatureFilter disabledFilter = new WebhookSignatureFilter(
                Optional.of(vnpaySigner), Optional.empty(), objectMapper);

        Map<String, String> orderedParams = momoOrderedParams();
        // No signature in body — should still pass because momo is disabled
        String body = objectMapper.writeValueAsString(orderedParams);
        MockHttpServletRequest request = momoRequest(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        disabledFilter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Map<String, String> vnpayParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_OrderInfo", "Thanh+toan+don+hang+12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", "DEMO");
        params.put("vnp_TransactionNo", "TXN-001");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "PAY-001");
        return params;
    }

    private static MockHttpServletRequest vnpayRequest(Map<String, String> params) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/payment/vnpay/ipn");
        params.forEach(request::addParameter);
        return request;
    }

    private static Map<String, String> momoOrderedParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("accessKey", "test-access-key");
        params.put("amount", "100000");
        params.put("extraData", "");
        params.put("message", "Successful");
        params.put("orderId", "PAY-001");
        params.put("orderInfo", "Payment for order ORDER-001");
        params.put("orderType", "momo_wallet");
        params.put("partnerCode", "MOMOTEST");
        params.put("payType", "qr");
        params.put("requestId", "REQ-001");
        params.put("responseTime", "1700000000000");
        params.put("resultCode", "0");
        params.put("transId", "TXN-MOMO-001");
        return params;
    }

    private String momoBody(Map<String, String> orderedParams, String signature) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(orderedParams);
        body.put("signature", signature);
        return objectMapper.writeValueAsString(body);
    }

    private static MockHttpServletRequest momoRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payment/momo/ipn");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
