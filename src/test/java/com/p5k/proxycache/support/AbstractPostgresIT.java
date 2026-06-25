package com.p5k.proxycache.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests needing a real PostgreSQL and a running web server.
 *
 * <p>Uses the Testcontainers <em>singleton container</em> pattern: the container is
 * started once in a static initializer and never stopped by JUnit, so it stays on a
 * stable port and is safely shared across every IT class for the whole test run.
 * (A {@code @Container}-managed static container is stopped after the first class,
 * which breaks cached Spring contexts in later classes.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractPostgresIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
