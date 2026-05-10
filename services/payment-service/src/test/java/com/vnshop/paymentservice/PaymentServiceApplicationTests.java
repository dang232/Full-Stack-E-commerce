package com.vnshop.paymentservice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceApplicationTests {

    @Test
    void applicationClassIsLoadableWithoutInfrastructure() {
        assertThat(PaymentServiceApplication.class).isNotNull();
    }
}
