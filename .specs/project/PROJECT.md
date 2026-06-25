# p5k-proxy-cache

**Vision:** Generic HTTP caching reverse proxy that sits in front of any API. Maps which endpoints should be cached so cache hits never reach the origin application — only the cache layer.
**For:** Backend teams who want to offload read traffic from an existing API without touching its code.
**Solves:** Repeated, identical read requests hammer the origin API. A configurable drop-in cache proxy serves those responses and shields the backend.

## Goals

- Serve a cacheable GET request from memory without forwarding to the origin (cache HIT measured via `X-Cache: HIT` header and hit/miss metrics).
- Configure cacheable endpoints at runtime (path, TTL) via an admin API, no redeploy.
- Transparent passthrough for any unmapped request (proxy behaves as a normal forward when no rule matches).

## Tech Stack

**Core:**

- Framework: Spring Boot 4.1.0 (GA 2026-06-10) on Spring Framework 7
- Gateway: Spring Cloud Gateway, Spring Cloud release train **2025.1.x (Oakwood)** — first train compatible with Boot 4.1.0
- Language: Java 26 (Boot 4.1 baseline is Java 17; runs on 26)
- Cache: Caffeine (in-memory, single-node)
- Database: PostgreSQL (stores cache rules / app config)

**Key dependencies:** Spring Cloud Gateway, Caffeine, Spring Data JPA, Flyway (migrations), Micrometer/Actuator (metrics).

## Scope

**v1 includes:**

- Reverse proxy in front of a **single upstream** base URL.
- Cache **GET** responses in Caffeine with per-rule **TTL**, key = `method + path + query`.
- `cache_rule` config in PostgreSQL + in-memory rule cache.
- Admin REST API (CRUD) to manage rules at runtime.
- `X-Cache: HIT|MISS` response header + hit/miss metrics via Actuator.
- Passthrough for unmatched requests.

**Explicitly out of scope (v1):**

- Distributed/shared cache (Redis L2) — Caffeine is per-instance.
- Per-user cache (vary by `Authorization`) — risk of cross-user data leak.
- Caching non-GET methods, ETag/conditional requests.
- Cache invalidation by tag, manual purge, origin webhooks.
- Multi-route (per-rule upstream targets) — single upstream only in v1.
- Hardened admin auth (basic/none in v1).

## Constraints

- Technical: Caffeine is in-memory → horizontal scaling means divergent caches. v1 targets single-node. Redis L2 deferred.
- Technical: Bleeding-edge stack (Boot 4.1 GA ~2 weeks old, Java 26). Verify exact dependency versions against current docs before each implementation step.
