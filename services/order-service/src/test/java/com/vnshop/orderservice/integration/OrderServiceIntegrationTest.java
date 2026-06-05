package com.vnshop.orderservice.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class OrderServiceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        // Verifies: Spring context boots, Flyway migrations run, Kafka connects
        assertThat(dataSource).isNotNull();
    }

    @Test
    void flywayMigrationsApplied() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            var meta = conn.getMetaData();
            var rs = meta.getTables(null, null, "orders", null);
            assertThat(rs.next()).isTrue();
        }
    }
}
