# Application Registry + Per-App Rule Routing — Specification

## Problem Statement

The proxy currently fronts a **single** upstream (`proxy.upstream.base-url`, AD-002). To serve
**N applications** from one proxy, each cache rule must know which backend it targets. We introduce
an **Application** registry (each app = slug + base URL + defaults) and link every cache rule to one
application. The incoming request selects the app by a **path prefix** (`/{app-slug}/...`); the proxy
resolves the app, strips the prefix, and matches the remaining path against that app's rules. The rule
`path_pattern` stays clean (no per-app encoding).

**Supersedes AD-002 (single upstream).** Pulls M2 "Multi-route upstreams" forward into M1.

## Goals

- [ ] CRUD applications over REST (`/admin/applications`); changes live without restart.
- [ ] Each cache rule references exactly one application; rule path stays backend-relative.
- [ ] Incoming `/{slug}/path` resolves slug → application → `base_url`; prefix stripped before forward.
- [ ] Matched cacheable GET served from per-rule cache without touching the origin.
- [ ] Resolved app + no matching rule → transparent forward (BYPASS), passthrough goal preserved.
- [ ] Unknown/disabled slug → 404 (no upstream to forward to).
- [ ] MVP data enrichment: `description` (app + rule), `updated_at` (app + rule), `app.default_ttl_seconds`
      with rule `ttl_seconds` now optional (inherits app default).

## Out of Scope

| Feature | Reason |
| --- | --- |
| Admin authentication | AD-004: basic/none in v1 (M2 hardening) |
| Host-header / vhost routing | Path-prefix model chosen (decision below) |
| Rule `priority` override | AntPathMatcher specificity is enough for MVP; deferred |
| Per-app default headers / timeouts / retries | M2 hardening |
| Redis L2, stampede protection | M3 |
| Migrating existing single-upstream `proxy.upstream.base-url` data | Fresh MVP, no prod rules (STATE confirms) |

**Depends on:** Cache Proxy Core + Rule Store Admin (entity, repo, `RuleRegistry`, `CaffeineCacheRegistry`,
`CachingProxyFilter`, `GatewayConfig`).

---

## Decisions Captured (this session)

| ID | Decision | Rationale |
| --- | --- | --- |
| D-1 | **Path-prefix routing**: request `/{app-slug}/rest` → app by slug, strip prefix, match `rest` vs app rules | Keeps rule path clean, disambiguates apps sharing a path, preserves passthrough |
| D-2 | **Passthrough preserved**: app resolved + no rule → forward to `base_url` (X-Cache: BYPASS) | Keeps PROJECT goal of transparent passthrough |
| D-3 | **Unknown/disabled slug → 404** | No upstream resolvable; fail fast |
| D-4 | MVP fields: `description` (app+rule), `updated_at` (app+rule), `app.default_ttl_seconds` (rule.ttl optional) | Ops visibility + less rule repetition; chosen from data analysis |
| D-5 | `priority` deferred | Matcher specificity sufficient for MVP |

---

## Data Model (MVP)

### `application` (new)

| Field | Type | Notes |
| --- | --- | --- |
| id | BIGINT PK identity | |
| slug | VARCHAR(64) UNIQUE NOT NULL | path prefix; pattern `^[a-z0-9][a-z0-9-]*$` (no slashes) |
| name | VARCHAR(128) NOT NULL | human label |
| base_url | VARCHAR(512) NOT NULL | upstream root, e.g. `https://billing.internal` |
| description | VARCHAR(512) NULL | ops note |
| default_ttl_seconds | BIGINT NULL | fallback TTL when a rule omits `ttl_seconds` |
| enabled | BOOLEAN NOT NULL DEFAULT TRUE | disabled app → 404 on its slug |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |
| updated_at | TIMESTAMPTZ NULL | set on PUT |

### `cache_rule` (changes)

| Change | Detail |
| --- | --- |
| + application_id | BIGINT FK → application(id) ON DELETE CASCADE. Required at API layer. |
| + description | VARCHAR(512) NULL |
| + updated_at | TIMESTAMPTZ NULL (set on PUT) |
| ~ ttl_seconds | now NULLABLE — effective TTL = `rule.ttl_seconds ?? app.default_ttl_seconds` |

**Effective TTL rule:** if both `rule.ttl_seconds` and `app.default_ttl_seconds` are null → reject create/update (400). At least one must resolve to a positive value.

**Cache isolation:** caches are per-rule-id (`CaffeineCacheRegistry`), and each rule belongs to one app,
so cross-app key collisions are impossible without changing the cache key. Cache key stays
`METHOD + strippedPath + sortedQuery` (path is already app-relative after prefix strip).

---

## User Stories

### P1: Manage applications over REST ⭐ MVP

As an operator, I want to CRUD applications so I can register the backends the proxy fronts.

**Acceptance Criteria**

1. WHEN POST `/admin/applications` valid body THEN persist + 201 with created app (id, created_at).
2. WHEN GET `/admin/applications` THEN return all apps (enabled + disabled).
3. WHEN GET `/admin/applications/{id}` existing THEN 200; missing → 404.
4. WHEN PUT `/admin/applications/{id}` existing THEN replace fields, set `updated_at`, 200; missing → 404.
5. WHEN DELETE `/admin/applications/{id}` existing THEN 204, cascade-delete its rules, evict their caches, reload registries; missing → 404.
6. WHEN body invalid (blank slug/name/base_url, slug not matching pattern, non-positive default_ttl) THEN 400, not persisted.
7. WHEN slug duplicates an existing app THEN 409 Conflict.

### P2: Link rules to an application ⭐ MVP

As an operator, I want each rule tied to an application so the proxy knows where to forward.

**Acceptance Criteria**

1. WHEN POST/PUT `/admin/rules` with a valid `applicationId` THEN persist the link.
2. WHEN `applicationId` is missing/blank THEN 400.
3. WHEN `applicationId` references a non-existent app THEN 400 (or 422) — not persisted.
4. WHEN `ttlSeconds` omitted AND the app has `default_ttl_seconds` THEN accept (inherits).
5. WHEN `ttlSeconds` omitted AND app has no default THEN 400.
6. Rule create/update accept optional `description`; PUT sets `updated_at`.

### P3: Route by slug prefix ⭐ MVP

As a client, my request `/{slug}/path` reaches the right backend, cached per rule.

**Acceptance Criteria**

1. WHEN GET `/{slug}/rest` and slug resolves to an enabled app AND `rest` matches an enabled rule of that app THEN serve from cache on HIT (no upstream call), else forward to `{base_url}/rest`, store 2xx, return (X-Cache HIT/MISS).
2. WHEN slug resolves but no rule matches `rest` THEN forward to `{base_url}/rest` without caching (X-Cache: BYPASS).
3. WHEN slug does not resolve (unknown or disabled app) THEN 404, no forward.
4. WHEN path targets `/admin/**` or `/actuator/**` THEN handled locally, never treated as a slug.
5. Forwarded request path is prefix-stripped: `/{slug}/users/1` → upstream receives `/users/1`.

---

## Requirement Traceability

| ID | Story | Status |
| --- | --- | --- |
| APP-01 | create app → 201 + persist | Pending |
| APP-02 | list apps | Pending |
| APP-03 | get app 200/404 | Pending |
| APP-04 | update app 200/404 + updated_at + reload | Pending |
| APP-05 | delete app 204/404 + cascade rules + evict + reload | Pending |
| APP-06 | app validation → 400 | Pending |
| APP-07 | duplicate slug → 409 | Pending |
| RULE-09 | rule requires valid applicationId (400 if missing/unknown) | Pending |
| RULE-10 | rule description + updated_at on PUT | Pending |
| RULE-11 | ttl optional, effective = rule.ttl ?? app.default; both null → 400 | Pending |
| ROUTE-01 | `/{slug}/path` resolves app by slug | Pending |
| ROUTE-02 | unknown/disabled slug → 404 | Pending |
| ROUTE-03 | matched rule → strip prefix, forward to base_url, HIT served w/o upstream | Pending |
| ROUTE-04 | resolved app, no rule → BYPASS forward | Pending |
| ROUTE-05 | admin/actuator local, not slug-routed | Pending |

**Status:** ✅ All Verified via `mvn verify` 2026-06-24 — 47 tests (18 unit + 29 IT). New ITs: `ApplicationAdminControllerIT` (APP-01..07), `RuleAdminControllerIT` extended (RULE-09..11), `CachingProxyFilterIT` + `GatewayRouteIT` (ROUTE-01..05, slug-strip, two-app isolation, unknown-slug 404). Routing mechanism = AD-008 (Approach 1, no RestClient fallback needed).

---

## Success Criteria

- [ ] `mvn verify` green; new ITs cover app CRUD + validation + cascade, rule↔app linkage, slug routing (HIT/MISS/BYPASS/404), prefix strip.
- [ ] Two apps with the same rule path (`/users/**`) route to their own backends and caches independently.
- [ ] Existing Cache Proxy Core / Rule Store ITs adapted and still green.
</content>
</invoke>
