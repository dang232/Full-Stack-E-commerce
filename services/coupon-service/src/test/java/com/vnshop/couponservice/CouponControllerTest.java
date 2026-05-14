package com.vnshop.couponservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.vnshop.couponservice.infrastructure.CouponRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:coupon_service;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS coupon_svc",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class CouponControllerTest {
    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Autowired
    private CouponRepository couponRepository;

    @BeforeEach
    void deleteCoupons() {
        couponRepository.deleteAll();
    }

    @Test
    void createCoupon() {
        Map<String, Object> response = restTemplate.postForObject(couponsUrl(), validCouponRequest("SAVE10"), Map.class);

        assertThat(response).containsEntry("code", "SAVE10");
        assertThat(response).containsEntry("discountType", "PERCENTAGE");
        assertThat(couponRepository.findByCode("SAVE10")).isPresent();
    }

    @Test
    void listActiveCoupons() {
        restTemplate.postForObject(couponsUrl(), validCouponRequest("LIST10"), Map.class);

        Map[] response = restTemplate.getForObject(couponsUrl(), Map[].class);

        assertThat(response).extracting(coupon -> coupon.get("code")).contains("LIST10");
    }

    @Test
    void validateCouponReturnsDiscountForValidCoupon() {
        restTemplate.postForObject(couponsUrl(), validCouponRequest("VALID10"), Map.class);

        Map<String, Object> response = restTemplate.postForObject(
                couponsUrl() + "/validate",
                Map.of("code", "VALID10", "orderAmount", BigDecimal.valueOf(200)),
                Map.class);

        assertThat(response).containsEntry("valid", true);
        assertThat(new BigDecimal(response.get("discount").toString())).isEqualByComparingTo("20.00");
        assertThat(response).containsEntry("message", "Coupon is valid");
    }

    @Test
    void validateCouponRejectsExpiredCoupon() {
        restTemplate.postForObject(couponsUrl(), couponRequest("EXPIRED10", Instant.now().minusSeconds(60), BigDecimal.valueOf(100)), Map.class);

        Map<String, Object> response = restTemplate.postForObject(
                couponsUrl() + "/validate",
                Map.of("code", "EXPIRED10", "orderAmount", BigDecimal.valueOf(200)),
                Map.class);

        assertThat(response).containsEntry("valid", false);
        assertThat(new BigDecimal(response.get("discount").toString())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response).containsEntry("message", "Coupon is expired");
    }

    @Test
    void validateCouponRejectsInsufficientOrderAmount() {
        restTemplate.postForObject(couponsUrl(), validCouponRequest("MIN10"), Map.class);

        Map<String, Object> response = restTemplate.postForObject(
                couponsUrl() + "/validate",
                Map.of("code", "MIN10", "orderAmount", BigDecimal.valueOf(50)),
                Map.class);

        assertThat(response).containsEntry("valid", false);
        assertThat(new BigDecimal(response.get("discount").toString())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response).containsEntry("message", "Order amount is below minimum");
    }

    private String couponsUrl() {
        return "http://localhost:" + port + "/coupons";
    }

    private Map<String, Object> validCouponRequest(String code) {
        return couponRequest(code, Instant.now().plusSeconds(3600), BigDecimal.valueOf(100));
    }

    private Map<String, Object> couponRequest(String code, Instant validUntil, BigDecimal minOrderValue) {
        return Map.of(
                "code", code,
                "discountType", "PERCENTAGE",
                "discountValue", BigDecimal.valueOf(10),
                "minOrderValue", minOrderValue,
                "maxDiscount", BigDecimal.valueOf(50),
                "maxUses", 100,
                "validUntil", validUntil.toString());
    }
}
