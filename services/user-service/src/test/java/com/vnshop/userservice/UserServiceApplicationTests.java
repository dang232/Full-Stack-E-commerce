package com.vnshop.userservice;

import com.vnshop.userservice.domain.port.out.GdprExportRepositoryPort;
import com.vnshop.userservice.domain.port.out.SellerStatsPort;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import com.vnshop.userservice.domain.port.out.WishlistRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
class UserServiceApplicationTests {

	@MockitoBean
	private UserRepositoryPort userRepositoryPort;

	@MockitoBean
	private WishlistRepositoryPort wishlistRepositoryPort;

	@MockitoBean
	private SellerStatsPort sellerStatsPort;

	@MockitoBean
	private GdprExportRepositoryPort gdprExportRepositoryPort;

	@MockitoBean
	@SuppressWarnings("rawtypes")
	private KafkaTemplate kafkaTemplate;

	@Test
	void contextLoads() {
	}

}
