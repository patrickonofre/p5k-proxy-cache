# Design — Application Registry + Per-App Rule Routing

## Architecture Overview

One proxy, N backends. A request is namespaced by the **first path segment** (the app slug). The
caching filter resolves slug → application, strips the slug, matches the remainder against that app's
rules, and either serves from cache or forwards to the app's `base_url`.

```mermaid
flowchart TD
    R[GET /{slug}/rest?q] --> F[CachingProxyFilter<br/>HIGHEST_PRECEDENCE]
    F --> AD{admin/actuator?}
    AD -- yes --> LOCAL[handled locally]
    AD -- no --> SLUG[parse slug = first segment]
    SLUG --> APP{ApplicationRegistry<br/>resolve slug}
    APP -- none/disabled --> P404[404]
    APP -- app --> MATCH{RuleRegistry.match<br/>app.id, GET, rest}
    MATCH -- no rule --> BYPASS[strip prefix + set target=base_url<br/>forward, X-Cache: BYPASS]
    MATCH -- rule --> HIT{cache HIT?}
    HIT -- yes --> SERVE[write cached body<br/>X-Cache: HIT, no upstream]
    HIT -- no --> FWD[strip prefix + set target=base_url<br/>forward via gateway route<br/>capture 2xx, store, X-Cache: MISS]
```

> Diagram tip: `mermaid-studio` skill is not installed — rendered inline. Install it for SVG/PNG export
> and validation. (Shown once.)

## Components

### New

| Component | Responsibility |
| --- | --- |
| `rules/Application` (entity) | maps `application` table |
| `rules/ApplicationRepository` | `JpaRepository`; `findBySlug`, `findAllByEnabledTrue`, `existsBySlug` |
| `rules/ApplicationRegistry` | in-memory `slug → Application` snapshot of enabled apps; `reload()`, `resolve(slug)` |
| `rules/ApplicationAdminService` | CRUD apps; on delete cascades rules + evicts their caches + reloads both registries |
| `web/ApplicationAdminController` | `/admin/applications` REST (201/200/204/404/400/409) |
| `web/ApplicationRequest` / `ApplicationResponse` | DTOs + bean validation |
| `proxy/SlugRouting` (helper) | split first segment → `(slug, remainder)`; pure, unit-testable |

### Modified

| Component | Change |
| --- | --- |
| `rules/CacheRule` | add `applicationId` (Long), `description`, `updatedAt`; `ttlSeconds` → `Long` (nullable) |
| `web/CacheRuleRequest` | add `@NotNull applicationId`, optional `description`; `ttlSeconds` optional (`Long`) |
| `web/CacheRuleResponse` | add `applicationId`, `description`, `updatedAt` |
| `rules/RuleRegistry` | `match(long appId, String method, String path)` — filter snapshot by `applicationId` |
| `rules/RuleAdminService` | validate `applicationId` exists; resolve effective TTL; set `updatedAt` on update |
| `cache/CaffeineCacheRegistry` | build cache with **effective TTL** (rule.ttl ?? app.default) — needs the value, not the rule alone → pass `effectiveTtlSeconds` |
| `proxy/CachingProxyFilter` | parse slug → resolve app (404 if none) → match app rules on remainder → set dynamic upstream + strip prefix |
| `config/GatewayConfig` | route target becomes **dynamic** per request (see Routing Mechanism) instead of static `uri(base)` |
| `config/ProxyFilterConfig` | inject `ApplicationRegistry` into the filter |
| `application.yml` | `proxy.upstream.base-url` deprecated/removed (no longer a single static target) |

## Routing Mechanism (⚠ VERIFY at execution — bleeding-edge, per L-001)

Current `GatewayConfig` binds a **static** `before(uri(base))` at bean build. For N apps the target is
per-request. Two candidate approaches — confirm #1 against Spring Cloud Gateway **server-webmvc**
(Oakwood 2025.1.x) docs/source before coding; fall back to #2 if unsupported.

**Approach 1 (preferred — keep Spring Cloud Gateway, AD-001/AD-005):**
- Our filter computes `targetUri = app.base_url` and the stripped path, then sets the gateway request
  URL attribute so the route's `http()` handler forwards there.
  - *Candidate attribute*: `MvcUtils.GATEWAY_REQUEST_URL_ATTR` (what `BeforeFilterFunctions.uri(...)`
    sets internally). **Not yet confirmed for this version** — verify exact constant/API.
  - Prefix strip: `BeforeFilterFunctions.stripPrefix(1)` on the route, or rewrite the path in the
    forwarded request (e.g. a request wrapper) so upstream receives `/rest`.
- `GatewayConfig` route predicate stays `/**` minus `/admin` `/actuator`; target supplied dynamically.

**Approach 2 (fallback — direct forward in filter):**
- On MISS/BYPASS, the filter performs the upstream call itself with a `RestClient`
  (`baseUrl(app.base_url)`), captures the response, stores 2xx. Drops the gateway route for the proxy
  path. Simpler routing, but reimplements header/stream copying the gateway already does, and partly
  steps away from SCG.

**Decision:** attempt Approach 1; if the dynamic-target attribute proves unavailable/unstable on
Oakwood server-webmvc, switch to Approach 2. Record the outcome as a new AD in STATE.md at execution.

## Effective TTL

```
effectiveTtl(rule, app):
    t = rule.ttlSeconds != null ? rule.ttlSeconds : app.defaultTtlSeconds
    require t != null && t > 0   // else reject at API (RULE-11)
    return t
```
`CaffeineCacheRegistry.cacheFor` currently reads `rule.getTtlSeconds()`. Change to accept the resolved
TTL (the filter/service computes it and passes it). The cache is keyed by rule id, so the app's default
is baked into that rule's cache at build time; rebuild on rule/app update.

## Migration — `V2__application_and_rule_link.sql`

```sql
CREATE TABLE application (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    slug                VARCHAR(64)              NOT NULL UNIQUE,
    name                VARCHAR(128)             NOT NULL,
    base_url            VARCHAR(512)             NOT NULL,
    description         VARCHAR(512),
    default_ttl_seconds BIGINT,
    enabled             BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE
);

ALTER TABLE cache_rule ADD COLUMN application_id BIGINT;
ALTER TABLE cache_rule ADD COLUMN description    VARCHAR(512);
ALTER TABLE cache_rule ADD COLUMN updated_at     TIMESTAMP WITH TIME ZONE;
ALTER TABLE cache_rule ALTER COLUMN ttl_seconds DROP NOT NULL;

ALTER TABLE cache_rule
    ADD CONSTRAINT fk_cache_rule_application
    FOREIGN KEY (application_id) REFERENCES application (id) ON DELETE CASCADE;

CREATE INDEX idx_cache_rule_application ON cache_rule (application_id);
```

**Migration-safety note:** `application_id` is added **nullable** at the DB level (avoids failing on any
pre-existing dev rows; STATE confirms no prod data). It is **required at the API layer** (`@NotNull` +
service check). `RuleRegistry` ignores rules with a null/unknown `application_id`. If we later want a hard
DB constraint, a follow-up migration can `SET NOT NULL` after backfill.

## Edge Cases

| Case | Behavior |
| --- | --- |
| `/` or empty path (no slug) | no app → 404 |
| `/{slug}` with no remainder | remainder = `/`; match rules, else BYPASS forward to `base_url/` |
| slug == `admin` or `actuator` | excluded before slug parsing (predicate negation preserved) |
| app disabled | `resolve` returns empty → 404 (same as unknown) |
| rule disabled | not in registry snapshot → treated as no-match → BYPASS |
| delete app with rules | ON DELETE CASCADE removes rules; service evicts each rule's cache + reloads |
| two apps, same rule path | distinct app rules, distinct rule ids, distinct caches → isolated |

## Test Strategy (see TESTING.md)

- Unit: `SlugRouting` split; `RuleRegistry.match` app-scoped; effective-TTL resolution.
- IT (Testcontainers, singleton per `AbstractPostgresIT`, L-004):
  - `ApplicationAdminControllerIT` — CRUD + validation + dup-slug 409 + cascade delete.
  - `RuleAdminControllerIT` (extend) — applicationId required/unknown, ttl inheritance, description/updated_at.
  - Routing IT — `/{slug}/path` HIT/MISS/BYPASS/404, prefix strip (assert upstream saw `/rest`), two-app isolation. Use `StubUpstream` with two stubs or path assertions.
- Gate: `mvn verify` green (`full` profile). Update existing ITs that assumed single upstream + non-prefixed paths.
```
</content>
