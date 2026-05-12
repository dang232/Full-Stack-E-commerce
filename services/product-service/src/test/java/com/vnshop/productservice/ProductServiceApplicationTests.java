package com.vnshop.productservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductServiceApplicationTests {

	@Test
	void applicationClassIsLoadableWithoutInfrastructure() {
		assertThat(ProductServiceApplication.class).isNotNull();
	}

}
