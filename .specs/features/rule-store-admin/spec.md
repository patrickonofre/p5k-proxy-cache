# Rule Store Admin CRUD Specification

## Problem Statement

Cache rules exist in `cache_rule` and are loaded into `RuleRegistry` at startup, but there is no way to manage them at runtime. Operators need a REST API to create, read, update, and delete rules, with changes taking effect immediately (no restart) and stale cached entries purged on change.

## Goals

- [ ] CRUD cache rules over REST; changes are visible to the proxy without a restart.
- [ ] Mutating a rule purges its cached entries and reloads the in-memory registry.
- [ ] Admin endpoints are served locally, never proxied to the upstream.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Admin authentication | AD-004: basic/none in v1 (M2 hardening) |
| Per-field PATCH | PUT replaces the rule; enable/disable via `enabled` field |
| Pagination/filtering on list | Small rule sets in v1 |

**Depends on:** Cache Proxy Core (entity, repo, `RuleRegistry`, `CaffeineCacheRegistry`).

---

## User Stories

### P1: Manage cache rules over REST ⭐ MVP

**User Story**: As an operator, I want to CRUD cache rules via REST so I can control caching without redeploying.

**Acceptance Criteria**:

1. WHEN POST `/admin/rules` with a valid body THEN system SHALL persist the rule and return 201 with the created rule (including id).
2. WHEN GET `/admin/rules` THEN system SHALL return all rules (enabled and disabled).
3. WHEN GET `/admin/rules/{id}` for an existing id THEN system SHALL return 200 with the rule; for a missing id, 404.
4. WHEN PUT `/admin/rules/{id}` for an existing id THEN system SHALL replace its fields and return 200; for a missing id, 404.
5. WHEN DELETE `/admin/rules/{id}` for an existing id THEN system SHALL remove it and return 204; for a missing id, 404.
6. WHEN a request body is invalid (blank path, non-positive ttl/maxSize, empty methods) THEN system SHALL return 400 and SHALL NOT persist.

**Independent Test**: POST a rule, GET it back, PUT changes, DELETE it — assert status codes and repo state.

---

### P1: Mutations apply immediately and purge stale cache ⭐ MVP

**User Story**: As an operator, I want a created/updated rule to be live immediately and stale cached responses gone.

**Acceptance Criteria**:

1. WHEN a rule is created THEN `RuleRegistry.match` SHALL resolve a matching GET request to it without a restart.
2. WHEN a rule is updated or deleted THEN its Caffeine cache SHALL be evicted and the registry reloaded.

**Independent Test**: POST a rule for `/api/**`, then assert `RuleRegistry.match("GET","/api/x")` is present.

---

### P1: Admin endpoints are not proxied ⭐ MVP

**Acceptance Criteria**:

1. WHEN a request targets `/admin/**` or `/actuator/**` THEN the gateway route SHALL NOT forward it upstream; it is handled locally.

---

## Requirement Traceability

| ID | Story | Status |
| --- | --- | --- |
| RULE-01 | create → 201 + persist | Pending |
| RULE-02 | list all | Pending |
| RULE-03 | get by id 200/404 | Pending |
| RULE-04 | update 200/404 + evict + reload | Pending |
| RULE-05 | delete 204/404 + evict + reload | Pending |
| RULE-06 | validation → 400 | Pending |
| RULE-07 | create live immediately (reload) | Pending |
| RULE-08 | admin/actuator not proxied | Pending |

**Status:** ✅ RULE-01..08 all Verified via `mvn verify` (RuleAdminControllerIT 6 ITs + gateway exclusion; 31 tests total, 2026-06-24).

---

## Execution Plan (Medium — tasks implicit)

1. `pom.xml`: add `spring-boot-starter-validation`. → build
2. `GatewayConfig`: exclude `/admin/**` + `/actuator/**` from proxy route (RULE-08). → IT (T7 still green)
3. `CacheRule`: add setters for mutable fields. → compile
4. `dto/CacheRuleRequest` (+validation) + `dto/CacheRuleResponse`. → unit/compile
5. `rules/RuleAdminService`: CRUD + reload registry + evict cache (RULE-01..05,07). → covered by controller IT
6. `web/RuleAdminController`: REST endpoints, 201/200/204/404/400 (RULE-01..06). → integration IT
7. `web/RuleAdminControllerIT`: full CRUD + validation + reload assertions. → full gate

## Success Criteria

- [ ] `mvn verify` green; CRUD IT covers create/list/get/update/delete/validation + registry reload.
- [ ] Admin + actuator reachable locally (not proxied).
