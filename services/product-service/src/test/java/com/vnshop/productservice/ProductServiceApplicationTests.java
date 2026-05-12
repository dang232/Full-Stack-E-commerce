package com.vnshop.productservice;

import com.vnshop.productservice.domain.ProductEvent;
import com.vnshop.productservice.domain.port.out.ObjectMetadataRepositoryPort;
import com.vnshop.productservice.domain.port.out.ObjectStoragePort;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import com.vnshop.productservice.domain.review.port.out.ReviewRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
})
class ProductServiceApplicationTests {

	@MockitoBean
	private KafkaTemplate<String, ProductEvent> kafkaTemplate;

	@MockitoBean
	private ObjectMetadataRepositoryPort objectMetadataRepositoryPort;

	@MockitoBean
	private ObjectStoragePort objectStoragePort;

	@MockitoBean
	private ProductRepositoryPort productRepositoryPort;

	@MockitoBean
	private ReviewRepositoryPort reviewRepositoryPort;

	@Test
	void contextLoads() {
	}

}
