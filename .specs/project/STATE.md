# State

**Last Updated:** 2026-06-24
**Current Work:** ✅ App Registry + Per-App Rule Routing COMPLETE (47 tests: 18 unit + 29 IT, `mvn verify` green). Multi-upstream: each `application` (slug + base_url + default_ttl) registered via `/admin/applications`; rules carry `applicationId`. Request `/{slug}/rest` → resolve app → stripPrefix(1) → match rules → HIT/MISS/BYPASS; unknown slug → 404. Supersedes single-upstream (AD-002). MVP fields added: app/rule `description`, `updated_at`, `app.default_ttl_seconds` (rule.ttl now optional). ✅ Cache Proxy Core + Rule Store admin CRUD (earlier). ✅ Dockerized. Next M1: Observability (Micrometer counters).
**Project renamed:** pk-open-cache → p5k-proxy-cache (artifact com.p5k:p5k-proxy-cache). Working dir folder still named pk-open-cache.

---

## Recent Decisions (Last 60 days)

### AD-006: Multi-upstream via Application registry + path-prefix routing (2026-06-24)

**Decision:** Proxy fronts N backends. Each `application` row holds slug + base_url + optional default_ttl; every `cache_rule` references one application. Request `/{slug}/rest` resolves the app by slug, strips the slug (`stripPrefix(1)`), and matches `rest` against that app's rules. **Supersedes AD-002 (single upstream); pulls M2 "Multi-route" into M1.**
**Reason:** One proxy serving many APIs; keeps rule `path_pattern` backend-relative and disambiguates apps sharing a path.
**Trade-off:** Clients must namespace requests by slug; unknown slug → 404 (no upstream).
**Impact:** New `application` table + `/admin/applications` CRUD; `cache_rule.application_id` FK (ON DELETE CASCADE). Passthrough preserved: resolved app + no rule → BYPASS forward.

### AD-007: MVP data enrichment for rules/apps (2026-06-24)

**Decision:** Add `description` (app + rule), `updated_at` (app + rule), `application.default_ttl_seconds`. Rule `ttl_seconds` now optional — effective TTL = `rule.ttl ?? app.default_ttl`, both null → 400. Rule `priority` deferred (AntPathMatcher specificity suffices).
**Impact:** `CacheRule.ttlSeconds` is `Long` (nullable); `CaffeineCacheRegistry` builds caches from a resolved effective TTL passed in.

### AD-008: Dynamic upstream via GATEWAY_REQUEST_URL_ATTR (2026-06-24, VERIFIED)

**Decision:** Approach 1 from design — `CachingProxyFilter` sets `MvcUtils.GATEWAY_REQUEST_URL_ATTR` (the app base_url URI) per request; the gateway route is `predicate + stripPrefix(1) + http()` with no static `uri()`. The RestClient fallback (Approach 2) was not needed.
**Reason:** Reuses Spring Cloud Gateway forwarding (AD-001/005) with a per-request target.
**Impact:** Verified live via `mvn verify` (GatewayRouteIT slug-strip, CachingProxyFilterIT HIT/MISS/BYPASS/404, two-app isolation). See L-006.

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

### L-006: SCG server-webmvc dynamic upstream via GATEWAY_REQUEST_URL_ATTR (2026-06-24)

**Context:** Needed a per-request upstream target (multi-app) instead of the static `before(uri(base))`.
**Verified (javap on gateway-server-webmvc 5.0.2):** `MvcUtils.GATEWAY_REQUEST_URL_ATTR` constant + `MvcUtils.setRequestUrl(ServerRequest, URI)` + `BeforeFilterFunctions.stripPrefix(int)` all exist.
**Solution:** A servlet filter ahead of the route sets `request.setAttribute(GATEWAY_REQUEST_URL_ATTR, URI.create(app.baseUrl))`; route = `predicate + stripPrefix(1) + http()` (no static uri). `http()` reads the attribute and forwards. Confirmed live by `mvn verify`.
**Prevents:** Reaching for a custom RestClient forwarder when SCG already supports a dynamic target. baseUrl should be scheme://host[:port] (path comes from the stripped request).

### L-007: Shared Testcontainers DB → each IT class must clean its own tables (2026-06-24)

**Context:** `CacheRuleRepositoryIT` seeded a fixed slug "app" and failed with `duplicate key ... application_slug_key`.
**Problem:** All ITs share one singleton Postgres (L-004); rows leak across classes/methods. A fixed unique value collides.
**Solution:** Every IT class clears `cache_rule` then `application` in `@BeforeEach` (rules before apps, or rely on FK cascade). ITs run sequentially (TESTING.md) so global cleanup is safe.
**Prevents:** Order-dependent IT failures from leftover unique rows.

---

## Quick Tasks Completed

| # | Description | Date | Commit | Status |
| --- | --- | --- | --- | --- |

---

## Deferred Ideas

- [ ] Redis L2 distributed cache — Captured during: project init (scaling)
- [x] Multi-route per-app upstream — DONE 2026-06-24 (AD-006, App Registry feature)
- [ ] Rule `priority` override for matcher tie-break — Deferred from AD-007 (MVP)
- [ ] Per-app default headers / timeout / retries — Deferred (M2 hardening)
- [ ] Hard DB NOT NULL on `cache_rule.application_id` (backfill migration) — nullable for now
- [ ] Per-user/header-varied caching w/ safety controls — Captured during: project init
- [ ] Cache stampede / single-flight on MISS — Captured during: project init

---

## Todos

- [x] Write feature spec for Cache Proxy Core (P1 vertical slice)
- [x] Verify Maven coordinates — pinned Boot 4.1.0, spring-cloud 2025.1.2, TC 2.0.5, Flyway 12.4.0
- [x] Design + Tasks + Execute (T1-T8) for Cache Proxy Core
- [x] Rule Store admin REST CRUD (`/admin/rules`, AD-004) + reload/evict on mutation + gateway excludes /admin & /actuator
- [x] Dockerize — Dockerfile (multi-stage temurin 26) + docker-compose (postgres + app; upstream = external cache-test-origin on host :8081); `docker compose up -d`
- [x] App Registry + Per-App Rule Routing (AD-006/007/008) — `/admin/applications` CRUD, rule→app FK, slug-prefix routing, MVP fields; 47 tests green
- [ ] Next feature: Observability — Micrometer hit/miss/bypass counters (X-Cache header already done in filter)
- [x] Git initialized + pushed → github.com/patrickonofre/p5k-proxy-cache (8 atomic feature commits on top of remote Initial commit; SSH, branch main)

---

## Preferences

**Model Guidance Shown:** 2026-06-24
