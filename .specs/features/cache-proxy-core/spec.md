# Cache Proxy Core Specification

## Problem Statement

Repeated identical GET requests hit the origin API unnecessarily, consuming its capacity. We need the proxy to serve mapped GET endpoints from an in-memory cache so HITs never reach the origin, while transparently forwarding everything else. This is the core vertical slice of the product — the request/response path that makes p5k-proxy-cache useful.

## Goals

- [ ] A mapped GET request with a fresh cache entry is served from Caffeine with **zero calls to the upstream** (verifiable via upstream request count + `X-Cache: HIT`).
- [ ] A mapped GET MISS forwards once, caches the 2xx response for the rule's TTL, and subsequent identical requests HIT.
- [ ] Any unmatched or non-GET request is forwarded unchanged (transparent passthrough).

## Out of Scope

| Feature | Reason |
| --- | --- |
| Per-user / header-varied cache | AD-003: key = method+path+query only; avoids cross-user leak |
| Multi-route (per-rule upstream) | AD-002: single upstream in v1 |
| Cache invalidation / manual purge | Deferred to M2 |
| Caching non-GET, ETag/conditional | Deferred to M2 |
| Distributed cache (Redis L2) | Deferred to M3; Caffeine single-node |
| Stampede protection (single-flight) | Deferred to M3; concurrent MISS may double-fetch in v1 |
| Admin CRUD of rules | Separate feature (Rule Store); this feature consumes rules |

**Depends on:** Project Scaffold (runnable Boot 4.1 + Gateway + Postgres) and Rule Store (rules loadable, at least one enabled rule available — may be seeded for testing).

---

## User Stories

### P1: Serve mapped GET from cache (HIT) ⭐ MVP

**User Story**: As an API consumer, I want a repeated mapped GET to be answered from cache so that the origin API is not called again.

**Why P1**: This is the product's core value — offloading read traffic from the origin.

**Acceptance Criteria**:

1. WHEN a GET request matches an enabled rule AND a non-expired entry exists for key `method + path + canonical-query` THEN system SHALL return the cached status, content-type, and body WITHOUT forwarding to the upstream.
2. WHEN a cached response is served THEN system SHALL set response header `X-Cache: HIT`.
3. WHEN the cached entry's TTL has elapsed THEN system SHALL treat the request as a MISS (re-fetch).

**Independent Test**: Seed a rule for `/foo`. Request `/foo` twice against a counting stub upstream; second response has `X-Cache: HIT` and upstream call count stays at 1.

---

### P1: Forward and cache on MISS ⭐ MVP

**User Story**: As an API consumer, I want the first mapped GET to be forwarded and its response cached so that the next identical request is a HIT.

**Why P1**: A cache is useless without a correct populate path.

**Acceptance Criteria**:

1. WHEN a GET matches an enabled rule AND no fresh entry exists THEN system SHALL forward to the configured upstream, return the upstream response, AND store it in Caffeine under the rule's `ttl_seconds`.
2. WHEN the upstream response status is **2xx** THEN system SHALL cache it; WHEN it is non-2xx THEN system SHALL return it but SHALL NOT cache it.
3. WHEN a response is served from the upstream THEN system SHALL set response header `X-Cache: MISS`.
4. WHEN the upstream is unreachable or exceeds the configured timeout THEN system SHALL return `502`/`504` and SHALL NOT cache.

**Independent Test**: First request to `/foo` returns `X-Cache: MISS`, upstream count = 1; inspect cache → entry present. Stub a 500 → response returned, no cache entry created.

---

### P1: Passthrough unmatched & non-GET requests ⭐ MVP

**User Story**: As an API consumer, I want requests that aren't mapped (or aren't GET) to behave exactly as if the proxy weren't there, so the proxy is a safe drop-in.

**Why P1**: A drop-in proxy must never break uncovered traffic.

**Acceptance Criteria**:

1. WHEN a request matches no enabled rule THEN system SHALL forward to the upstream and return the response WITHOUT caching, setting `X-Cache: BYPASS`.
2. WHEN a request method is not GET THEN system SHALL forward without caching, even if the path matches a rule.
3. WHEN a matching rule is disabled THEN system SHALL passthrough (treat as no match).

**Independent Test**: POST `/foo` (rule exists) forwards every time, no cache. GET `/unmapped` forwards every time with `X-Cache: BYPASS`.

---

### P2: Canonical cache key

**User Story**: As an operator, I want query-param order to not fragment the cache, so that `?a=1&b=2` and `?b=2&a=1` share one entry.

**Why P2**: Correctness/efficiency improvement; without it the cache still works but fragments.

**Acceptance Criteria**:

1. WHEN two GETs differ only in query-parameter order THEN system SHALL resolve them to the same cache key (params sorted before keying).

**Independent Test**: Request `/foo?a=1&b=2` (MISS), then `/foo?b=2&a=1` returns `X-Cache: HIT`, upstream count = 1.

---

## Edge Cases

- WHEN a rule's `path_pattern` matches via prefix/wildcard AND multiple rules match THEN system SHALL apply the most specific (longest) match deterministically.
- WHEN the upstream response has no `Content-Type` THEN system SHALL still cache the body and status.
- WHEN concurrent MISSes occur for the same key THEN system MAY forward more than once (single-flight is out of scope); cache SHALL remain consistent (last write wins).
- WHEN Caffeine reaches the rule's `max_size` THEN it SHALL evict per Caffeine policy without erroring requests.
- WHEN a GET carries a request body or unusual headers THEN keying SHALL ignore them (key = method+path+canonical-query only).
- WHEN the upstream sends hop-by-hop headers (e.g. `Connection`, `Transfer-Encoding`) THEN system SHALL NOT replay them from cache.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| CACHE-01 | P1: HIT serve from cache | Design | Pending |
| CACHE-02 | P1: HIT sets `X-Cache: HIT` | Design | Pending |
| CACHE-03 | P1: expired TTL → MISS | Design | Pending |
| CACHE-04 | P1: MISS forwards + caches 2xx w/ TTL | Design | Pending |
| CACHE-05 | P1: non-2xx not cached | Design | Pending |
| CACHE-06 | P1: MISS sets `X-Cache: MISS` | Design | Pending |
| CACHE-07 | P1: upstream down → 502/504, no cache | Design | Pending |
| CACHE-08 | P1: unmatched → passthrough `BYPASS` | Design | Pending |
| CACHE-09 | P1: non-GET → passthrough no cache | Design | Pending |
| CACHE-10 | P1: disabled rule → passthrough | Design | Pending |
| CACHE-11 | P2: canonical (sorted) query key | - | Pending |
| CACHE-12 | Edge: most-specific rule match | - | Pending |
| CACHE-13 | Edge: hop-by-hop headers not replayed | - | Pending |

**ID format:** `CACHE-[NUMBER]`
**Status values:** Pending → In Design → In Tasks → Implementing → Verified
**Coverage:** 13 total, 13 mapped to tasks (T1-T8), all ✅ Verified via `mvn verify` (25 tests, 2026-06-24)

---

## Success Criteria

- [ ] Mapped GET HIT serves correct status+body+content-type with `X-Cache: HIT` and **0** upstream calls.
- [ ] Mapped GET MISS results in exactly **1** upstream call, a cache entry honoring TTL, and a subsequent HIT.
- [ ] Non-2xx and non-GET and unmatched traffic is never served from or written to cache.
- [ ] Upstream failure surfaces as 5xx without poisoning the cache.
