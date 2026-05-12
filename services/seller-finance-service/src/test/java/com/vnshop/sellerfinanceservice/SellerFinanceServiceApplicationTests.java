package com.vnshop.sellerfinanceservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.sellerfinanceservice.domain.port.out.PayoutRepositoryPort;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
})
class SellerFinanceServiceApplicationTests {

	@MockitoBean
	private SellerWalletRepositoryPort sellerWalletRepositoryPort;

	@MockitoBean
	private PayoutRepositoryPort payoutRepositoryPort;

	@MockitoBean
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

}
