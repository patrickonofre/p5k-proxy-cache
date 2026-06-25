# p5k-proxy-cache

Generic HTTP caching reverse proxy. Fronts **N backends**: register each backend as an
*application*, then declare cache *rules* for it. An incoming request is namespaced by the
application **slug** (first path segment); the proxy resolves the slug to the backend, strips
the slug, matches the remainder against that application's rules, and serves cache HITs without
touching the origin.

```
GET /billing/users/1
  -> app "billing" (base_url https://billing.internal)
  -> strip slug -> /users/1
  -> match rule /users/** (app=billing) -> HIT served from cache (no upstream call)
```

`X-Cache: HIT | MISS | BYPASS` is set on every proxied response.

## Run

```bash
docker compose up -d     # postgres + app on :8080
```

## Usage

1. Register an application (the backend):

```bash
curl -X POST localhost:8080/admin/applications -H 'content-type: application/json' -d '{
  "slug": "billing",
  "name": "Billing API",
  "baseUrl": "https://billing.internal",
  "defaultTtlSeconds": 60,
  "enabled": true
}'
```

2. Declare a cache rule for it (path is backend-relative; `ttlSeconds` optional — inherits the
   app's `defaultTtlSeconds`):

```bash
curl -X POST localhost:8080/admin/rules -H 'content-type: application/json' -d '{
  "applicationId": 1,
  "pathPattern": "/users/**",
  "methods": ["GET"],
  "ttlSeconds": 120,
  "maxSize": 1000,
  "enabled": true,
  "description": "user lookups"
}'
```

3. Hit the proxy through the slug:

```bash
curl localhost:8080/billing/users/1   # first MISS (forwarded + cached), then HIT
```

## Admin API

| Resource | Endpoints |
| --- | --- |
| Applications | `POST/GET /admin/applications`, `GET/PUT/DELETE /admin/applications/{id}` |
| Rules | `POST/GET /admin/rules`, `GET/PUT/DELETE /admin/rules/{id}` |

Deleting an application cascades to its rules. Mutations take effect immediately (no restart)
and evict stale cache entries. `/admin/**` and `/actuator/**` are handled locally, never proxied.

## Notes

- Caffeine in-memory cache, one per rule, sized/expired by effective TTL.
- Single node (v1). Distributed L2 (Redis), admin auth, and conditional requests are roadmapped.
- Backends on the host (e.g. the `cache-test-origin` app on `:8081`) are reachable via
  `http://host.docker.internal:8081` as an application `baseUrl`.
</content>
