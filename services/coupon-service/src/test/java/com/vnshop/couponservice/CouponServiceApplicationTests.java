package com.vnshop.couponservice;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CouponServiceApplicationTests {
    @Test
    void applicationClassIsLoadableWithoutInfrastructure() {
        assertThat(CouponServiceApplication.class).isNotNull();
    }
}