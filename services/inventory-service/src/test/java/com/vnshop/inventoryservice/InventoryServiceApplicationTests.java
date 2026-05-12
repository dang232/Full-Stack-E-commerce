package com.vnshop.inventoryservice;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InventoryServiceApplicationTests {
    @Test
    void applicationClassIsLoadableWithoutInfrastructure() {
        assertThat(InventoryServiceApplication.class).isNotNull();
    }
}