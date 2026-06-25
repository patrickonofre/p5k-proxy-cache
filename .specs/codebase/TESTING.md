# Testing

**Stack:** JUnit 5 + Spring Boot Test (`@SpringBootTest` RANDOM_PORT, `TestRestTemplate`), Testcontainers (PostgreSQL), JDK `HttpServer` upstream stub (`support/StubUpstream`, zero-dep — replaces MockWebServer to avoid okhttp 5 version churn). Build: Maven.

## Gate Check Commands

| Gate | Command | Scope |
| --- | --- | --- |
| quick | `./mvnw -q test` | Surefire — unit tests (`*Test`) |
| full | `./mvnw -q verify` | Failsafe — integration (`*IT`), boots Testcontainers + MockWebServer |
| build | `./mvnw -q -DskipTests package` | Compile + package, no tests |

## Test Coverage Matrix

| Code Layer | Required Test | Notes |
| --- | --- | --- |
| Value/util (`CacheKeyFactory`, `CachedResponse`) | unit | Pure logic, no Spring context |
| Domain logic (`RuleRegistry`, `CaffeineCacheRegistry`) | unit | Matcher, TTL/eviction behavior |
| JPA entity + repo + Flyway (`CacheRule`, migration) | integration | Real Postgres via Testcontainers |
| Gateway route (`GatewayConfig`) | integration | Forward to MockWebServer upstream |
| Proxy filter (`CachingProxyFilter`) | integration | Full HIT/MISS/BYPASS flow, Testcontainers + MockWebServer |
| App bootstrap | integration | Context loads with Postgres up |

## Parallelism Assessment

| Test Type | Parallel-Safe | Reason |
| --- | --- | --- |
| unit | Yes | No shared state, no ports/containers |
| integration | **No** | Shared Testcontainers Postgres + MockWebServer ports; run sequentially |

## Conventions

- Unit tests → `*Test.java` (Surefire). Integration tests → `*IT.java` (Failsafe).
- One Testcontainers Postgres reused across IT via a shared base class (`@Testcontainers` + static container).
- Assert upstream call counts via `MockWebServer.getRequestCount()` to prove HIT made 0 calls.
