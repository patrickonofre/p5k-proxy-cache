# State

**Last Updated:** 2026-06-24
**Current Work:** ✅ Cache Proxy Core + Rule Store admin CRUD COMPLETE (31 tests). ✅ Dockerized (Dockerfile multi-stage + docker-compose: postgres + go-httpbin upstream + app) — verified end-to-end: `docker compose up`, seeded /uuid rule, GET twice = MISS→HIT same uuid. Next M1: Observability (Micrometer counters).
**Project renamed:** pk-open-cache → p5k-proxy-cache (artifact com.p5k:p5k-proxy-cache). Working dir folder still named pk-open-cache.

---

## Recent Decisions (Last 60 days)

### AD-001: Proxy core built on Spring Cloud Gateway (2026-06-24)

**Decision:** Use Spring Cloud Gateway (release train 2025.1.x / Oakwood) as the proxy foundation, not a custom WebFlux proxy or servlet MVC.
**Reason:** Purpose-built reverse proxy (route + filter model, reactive). Caching becomes a custom filter.
**Trade-off:** Couples to Spring Cloud release train cadence vs Boot.
**Impact:** Verified — Spring Cloud 2025.1.2 (Oakwood, 2026-06-11) introduces compatibility with Spring Boot 4.1.0. Caching logic lives in a Gateway filter.

### AD-002: Single upstream for v1 (2026-06-24)

**Decision:** Proxy fronts one upstream base URL in v1. Multi-route (per-rule upstream) deferred to M2.
**Reason:** Simplest MVP path.
**Trade-off:** Less generic initially.
**Impact:** One configured upstream target; rules only decide cache behavior, not destination.

### AD-003: Cache key = method + path + query (2026-06-24)

**Decision:** Cache key composed of method + path + query string only. No header/auth variation in v1.
**Reason:** Shared cache for public GET data; avoids key explosion and cross-user data leak.
**Trade-off:** Cannot cache per-user/per-header responses yet.
**Impact:** Default policy: cache only public GET.

### AD-004: Rules managed via admin REST CRUD (2026-06-24)

**Decision:** Cache rules stored in PostgreSQL, managed at runtime through an admin REST API.
**Reason:** Dynamic config without redeploy.
**Trade-off:** Needs admin endpoints + (later) auth.
**Impact:** Rule store = Postgres + in-memory cache; admin API mutates it.

### AD-005: Gateway flavor = Server WebMVC + virtual threads (2026-06-24, CONFIRMED)

**Decision:** Use `spring-cloud-starter-gateway-server-webmvc` with `spring.threads.virtual.enabled=true`, not the WebFlux variant.
**Reason:** Servlet model makes response-body capture/replay simple (`ContentCachingResponseWrapper`); Java 26 virtual threads remove blocking-thread cost.
**Trade-off:** Not reactive; per-request thread (cheap via Loom).
**Impact:** Caching = `OncePerRequestFilter` short-circuit. Supersedes "reactive" note in AD-001.
**Open validation:** servlet Filter ordering vs Gateway WebMVC RouterFunction — confirm on first run.

---

## Active Blockers

_None active._

### B-001 (RESOLVED 2026-06-24): Docker daemon was down — `full` gate blocked

Docker started; T1 context-load IT executed green (`mvn verify`, Tests run: 1). Integration gates now runnable.

---

## Lessons Learned

### L-001: Stack is bleeding-edge — verify versions before coding (2026-06-24)

**Context:** Boot 4.1.0 GA'd 2026-06-10; Java 26; Spring Cloud Oakwood compat landed 2026-06-11.
**Problem:** Docs/APIs may shift; fabricating versions cascades into broken design.
**Solution:** Confirmed via web search before writing docs.
**Prevents:** Pinning wrong/incompatible dependency versions in design/build.

### L-002: Boot 4.1 manages Testcontainers 2.0.5; TC 2.0 renamed module artifacts (2026-06-24)

**Context:** First pom used TC 1.x coords `org.testcontainers:postgresql` / `:junit-jupiter`.
**Problem:** Boot 4.1 imports Testcontainers BOM 2.0.5; TC 2.0 renamed modules → build failed "version missing".
**Solution:** Use `org.testcontainers:testcontainers-postgresql` / `testcontainers-junit-jupiter` (no explicit version — Boot-managed).
**Prevents:** Wrong TC coordinates in T2/T7/T8. Java imports (`org.testcontainers.containers.*`, `...junit.jupiter.*`, `@ServiceConnection`) still compile under TC 2.0.5.

### L-003: Boot 4 splits autoconfig into per-tech `spring-boot-<tech>` modules (2026-06-24)

**Context:** Flyway migration silently never ran; Hibernate `validate` failed "missing table cache_rule".
**Problem:** Boot 4 moved autoconfigurations out of `spring-boot-autoconfigure` into per-tech jars. `flyway-core` is only the library; Boot's `FlywayAutoConfiguration` lives in `org.springframework.boot:spring-boot-flyway`, which wasn't on the classpath → Flyway never triggered.
**Solution:** Add `org.springframework.boot:spring-boot-flyway` (Boot-managed version). JPA worked because `spring-boot-starter-data-jpa` already pulls `spring-boot-jpa`/`spring-boot-hibernate`.
**Prevents:** Same trap for other integrations — when an integration "does nothing", check its `spring-boot-<tech>` autoconfig module is present.

### L-005: HTTPS upstream works via `uri(https://...)` + `http()` handler (2026-06-24)

**Context:** Doubt whether the gateway `http()` handler does TLS for an `https://` upstream.
**Verified:** Live test through Docker against `https://api.github.com/users/octocat` — 200 over TLS, `X-Cache: MISS→HIT`, `x-ratelimit-remaining` frozen at 57 on HIT (no upstream call). `before(uri(...))` honors the URL scheme; no separate `https()` handler needed.
**Use:** `PROXY_UPSTREAM_BASE_URL=https://api.github.com docker compose up -d` (compose env now overridable).

### L-004: Testcontainers — use singleton container across multiple IT classes (2026-06-24)

**Context:** Adding a 5th IT class made an earlier-passing repo IT hang 30s ("HikariPool ... connection has been closed").
**Problem:** A `static` container managed by `@Testcontainers`/`@Container` is stopped after the first test class; cached Spring contexts in later classes keep stale connections to the dead container → Hikari 30s connection-timeout.
**Solution:** Singleton pattern in `AbstractPostgresIT` — start container in a `static {}` block, no `@Testcontainers`/`@Container`, wire via `@DynamicPropertySource`. Container lives for the whole JVM run; Ryuk cleans up at exit. Also sped ITs up (context reuse).
**Prevents:** Flaky/slow ITs as the suite grows.

---

## Quick Tasks Completed

| # | Description | Date | Commit | Status |
| --- | --- | --- | --- | --- |

---

## Deferred Ideas

- [ ] Redis L2 distributed cache — Captured during: project init (scaling)
- [ ] Multi-route per-rule upstream — Captured during: project init
- [ ] Per-user/header-varied caching w/ safety controls — Captured during: project init
- [ ] Cache stampede / single-flight on MISS — Captured during: project init

---

## Todos

- [x] Write feature spec for Cache Proxy Core (P1 vertical slice)
- [x] Verify Maven coordinates — pinned Boot 4.1.0, spring-cloud 2025.1.2, TC 2.0.5, Flyway 12.4.0
- [x] Design + Tasks + Execute (T1-T8) for Cache Proxy Core
- [x] Rule Store admin REST CRUD (`/admin/rules`, AD-004) + reload/evict on mutation + gateway excludes /admin & /actuator
- [x] Dockerize — Dockerfile (multi-stage temurin 26) + docker-compose (postgres + go-httpbin upstream + app); `docker compose up -d`
- [ ] Next feature: Observability — Micrometer hit/miss/bypass counters (X-Cache header already done in filter)
- [x] Git initialized + pushed → github.com/patrickonofre/p5k-proxy-cache (8 atomic feature commits on top of remote Initial commit; SSH, branch main)

---

## Preferences

**Model Guidance Shown:** 2026-06-24
