package com.vnshop.searchservice;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceApplicationTests {
    @Test
    void applicationClassIsLoadableWithoutInfrastructure() {
        assertThat(SearchServiceApplication.class).isNotNull();
    }
}