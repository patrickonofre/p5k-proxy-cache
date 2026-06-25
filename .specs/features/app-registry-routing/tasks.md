# Tasks — Application Registry + Per-App Rule Routing

Gates (TESTING.md): `quick` = `./mvnw -q test`, `full` = `./mvnw -q verify`, `build` = `./mvnw -q -DskipTests package`.
`[P]` = parallelizable with siblings sharing the same deps.

---

### T1 — V2 migration: `application` table + `cache_rule` link/fields
- **What:** add `db/migration/V2__application_and_rule_link.sql` exactly as in design.md (new `application`, `cache_rule.application_id` FK nullable, `description`, `updated_at`, `ttl_seconds` drop NOT NULL, index).
- **Where:** `src/main/resources/db/migration/V2__application_and_rule_link.sql`
- **Depends on:** —
- **Done when:** Flyway applies clean on a fresh Postgres (validated by any IT booting context).
- **Gate:** build (full once T-repo IT exists)

### T2 — `Application` entity + repository  `[P after T1]`
- **What:** `rules/Application` JPA entity (fields per spec; `@CreationTimestamp createdAt`, `updatedAt` plain), `rules/ApplicationRepository` (`findBySlug`, `findAllByEnabledTrue`, `existsBySlug`).
- **Where:** `src/main/java/com/p5k/proxycache/rules/Application.java`, `ApplicationRepository.java`
- **Depends on:** T1
- **Done when:** compiles; entity maps table.
- **Gate:** build

### T3 — `CacheRule` entity changes  `[P after T1]`
- **What:** add `applicationId` (Long), `description` (String), `updatedAt` (Instant); change `ttlSeconds` to `Long` (nullable) + getters/setters; update both constructors.
- **Where:** `src/main/java/com/p5k/proxycache/rules/CacheRule.java`
- **Depends on:** T1
- **Done when:** compiles; nullable ttl honored.
- **Gate:** build

### T4 — `ApplicationRegistry` (slug snapshot)
- **What:** in-memory `Map<String,Application>` of enabled apps; `reload()` (`@PostConstruct`), `resolve(slug) → Optional<Application>`. Mirror `RuleRegistry` style (volatile snapshot).
- **Where:** `rules/ApplicationRegistry.java` + `rules/ApplicationRegistryTest.java`
- **Depends on:** T2
- **Done when:** resolve hits enabled, misses disabled/unknown.
- **Gate:** quick (unit)

### T5 — `RuleRegistry.match` app-scoped
- **What:** change signature to `match(long applicationId, String method, String path)`; filter snapshot by `applicationId` before specificity match; ignore rules with null app.
- **Where:** `rules/RuleRegistry.java` + update `rules/RuleRegistryTest.java`
- **Depends on:** T3
- **Done when:** only the given app's rules match; two apps same path isolated in test.
- **Gate:** quick (unit)

### T6 — DTOs + validation
- **What:** `web/ApplicationRequest` (`@NotBlank slug` w/ `@Pattern ^[a-z0-9][a-z0-9-]*$`, `@NotBlank name`, `@NotBlank baseUrl`, optional `description`, optional `@Positive Long defaultTtlSeconds`, `boolean enabled`), `web/ApplicationResponse` (+`from`). Update `web/CacheRuleRequest` (`@NotNull Long applicationId`, optional `Long ttlSeconds` no longer `@Positive`-required, optional `description`) and `web/CacheRuleResponse` (+`applicationId`, `description`, `updatedAt`).
- **Where:** `web/ApplicationRequest.java`, `ApplicationResponse.java`, `CacheRuleRequest.java`, `CacheRuleResponse.java`
- **Depends on:** T2, T3
- **Done when:** compiles; validation annotations present.
- **Gate:** build

### T7 — `ApplicationAdminService` + `ApplicationAdminController` + IT
- **What:** CRUD service (create/list/get/update/delete). Update sets `updatedAt`. Delete cascades (FK ON DELETE CASCADE) then evicts each deleted rule's cache + reloads `RuleRegistry` and `ApplicationRegistry`. Dup slug → 409 (`existsBySlug` / catch constraint). Controller `/admin/applications` → 201/200/204/404/400/409. `ApplicationAdminControllerIT` covers APP-01..07.
- **Where:** `rules/ApplicationAdminService.java`, `web/ApplicationAdminController.java`, `web/ApplicationAdminControllerIT.java`
- **Depends on:** T2, T4, T6
- **Done when:** APP-01..07 green.
- **Gate:** full

### T8 — `RuleAdminService` app-link + effective TTL + IT updates
- **What:** create/update validate `applicationId` exists (else 400); resolve effective TTL = `rule.ttl ?? app.default_ttl`, reject if both null (400); set `description`; set `updatedAt` on update. Extend `RuleAdminControllerIT` for RULE-09..11.
- **Where:** `rules/RuleAdminService.java`, `web/RuleAdminControllerIT.java`
- **Depends on:** T3, T5, T6, T2
- **Done when:** RULE-09..11 green; existing RULE-01..08 adapted (rules now need an app).
- **Gate:** full

### T9 — `CaffeineCacheRegistry` effective TTL
- **What:** build cache from a resolved `effectiveTtlSeconds` (passed in) instead of `rule.getTtlSeconds()`; rebuild on update. Keep per-rule-id keying.
- **Where:** `cache/CaffeineCacheRegistry.java` + update `CaffeineCacheRegistryTest.java`
- **Depends on:** T3, T5
- **Done when:** cache TTL reflects effective value; unit green.
- **Gate:** quick

### T10 — Routing: slug parse + dynamic upstream + prefix strip  ⚠ VERIFY FIRST
- **What:** (1) **Verify** the Spring Cloud Gateway server-webmvc dynamic-target mechanism (design.md Approach 1: `MvcUtils.GATEWAY_REQUEST_URL_ATTR` + `stripPrefix(1)`); if unworkable, switch to Approach 2 (RestClient) and record an AD. (2) `proxy/SlugRouting` helper (split first segment) + unit test. (3) `CachingProxyFilter`: skip `/admin/**`,`/actuator/**`; parse slug; `ApplicationRegistry.resolve` → 404 if none; `RuleRegistry.match(app.id, method, remainder)`; on rule set effective TTL via `CaffeineCacheRegistry`; HIT serve; MISS/BYPASS set target=`base_url`, strip prefix, forward. (4) `GatewayConfig` dynamic target. (5) `ProxyFilterConfig` inject `ApplicationRegistry`.
- **Where:** `proxy/SlugRouting.java` (+test), `proxy/CachingProxyFilter.java`, `config/GatewayConfig.java`, `config/ProxyFilterConfig.java`
- **Depends on:** T4, T5, T9
- **Done when:** ROUTE-01..05 logic in place; slug parse unit green.
- **Gate:** quick (unit for SlugRouting); full via T11

### T11 — Routing IT + adapt existing ITs
- **What:** new routing IT — `/{slug}/path` HIT/MISS/BYPASS/404, prefix strip (assert `StubUpstream` saw `/rest`), two-app isolation, HIT = 0 upstream calls. Adapt `GatewayRouteIT`, `CachingProxyFilterIT`, `CacheRuleRepositoryIT`, `RuleAdminControllerIT`, `P5kProxyCacheApplicationIT` to seed an `application` row and use slug-prefixed paths.
- **Where:** `proxy/*IT.java`, `config/GatewayRouteIT.java`, `rules/CacheRuleRepositoryIT.java`, `web/RuleAdminControllerIT.java`, `P5kProxyCacheApplicationIT.java`, `support/StubUpstream.java` (path-capture if needed)
- **Depends on:** T10
- **Done when:** ROUTE-01..05 verified; whole suite green.
- **Gate:** full

### T12 — Docs: STATE / ROADMAP / spec traceability / README
- **What:** STATE.md — AD-006 (supersede AD-002, multi-app via path-prefix), AD-007 (routing decisions D-1..D-5), and the AD recording T10's chosen mechanism (Approach 1 or 2) + any new lesson. ROADMAP — move "Multi-route upstreams" into M1 (done) / note. Flip spec.md traceability to Verified. README usage (register app, then rule). 
- **Where:** `.specs/project/STATE.md`, `.specs/project/ROADMAP.md`, `.specs/features/app-registry-routing/spec.md`, `README.md`
- **Depends on:** T1–T11
- **Done when:** docs reflect shipped behavior.
- **Gate:** build (docs only)

---

## Dependency Graph

```
T1 ──┬─ T2 ─┬─ T4 ─────────────┐
     │      └─ T7(IT) ───────┐ │
     └─ T3 ─┬─ T5 ─┬─ T9 ────┼─┼─ T10 ─ T11 ─ T12
            │      └─────────┘ │
            └─ T6 ─┬─ T7 ──────┘
                   └─ T8(IT)
```

Parallelizable: **T2 ∥ T3** (after T1). After both: **T4 ∥ T5 ∥ T6**. T7/T8 after their deps. T9 after T3/T5. T10 is the integration choke point (single-threaded, VERIFY gate). T11 sequential (shared Testcontainers, TESTING.md).

## Risk

- **T10 mechanism** is the only real unknown (bleeding-edge SCG server-webmvc). Verify before writing; Approach 2 is the escape hatch. Everything else is conventional CRUD/JPA/Flyway, low risk.
</content>
