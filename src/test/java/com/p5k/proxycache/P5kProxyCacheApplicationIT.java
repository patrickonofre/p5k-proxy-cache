package com.p5k.proxycache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application context against a real PostgreSQL provided by
 * Testcontainers. Requires a running Docker daemon (gate: full / {@code mvn verify}).
 */
@Testcontainers
@SpringBootTest
class P5kProxyCacheApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void contextLoads() {
        // Context boots = scaffold (Boot 4.1 + Gateway WebMVC + JPA + Flyway) wires correctly.
    }
}
