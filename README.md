# API Gateway with Usage Analytics

A multi-tenant API gateway: developers register an upstream API, issue hashed API keys,
enforce per-key token-bucket rate limits, and see usage analytics on a React dashboard.

**Stack:** Java 21, Spring Boot 3.3.5, MySQL 8 (hosted on Aiven, SSL), Flyway, Spring
Security + JWT (HS256, jjwt), Caffeine, React 19 + Vite + React Router 7 (library mode),
Recharts, plain CSS. **No Lombok. No Docker. No Testcontainers.**

> One git commit per phase (0–9). `git log --oneline` shows what each phase added.
> The full specification is in `docs/SPEC.md`; design decisions are in `docs/DECISIONS.md`.

---

## Architecture

```
 Client (browser / curl)
   │  X-API-Key: gk_...
   ▼
┌──────────────────────────── Spring Boot (port 8080) ────────────────────────────┐
│  /gw/{slug}/**  ──▶  GatewayController                                          │
│     1. requestId + timer                                                         │
│     2. read X-API-Key                                                            │
│     3. SHA-256 hash  ─▶ KeyLookupService (Caffeine cache, 60s) ─▶ MySQL          │
│     4. key.apiId == slug's API?  (403 KEY_NOT_FOR_API if not)                    │
│     5. API enabled?  (503 API_DISABLED)                                          │
│     6. RateLimiterRegistry.tryConsume(keyId)  ──▶ TokenBucket (per key, in-mem)  │
│        ├─ allowed  → X-RateLimit-Limit/Remaining                                 │
│        └─ denied   → 429 + Retry-After                                           │
│     7. read body (≤ 1 MB)  (413 BODY_TOO_LARGE)                                  │
│     8. Forwarder ─▶ RestClient → upstream (per-API timeout, no redirect)         │
│        ├─ connection refused → 502 UPSTREAM_ERROR                                │
│        └─ read timeout        → 504 UPSTREAM_TIMEOUT                             │
│     9. copy upstream status/headers/body + X-Request-Id                          │
│    10. UsageRecorder.record(event)  ──▶ AsyncUsageRecorder                       │
│           ArrayBlockingQueue(10k) ─▶ background thread ─▶ JdbcTemplate.batchUpdate│
│           + api_keys.last_used_at; full-queue drops counted on /actuator/health  │
│                                                                                  │
│  /api/** (management, JWT-protected)                                             │
│     auth (register/login/me, BCrypt, login throttle 5/min/IP)                    │
│     apis CRUD (+ slug + SSRF validator, owner-scoped → 404 on IDOR)              │
│     keys (generate gk_ + SHA-256, show-once, revoke, per-API cap 20)             │
│     analytics (summary, timeseries w/ gap fill, top-paths, per-key, recent)      │
└────────────────────────────────────────────┬────────────────────────────────────┘
                                             │ JdbcTemplate / JPA
                                             ▼
                                   MySQL 8 (Aiven, SSL)
                                   tables: users, apis, api_keys, usage_logs
```

**Package layout** (`com.example.gateway`): `auth`, `apis`, `keys`, `gateway`,
`ratelimit`, `usage`, `analytics`, `common`. Package-by-feature; entities never leave
the feature; controllers return record DTOs; constructor injection throughout.

---

## Prerequisites (Windows)

- **Java 21** — `java -version`. If Maven can't find it, set `$env:JAVA_HOME` to your JDK 21.
- **MySQL 8** — this project targets a hosted **Aiven MySQL** that requires SSL.
- **Node 20+** (for the frontend only) — `node -v`.
- **Internet** — the Maven Wrapper downloads Maven 3.9.9 on first run; `npm install`
  downloads the frontend deps.

## Aiven MySQL setup (one-time)

1. Create a MySQL 8 service in the Aiven console and a database (e.g. `api_gateway`).
   Find host, port, database, user, password under **"Connection information"**.
2. The JDBC URL format (note `sslMode=REQUIRED`):
   ```
   jdbc:mysql://<host>:<port>/<database>?sslMode=REQUIRED&serverTimezone=UTC
   ```

## Run the backend

In a PowerShell window (these `$env:` vars live only in this session — run `mvnw.cmd`
from the same window):

```powershell
$env:DB_URL="jdbc:mysql://<host>:<port>/<database>?sslMode=REQUIRED&serverTimezone=UTC"
$env:DB_USERNAME="avnadmin"
$env:DB_PASSWORD="<your Aiven password>"
$env:JWT_SECRET=[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))

cd backend
.\mvnw.cmd clean spring-boot:run
```

You should see Flyway migrate `V1__init`, `Tomcat started on port 8080`, and
`Started GatewayApplication`. Health check (separate window):

```powershell
curl.exe http://localhost:8080/actuator/health
# {"status":"UP",...,"components":{"usageWriter":{"details":{"droppedUsageEvents":0,...}}}}
```

## Run the frontend

```powershell
cd frontend
npm install
npm run dev      # serves http://localhost:5173
```

Open the app (Vite dev server) in your browser and register a user.

> If the backend is not on `http://localhost:8080`, set `$env:VITE_API_BASE` before
> `npm run dev`.

---

## Demo flow (the 10-minute proof)

1. Register and log in (browser at `http://localhost:5173`).
2. Create an API with slug `demo` and upstream URL `https://httpbin.org`.
3. Create a key with capacity 5 and refill 1 per second. The modal shows the full key once —
   copy it.
4. Call the gateway with the key:
   ```powershell
   curl.exe -H "X-API-Key: <your-key>" http://localhost:8080/gw/demo/get
   ```
5. Run that call 10 times quickly. The first 5 succeed, then you get `429` with a
   `Retry-After` header.
6. Open the dashboard → the `demo` API → Overview tab: the calls and the rate-limited
   count appear on the chart and summary cards.

---

## Security checklist (spec)

| # | Requirement | How it's met |
|---|---|---|
| 1 | Passwords | BCrypt strength 10; min 8 chars + a letter + a digit (`PasswordPolicy`); hash never returned/logged |
| 2 | JWT | HS256 via jjwt; `JWT_SECRET` ≥ 32 bytes or the app refuses to start (`JwtProperties` compact ctor); 60-min TTL; no refresh |
| 3 | API keys | Stored as SHA-256 hex only; shown once (`CreateKeyResponse.fullKey`); logs/errors carry at most the prefix |
| 4 | IDOR | Every apis/keys/analytics query filters by `currentUserId()`; another user's resource → `404 NOT_FOUND` |
| 5 | SSRF | `SsrfValidator` on save and on each forward: http/https only; rejects loopback/private/link-local/unspecified (incl. a host resolving to 127.0.0.1); `gateway.allow-private-upstreams` toggles for dev |
| 6 | Brute force | `LoginThrottle` = `TokenBucket` 5/min per IP → `429` + `Retry-After` |
| 7 | Info leaks | Missing/unknown/revoked/expired keys all return the same `401 INVALID_API_KEY`; login failure says only "Invalid email or password." |
| 8 | Validation | Bean Validation on every body; slug regex + reserved words; parameterized JDBC only |
| 9 | Resource limits | Body ≤ 1 MB; ≤ 10 APIs/user; ≤ 20 active keys/API; analytics range ≤ 90 days |
| 10 | CORS/headers | CORS only on `/api/**` (not `/gw`); `X-Content-Type-Options: nosniff` + `Cache-Control: no-store` on management responses |
| 11 | CSRF | Disabled (stateless + Bearer header, not cookies) — see `SecurityConfig` |
| 12 | Secrets | No secrets in the repo; `.env.example` lists variable names only |

---

## Testing

Pure unit tests + MockMvc with mocks. **No Docker, no Testcontainers** (per the user's
environment). Run:

```powershell
cd backend
.\mvnw.cmd test
```

105 tests cover: `TokenBucket` (incl. a 100-thread concurrency test → exactly 50 allowed),
`RateLimiterRegistry` (self-heal on limit change, idle cleanup), `KeyLookupService`
(cache hit/miss/no-cache-miss/evict), `SlugValidator`, `SsrfValidator` (every blocked range),
`KeyGenerator`/`KeyHasher`, `PasswordPolicy`, `JwtService`/`JwtProperties` (fail-fast),
`AuthController`, `ApiController`, `KeyController`, `GatewayController` (success/401/403/
503/413/429/502/504, upstream 4xx passthrough, X-API-Key not forwarded), `AnalyticsService`
(gap fill, range too wide), `AsyncUsageRecorder` (500 events → 500 rows, queue-full drops,
batch chunking).

Frontend: `cd frontend; npm run build` (TypeScript strict + Vite compile).

---

## Load test

One script sends ~200 rps for 60s through a key limited to 50/s and reports the 200/429
share, the gateway p95 latency overhead vs calling the upstream directly, and that no
usage events were dropped. Stdlib Python only.

```powershell
# backend must be running with DB env vars set, and https://httpbin.org reachable
python loadtest\loadtest.py --base http://localhost:8080 --rps 200 --duration 60
```

**Fill these in after you run it** (the script prints a ready-to-paste summary):

- Sustained **[X]** rps for 60s; **[Y]%** of responses were 200, the rest 429 (key limited
  to 50/s).
- Gateway p95 latency overhead vs direct upstream call: **[Z]** ms.
- Dropped usage events: **0** (queue never overflowed).

---

## Single-instance limit (and what changes for multiple)

Token-bucket state and the key-lookup cache live **in one JVM**. Run two gateway instances
and a key's rate limit is enforced per-instance (double the effective limit), and a revoke
on instance A is not reflected in instance B's cache until its 60s TTL expires.

**What changes for multiple instances** (stretch goal #1):
- Move token buckets to **Redis** with an atomic Lua script (`EVAL` of a Lua decrement +
  refill), so all instances share one bucket per key.
- Move the key-lookup cache to Redis (or accept the 60s TTL staleness, which is already
  safe because revoke also evicts).
- The async usage writer already survives multiple instances (each drains its own queue).

---

## Project layout

```
backend/   Spring Boot app (com.example.gateway.*)
  src/main/java/.../{auth,apis,keys,gateway,ratelimit,usage,analytics,common}
  src/main/resources/{application.yml, db/migration/V1__init.sql}
  src/test/java/...
  pom.xml  mvnw.cmd  mvnw
frontend/  React 19 + Vite (src/{pages,components,api,auth,hooks,utils}, styles.css)
loadtest/  loadtest.py (stdlib Python)
docs/SPEC.md  docs/DECISIONS.md
.env.example  README.md
```

## `.env.example`

Lists the variable names only (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`,
`CORS_ALLOWED_ORIGIN`, `GATEWAY_ALLOW_PRIVATE_UPSTREAMS`). Spring Boot does **not** read
`.env`; set them as PowerShell `$env:` variables in the window you run `mvnw.cmd` from.
