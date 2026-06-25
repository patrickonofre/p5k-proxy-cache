# Roadmap

**Current Milestone:** M1 — Cacheable Proxy MVP
**Status:** In Progress — Cache Proxy Core shipped (25 tests green)

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

**Multi-route upstreams** - PLANNED
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
