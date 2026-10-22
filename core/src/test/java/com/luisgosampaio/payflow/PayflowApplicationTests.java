package com.luisgosampaio.payflow;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PayflowApplicationTests {

    @ServiceConnection
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
        assertThat(postgres.isRunning()).isTrue();
	}

    // only enter in 'marco 1' with the first migration in resources/db/migrations
    @Test
    @Disabled
    void flywayAppliedMigrations() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class
        );

        assertThat(applied).isPositive();
    }

}
