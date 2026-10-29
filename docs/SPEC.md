# API Gateway with Usage Analytics: Project Specification

Date: 2026-10-28

A multi-tenant API gateway where developers register an upstream API, issue hashed API keys, enforce per-key token-bucket rate limits, and see usage analytics on a React dashboard. Stack: Java 21, Spring Boot 3, MySQL 8, React 19.

## Minimum version and demo flow

Build the core first. A project with only the core rows below already shows the main idea. Add the later rows once the core tests pass.

| Item | When |
| --- | --- |
| Accounts and JWT, API registration, key create and revoke, gateway forwarding, token bucket with headers, usage logging, summary and time series analytics, basic dashboard | Core |
| SSRF validator | Core. You may build it after Phase 4, but it must be done before the gateway is reachable from the internet |
| Usage logging as a plain database save on each request | Core, first version. Replace it with the async batch writer later |
| Key lookup straight from MySQL on each request | Core, first version. Add the Caffeine cache later and keep the revoke test |
| Login throttling | Add later |
| Top paths, per-key table, recent requests tab | Add later |

**Demo flow.** If you have 10 minutes, this is the proof that it works:

1. Register and log in.
2. Create an API with slug `demo` and upstream URL `https://httpbin.org`.
3. Create a key with capacity 5 and refill 1 per second.
4. Call the gateway with the key:

```
curl -H "X-API-Key: <your key>" http://localhost:8080/gw/demo/get
```

5. Run that call 10 times quickly. The first 5 succeed, then you get `429`.
6. Open the dashboard and see the calls and the rate limited count.

## Goal and scope

Version 1 is a working gateway plus dashboard that one developer can finish and demo. Anything not listed under "In scope" is out.

**In scope**

- Accounts: register, login, JWT-protected management API.
- Register an upstream API (name, slug, base URL, timeout).
- Create, list, update limits of, and revoke API keys. The key is shown once.
- Gateway endpoint `/gw/{slug}/**` that checks the key, rate limits it, forwards the request, and returns the upstream response.
- Per-key token-bucket rate limiting with `429` and rate limit headers.
- Usage logging of every gateway call.
- Dashboard: summary cards, requests over time, top paths, per-key usage, recent requests.

**Out of scope for version 1**

- Billing, plans, payments, teams or organizations.
- Running more than one gateway instance (rate limit state is in memory).
- WebSockets, streaming responses, request or response transformation.
- Email verification and password reset.

## Request flow

A client calls `https://<gateway>/gw/{slug}/{path}?{query}` with the header `X-API-Key: gk_...`. The gateway runs these steps in order and stops at the first failure.

1. Generate a request ID (UUID) and start a timer.
2. Read `X-API-Key`. If missing, return `401 MISSING_API_KEY`.
3. Hash the key with SHA-256 and look it up (cache first, then MySQL). If not found, revoked, or expired, return `401 INVALID_API_KEY`. Use the same message for all three cases.
4. If the key belongs to a different API than `{slug}`, return `403 KEY_NOT_FOR_API`.
5. If the API is disabled, return `503 API_DISABLED`.
6. Ask the key's token bucket for one token. If empty, return `429 RATE_LIMITED` with a `Retry-After` header.
7. Forward the request to the upstream URL (rules in Core logic).
8. Copy the upstream status, headers and body back to the client, and add `X-RateLimit-Limit`, `X-RateLimit-Remaining` and `X-Request-Id`.
9. In all cases, queue one usage log row (outcome, status, latency).

**Gateway error responses**

Every error uses the body `{"error": {"code": "...", "message": "...", "requestId": "..."}}`.

| Situation | HTTP status | Code | Usage outcome |
| --- | --- | --- | --- |
| No key header | 401 | MISSING_API_KEY | UNAUTHORIZED |
| Bad, revoked or expired key | 401 | INVALID_API_KEY | UNAUTHORIZED |
| Key belongs to another API | 403 | KEY_NOT_FOR_API | UNAUTHORIZED |
| API disabled | 503 | API_DISABLED | REJECTED |
| Bucket empty | 429 | RATE_LIMITED | RATE_LIMITED |
| Request body over 1 MB | 413 | BODY_TOO_LARGE | REJECTED |
| Upstream refused or failed to connect | 502 | UPSTREAM_ERROR | UPSTREAM_ERROR |
| Upstream exceeded the API's timeout | 504 | UPSTREAM_TIMEOUT | UPSTREAM_TIMEOUT |
| Upstream answered | upstream's status | none | SUCCESS (any upstream status, even 4xx or 5xx) |

## Stack and repo layout

| Layer | Choice | Why |
| --- | --- | --- |
| Language | Java 21 | Matches your resume |
| Backend | Spring Boot 3.x, Spring Web (MVC, not WebFlux) | Simplest to reason about and test |
| Security | Spring Security, JWT (HS256), BCrypt | Matches your other projects |
| Data | Spring Data JPA for management data, `JdbcTemplate` for batch usage inserts | Batch inserts are much faster with JDBC |
| Database | MySQL 8, migrations with Flyway | Schema is versioned in the repo |
| HTTP forwarding | Spring `RestClient` (Spring 6.1+) | Built in, supports timeouts |
| Cache | Caffeine | In-memory key lookup cache |
| Rate limiter | Your own `TokenBucket` class | Do not use a library. This is the interview talking point |
| Tests | JUnit 5, Mockito, MockMvc, WireMock, Testcontainers (MySQL) | Covers unit and integration |
| Frontend | React 19, Vite, React Router 7, Recharts, plain CSS | Matches RenewalDesk's setup |
| Local infra | Docker Compose: MySQL 8 and an echo service (`mccutchen/go-httpbin`) as a demo upstream | The demo works with no outside accounts |

**Repository layout**

```
api-gateway/
  backend/
    src/main/java/com/<you>/gateway/
      auth/        register, login, JWT filter, login throttle
      apis/        upstream API entity, service, controller, SSRF validator
      keys/        API key entity, generator, hasher, service, controller
      gateway/     /gw controller, forwarder, error mapper
      ratelimit/   TokenBucket, RateLimiterRegistry
      usage/       UsageEvent, async queue, batch writer
      analytics/   queries and controller
      common/      error format, exceptions, config properties
    src/main/resources/db/migration/V1__init.sql
    src/main/resources/application.yml
    src/test/java/...
  frontend/
    src/pages, src/components, src/api, src/styles
  docs/SPEC.md
  docker-compose.yml
  README.md
```

Organize code by feature (package per folder above), not by layer. Keep every file under 500 lines.

## Data model

Four tables. All timestamps are `DATETIME(3)` in UTC. All primary keys are `BIGINT AUTO_INCREMENT`.

**users**

| Column | Type | Rules |
| --- | --- | --- |
| id | BIGINT | Primary key |
| email | VARCHAR(255) | Unique, not null, stored lowercase |
| password_hash | VARCHAR(100) | BCrypt, not null |
| display_name | VARCHAR(100) | Not null |
| role | ENUM('USER','ADMIN') | Default USER |
| created_at | DATETIME(3) | Not null |

**apis**

| Column | Type | Rules |
| --- | --- | --- |
| id | BIGINT | Primary key |
| owner_id | BIGINT | Foreign key to users, not null |
| name | VARCHAR(100) | Not null |
| slug | VARCHAR(50) | Unique, not null, used in `/gw/{slug}` |
| upstream_base_url | VARCHAR(500) | Not null, http or https only |
| timeout_ms | INT | Default 10000, allowed 500 to 30000 |
| enabled | BOOLEAN | Default true |
| created_at | DATETIME(3) | Not null |

Index: `(owner_id)`.

**api_keys**

| Column | Type | Rules |
| --- | --- | --- |
| id | BIGINT | Primary key |
| api_id | BIGINT | Foreign key to apis, `ON DELETE CASCADE` |
| name | VARCHAR(100) | Not null, label like "mobile app" |
| key_prefix | VARCHAR(16) | First characters of the key, shown in the UI |
| key_hash | CHAR(64) | SHA-256 hex of the full key, unique, not null |
| capacity | INT | Bucket size (burst), default 10, allowed 1 to 1000 |
| refill_per_sec | DECIMAL(8,2) | Tokens added per second, default 5.00, allowed 0.10 to 1000 |
| status | ENUM('ACTIVE','REVOKED') | Default ACTIVE |
| expires_at | DATETIME(3) | Nullable |
| last_used_at | DATETIME(3) | Nullable, updated by the usage batch writer |
| created_at | DATETIME(3) | Not null |
| revoked_at | DATETIME(3) | Nullable |

Indexes: unique `(key_hash)`, `(api_id)`.

**usage_logs**

| Column | Type | Rules |
| --- | --- | --- |
| id | BIGINT | Primary key |
| api_id | BIGINT | Foreign key to apis, `ON DELETE CASCADE` |
| api_key_id | BIGINT | Nullable (a missing or bad key has no key row) |
| request_id | CHAR(36) | UUID |
| method | VARCHAR(10) | Not null |
| path | VARCHAR(500) | Path after the slug, query string removed, cut at 500 characters |
| status_code | SMALLINT | Status returned to the client |
| latency_ms | INT | Total time inside the gateway |
| outcome | ENUM('SUCCESS','RATE_LIMITED','UNAUTHORIZED','UPSTREAM_ERROR','UPSTREAM_TIMEOUT','REJECTED') | Not null |
| created_at | DATETIME(3) | Not null |

Indexes: `(api_id, created_at)`, `(api_key_id, created_at)`.

When the slug is unknown there is no `api_id`, so those requests are not logged. Only requests for an existing API are logged.

## REST API

The management API lives under `/api` and needs the header `Authorization: Bearer <jwt>`, except register and login. Request and response bodies are JSON. Validation errors return `400` with the same error body as the gateway, plus a `fields` map. A resource owned by another user returns `404`, never `403`.

**Auth**

| Method and path | Body | Success response |
| --- | --- | --- |
| POST `/api/auth/register` | email, password, displayName | 201 with token and user |
| POST `/api/auth/login` | email, password | 200 with token and user |
| GET `/api/auth/me` | none | 200 with user |

**APIs**

| Method and path | Body | Success response |
| --- | --- | --- |
| POST `/api/apis` | name, slug, upstreamBaseUrl, timeoutMs (optional) | 201 with the API |
| GET `/api/apis` | none | 200 with the caller's APIs |
| GET `/api/apis/{id}` | none | 200 with the API |
| PATCH `/api/apis/{id}` | any of name, upstreamBaseUrl, timeoutMs, enabled | 200 with the API |
| DELETE `/api/apis/{id}` | none | 204, also deletes its keys and usage logs |

**Keys**

| Method and path | Body | Success response |
| --- | --- | --- |
| POST `/api/apis/{apiId}/keys` | name, capacity (optional), refillPerSec (optional), expiresAt (optional) | 201 with the key record and the full `key` string. This is the only time it is returned |
| GET `/api/apis/{apiId}/keys` | none | 200 with keys (prefix, limits, status, lastUsedAt, never the full key) |
| PATCH `/api/keys/{keyId}` | any of name, capacity, refillPerSec | 200 with the key record |
| POST `/api/keys/{keyId}/revoke` | none | 204 |

**Analytics.** All take `apiId` and `from` and `to` (ISO 8601 UTC, default last 24 hours, maximum range 90 days).

| Method and path | Extra parameters | Response |
| --- | --- | --- |
| GET `/api/analytics/summary` | none | totalRequests, successCount, rateLimitedCount, unauthorizedCount, errorCount, successRate, avgLatencyMs |
| GET `/api/analytics/timeseries` | `bucket` = `hour` or `day` | List of bucketStart, total, success, rateLimited, errors |
| GET `/api/analytics/top-paths` | `limit` (default 10, max 50) | List of path, method, count, avgLatencyMs |
| GET `/api/analytics/keys` | none | List of keyId, name, prefix, total, rateLimited, lastUsedAt |
| GET `/api/analytics/recent` | `limit` (default 50, max 200) | List of the newest log rows |

**Gateway**

| Method and path | Notes |
| --- | --- |
| ANY `/gw/{slug}/**` | Methods GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS. Needs `X-API-Key`. Not protected by JWT |

## Core logic

**1. API key generation and storage**

- Format: `gk_` + 32 random bytes from `SecureRandom`, encoded base64url without padding (43 characters). Total length 46.
- Store `key_prefix` = the first 11 characters (`gk_` plus 8), for display like `gk_ab12cd34...`.
- Store `key_hash` = SHA-256 of the full key as lowercase hex. SHA-256 is correct here because the key is 256 bits of randomness and every gateway request must hash it. BCrypt would be far too slow, and it is only needed for low-entropy passwords.
- Return the full key once, in the create response. The gk prefix stands for gateway key. It is never stored or logged.

**2. Token bucket**

One `TokenBucket` object per key, held in a `ConcurrentHashMap<Long, TokenBucket>` inside `RateLimiterRegistry`.

- Fields: `capacity`, `refillPerSec`, `tokens` (double, starts full), `lastRefillNanos`.
- `tryConsume()` is `synchronized` on the bucket. Steps: read `System.nanoTime()`, add `elapsedSeconds * refillPerSec` to `tokens`, cap at `capacity`, set `lastRefillNanos`. If `tokens >= 1`, subtract 1 and allow. Otherwise deny.
- On allow, return remaining = `floor(tokens)`. On deny, return `retryAfterSeconds = ceil((1 - tokens) / refillPerSec)`, minimum 1.
- Response headers: `X-RateLimit-Limit` = capacity, `X-RateLimit-Remaining` = remaining. On `429` add `Retry-After`.
- When a key's limits are changed through PATCH, remove its bucket so the next request creates a new one with the new limits.
- A scheduled job runs every 10 minutes and removes buckets unused for 1 hour.
- Known limit, state it in the README: buckets live in one JVM, so this works for a single instance only. The stretch goal moves them to Redis.
- The same `TokenBucket` class also throttles login attempts: 5 attempts per minute per client IP.

**3. Key lookup cache**

- Caffeine cache keyed by `key_hash`. Value: key id, api id, status, expiry, limits, slug, upstream URL, timeout, API enabled flag.
- Maximum 10,000 entries, expire 60 seconds after write.
- Evict the entry immediately on revoke, limit change, API update, and API delete. Because of this, a revoked key stops working on the very next request.
- Do not cache misses.

**4. Forwarding rules**

- Target URL = `upstream_base_url` without a trailing slash + the path after `/gw/{slug}` + the original query string.
- Remove these request headers before forwarding: `X-API-Key`, `Host`, `Connection`, `Keep-Alive`, `Proxy-Authenticate`, `Proxy-Authorization`, `TE`, `Trailer`, `Transfer-Encoding`, `Upgrade`.
- Add `X-Request-Id` and `X-Forwarded-For` (client IP).
- Read the request body as bytes, maximum 1 MB. Over that, return `413`.
- Use the API's `timeout_ms` for connect and read timeouts. Do not follow redirects, return them to the client as they are.
- Return the upstream status and body. Copy upstream response headers except the hop-by-hop list above and `Content-Length` (let the server recompute it).

**5. Async usage logging**

- The request thread creates a `UsageEvent` and calls `queue.offer(event)` on a bounded `ArrayBlockingQueue` (capacity 10,000). It never waits for the database.
- If the queue is full, drop the event and increment a `droppedUsageEvents` counter exposed on the actuator health endpoint.
- One background thread drains the queue every 1 second or when 100 events are waiting, whichever comes first, and writes them with one `JdbcTemplate.batchUpdate`. In the same pass it updates `last_used_at` for the keys seen.
- On application shutdown, flush the remaining events before exit.

**6. Analytics queries**

- All filtered by `api_id` and a `created_at` range, so they use the `(api_id, created_at)` index.
- Time series: group by `DATE_FORMAT(created_at, ...)` for the chosen bucket and fill empty buckets with zeros in Java, so the chart has no gaps.
- Success rate = SUCCESS outcomes divided by total, as a percentage with one decimal. unauthorizedCount counts UNAUTHORIZED. errorCount counts UPSTREAM_ERROR, UPSTREAM_TIMEOUT and REJECTED. In the time series, errors uses the same definition.

## Security requirements

Every item below needs a test or a clear code path. This is the checklist for review before you call the project done.

| # | Requirement | How |
| --- | --- | --- |
| 1 | Passwords | BCrypt, strength 10 or higher. Minimum 8 characters with at least one letter and one digit. Never return or log the hash |
| 2 | JWT | HS256, secret from the environment variable `JWT_SECRET` (at least 32 bytes), expiry 60 minutes, no refresh token in version 1. App refuses to start if the secret is missing or short |
| 3 | API keys | Stored only as SHA-256 hash. Shown once. Logs and error messages contain at most the prefix |
| 4 | IDOR | Every query for APIs, keys and analytics filters by the logged-in user's id. A resource owned by someone else returns `404` |
| 5 | SSRF | When an upstream URL is saved, and again before each forward, accept only `http` or `https` and reject any host that resolves to a loopback, private, link-local or unspecified address (`127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `::1`, `fc00::/7`, `0.0.0.0`). Config flag `gateway.allow-private-upstreams` turns this off for local development and Docker Compose only |
| 6 | Brute force | Login throttled to 5 attempts per minute per IP using `TokenBucket`. Return `429` |
| 7 | Information leaks | Missing, unknown, revoked and expired keys all return the same `401 INVALID_API_KEY` text. Login failure says only "invalid email or password" |
| 8 | Input validation | Bean Validation on every request body. Slug must match `^[a-z0-9]([a-z0-9-]{1,48}[a-z0-9])$` and must not be `api`, `admin`, `gw`, `health` or `actuator`. All database access through JPA or parameterized JDBC, no string-built SQL |
| 9 | Resource limits | Request body maximum 1 MB. Maximum 10 APIs per user and 20 active keys per API. Analytics range maximum 90 days |
| 10 | CORS and headers | CORS allows only the configured frontend origin and only on `/api`. Not on `/gw`. Send `X-Content-Type-Options: nosniff` and `Cache-Control: no-store` on management responses |
| 11 | CSRF | Disabled, because the API is stateless and uses a Bearer header, not cookies. Note this in the README |
| 12 | Secrets | Nothing secret in the repository. `.env.example` lists the variable names only |

## Frontend

React 19 with Vite and React Router 7 in library mode (BrowserRouter or createBrowserRouter, not framework mode). Charts with Recharts. Plain CSS with variables, no UI kit. Pick one font pair and one accent color and keep them everywhere. Avoid a purple-blue gradient look. The JWT is kept in memory and in `localStorage`, and sent in the `Authorization` header by one `api` helper that also redirects to login on `401`.

| Route | Page | Contents |
| --- | --- | --- |
| `/login` and `/register` | Auth forms | Field validation messages from the `fields` map, error banner |
| `/apis` | API list | Cards or table with name, slug, status, 24-hour request count. Button "New API" opens a form (name, slug, upstream URL, timeout) |
| `/apis/:id` | API detail with four tabs | Overview, Keys, Requests, Settings (below) |

**Tabs on the API detail page**

| Tab | What it shows |
| --- | --- |
| Overview | Date range picker (1 hour, 24 hours, 7 days, 30 days, default 24 hours). Five summary cards: total requests, success rate, rate limited, errors, average latency. A stacked bar chart of requests over time split into success, rate limited and error. A top paths table |
| Keys | Table of keys (name, prefix, limits, status, last used, requests). "Create key" form with name, capacity, refill per second, optional expiry. After creation, a modal shows the full key once with a copy button and the warning "You cannot see this key again". Buttons to edit limits and revoke (with confirm) |
| Requests | Table of the 50 newest requests (time, method, path, status, outcome, latency, key name). Outcome shown as a text label with a color, never color alone. Refresh button |
| Settings | Edit name, upstream URL, timeout. Enable or disable toggle. Delete API with a typed-name confirmation. A copy-ready `curl` example using the gateway URL and a placeholder key |

Every list and chart needs three states: loading, empty (with a clear next step such as "Create your first key"), and error with a retry button. Layout must work at phone width.

## Configuration and conventions

**Settings in `application.yml`.** Every value can be overridden by an environment variable.

| Setting | Default | Meaning |
| --- | --- | --- |
| `spring.datasource.url`, `username`, `password` | from env | MySQL connection |
| `jwt.secret` | from env `JWT_SECRET`, no default | At least 32 bytes |
| `jwt.ttl-minutes` | 60 | Token lifetime |
| `cors.allowed-origin` | `http://localhost:5173` | Frontend origin |
| `gateway.max-body-bytes` | 1048576 | Request body limit |
| `gateway.default-timeout-ms` | 10000 | Used when an API sets none |
| `gateway.cache-ttl-seconds` | 60 | Key cache expiry |
| `gateway.cache-max-entries` | 10000 | Key cache size |
| `gateway.usage-queue-capacity` | 10000 | Usage event queue size |
| `gateway.usage-flush-interval-ms` | 1000 | Batch writer interval |
| `gateway.usage-batch-size` | 100 | Rows per batch |
| `gateway.allow-private-upstreams` | false | Turn on only for local development |

**Conventions for all code**

- Package by feature, constructor injection, no field injection.
- Request and response bodies are Java `record` DTOs. Entities are never returned from controllers.
- One `@RestControllerAdvice` maps exceptions to the error body shown in Request flow.
- Times are `Instant` in UTC everywhere. The frontend converts to local time for display.
- Flyway owns the schema. Hibernate `ddl-auto` is set to `validate`.
- Do not merge features, endpoints, columns or libraries that this document does not list. To try something new, use a separate branch. If something is missing, record it as an open question in `docs/DECISIONS.md` instead of inventing it.
- Every module ends with its tests passing and one git commit.

## Testing plan

Write tests with each module, not at the end. Each feature needs a happy path, an edge case and a failure case.

**Unit tests**

| Subject | Cases |
| --- | --- |
| `TokenBucket` | Starts full. Allows exactly `capacity` calls in a burst, then denies. Refills over time (use an injectable clock or time source, not `Thread.sleep`). Never exceeds capacity. `retryAfter` is correct. 100 threads on one bucket with capacity 50 allow exactly 50 |
| Key generator and hasher | Correct format and length. Two keys differ. Hash is 64 hex characters and stable |
| SSRF validator | Rejects every address range in the Security table, including a hostname that resolves to `127.0.0.1`. Accepts a normal public host. Rejects `ftp://` |
| Slug validator | Accepts valid slugs. Rejects uppercase, spaces, leading or trailing hyphen, reserved words |
| Analytics gap filler | A bucket with no rows becomes zeros |

**Integration tests.** Use MockMvc with Testcontainers MySQL and WireMock as the fake upstream.

| Scenario | Expected result |
| --- | --- |
| Valid key, GET through the gateway | Upstream response returned, `X-RateLimit-*` headers present, one usage row written |
| No key, bad key, revoked key, expired key | 401 with the right code |
| Revoke a key, call again immediately | 401 on the very next call (cache eviction works) |
| Key for API A used on API B | 403 |
| Send more requests than capacity | 429 with `Retry-After`, then success after waiting |
| Upstream down | 502. Upstream slower than timeout: 504 |
| Body over 1 MB | 413 |
| `X-API-Key` header | Not present in the request WireMock received |
| User B reads user A's API, keys or analytics | 404 every time |
| Register with a short password or duplicate email | 400 or 409 |
| Six logins in one minute from one IP | Sixth returns 429 |
| Usage writer | 500 events queued produce 500 rows, and shutdown flushes the remainder |

**Load test.** One script (k6 or a simple Java or Python script) sends about 200 requests per second for 60 seconds through a key limited to 50 per second. Record: share of `200` versus `429`, gateway p95 latency overhead compared with calling the upstream directly, and that no usage events were dropped. Put these numbers in the README. You will use them on your resume.

## Build order

Build in this order. Each phase has a check you can run before moving on. Size: S is about half a day, M about a day or two, L several days.

| Phase | Build | Size | Done when |
| --- | --- | --- | --- |
| 0 | Repo, `docker-compose.yml` (MySQL and echo upstream), Flyway `V1__init.sql` with all four tables, health endpoint | S | `docker compose up`, app starts, tables exist, `/actuator/health` returns UP |
| 1 | Accounts: register, login, `me`, BCrypt, JWT filter, login throttle, error format | M | Auth tests pass. Calling `/api/apis` without a token returns 401 |
| 2 | APIs: create, list, get, update, delete, slug rules, SSRF validator, owner scoping | M | CRUD tests pass. User B gets 404 on user A's API. Private URL rejected |
| 3 | API keys: generate, hash, create (shown once), list, update limits, revoke, per-API key cap | M | Full key never appears in a later response. Hash test passes |
| 4 | Gateway core: key check, slug check, forwarding, header rules, error mapping. No rate limit yet | L | WireMock tests pass for success, 401, 403, 502, 504, 413. Key header not forwarded |
| 5 | Rate limiting: `TokenBucket`, registry, headers, key cache with eviction, idle cleanup | M | Concurrency test passes. A burst past capacity returns 429 with `Retry-After`. Revoke takes effect on the next call |
| 6 | Usage logging: queue, batch writer, `last_used_at`, shutdown flush | M | 500 events produce 500 rows. Gateway latency does not change when the database is slow (test with a delayed writer) |
| 7 | Analytics endpoints and gap filling | M | Each endpoint returns correct numbers on a seeded dataset |
| 8 | React dashboard: auth, API list, detail tabs, key modal, charts | L | You can run the whole flow from the browser: register, add API, create key, call the gateway with `curl`, see the call on the dashboard |
| 9 | Polish: README with architecture diagram, screenshots, load test numbers, `.env.example`, deploy | M | A stranger can clone, run `docker compose up`, and follow the README demo in 10 minutes |

**Definition of done for the whole project**

- All security requirements in the table above are met and each has a test or a documented code path.
- Load test numbers are in the README.
- The README states the single-instance limit and what would change for multiple instances.
- A demo is deployed or recorded: a 2-minute video showing the flow in phase 8.

## Stretch goals and resume bullets

**Stretch goals.** Only after phase 9 is done, and one at a time.

1. Move token buckets to Redis with an atomic Lua script, so several gateway instances share limits.
2. Add a `usage_hourly` roll-up table filled by a scheduled job, so analytics stay fast with millions of rows.
3. Add p95 latency to the summary.
4. Add an admin role page: list users, disable a user.
5. Add alerts: email the API owner when a key is rate limited more than 100 times in an hour.
6. Add a per-key allowed-IP list.

**Draft resume bullets.** Replace the brackets with your measured numbers from the load test.

- Built an API gateway (Java 21, Spring Boot, MySQL, React) that issues hashed API keys, enforces per-key token-bucket rate limits, and proxies requests to registered upstream APIs.
- Implemented async batched usage logging and an analytics dashboard (requests over time, error rate, top endpoints); sustained [X] requests per second in load tests with [Y] ms added p95 latency.
- Secured the platform against SSRF, IDOR and key leakage with SHA-256 key hashing, show-once keys, owner-scoped queries, JWT and BCrypt, and covered it with [N] unit and integration tests.
