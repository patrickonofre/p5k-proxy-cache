# Roadmap

**Current Milestone:** M1 — Cacheable Proxy MVP
**Status:** In Progress — Core + Rule Store + App Registry/Multi-app routing shipped (47 tests green); Observability pending

---

## M1 — Cacheable Proxy MVP

**Goal:** A runnable proxy that fronts one upstream API, caches mapped GET endpoints in Caffeine, serves HITs without touching the origin, and exposes admin CRUD for rules.
**Target:** First working vertical slice — request → HIT served from cache.

### Features

**Project Scaffold** - COMPLETE

- Spring Boot 4.1 + Spring Cloud Gateway (train 2025.1.x) project, Java 26
- PostgreSQL + Flyway baseline migration
- Actuator/Micrometer wired

**Rule Store** - COMPLETE — schema/entity/repo + registry + admin REST CRUD (`/admin/rules`) with live reload & cache eviction

- `cache_rule` table + JPA entity (path_pattern, methods, ttl_seconds, max_size, enabled)
- In-memory rule cache loaded from Postgres
- Admin REST CRUD for rules

**App Registry + Per-App Routing** - COMPLETE (AD-006/007/008) — multi-upstream

- `application` table + `/admin/applications` CRUD (slug, base_url, default_ttl, description, enabled)
- `cache_rule.application_id` FK (ON DELETE CASCADE) + MVP fields (description, updated_at, optional ttl)
- Slug-prefix routing: `/{slug}/rest` → resolve app → stripPrefix(1) → dynamic upstream; unknown slug → 404

**Cache Proxy Core** - COMPLETE

- Single-upstream forwarding via Gateway route
- Rule matcher (request path/method vs rules)
- Caffeine lookup/store keyed by `method + path + query`, per-rule TTL
- HIT → serve from cache (no origin call); MISS → forward, store, return
- Passthrough for unmatched requests

**Observability** - IN PROGRESS — `X-Cache` header done in filter; Micrometer counters pending

- `X-Cache: HIT|MISS` response header ✅
- Hit/miss counters via Micrometer, exposed through Actuator — pending

---

## M2 — Hardening & Reach

**Goal:** Make it production-leanable beyond a single happy path.

### Features

**Multi-route upstreams** - DONE (pulled into M1 — see App Registry, AD-006)
**Cache invalidation (manual purge + TTL refresh)** - PLANNED
**Admin auth (API key / basic)** - PLANNED
**Conditional requests (ETag / If-None-Match passthrough)** - PLANNED

---

## M3 — Scale

**Goal:** Multi-node consistency.

### Features

**Redis L2 distributed cache** - PLANNED
**Cache stampede protection (single-flight on MISS)** - PLANNED

---

## Future Considerations

- Per-user / header-varied caching with explicit safety controls
- Cache warming / pre-fetch
- Per-rule cache size & eviction tuning dashboard
- Non-GET cache strategies (idempotent POST)
