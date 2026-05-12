package com.vnshop.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.domain.finance.port.out.PayoutRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerTransactionRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerWalletRepositoryPort;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.infrastructure.persistence.OrderJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
})
class OrderServiceApplicationTests {

	@MockitoBean
	private OrderJpaRepository orderJpaRepository;

	@MockitoBean
	private OrderEventPublisherPort orderEventPublisherPort;

	@MockitoBean
	private SellerWalletRepositoryPort sellerWalletRepositoryPort;

	@MockitoBean
	private SellerTransactionRepositoryPort sellerTransactionRepositoryPort;

	@MockitoBean
	private PayoutRepositoryPort payoutRepositoryPort;

	@MockitoBean
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

}
