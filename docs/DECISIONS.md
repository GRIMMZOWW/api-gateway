# Design decisions and open questions

Decisions made when the spec was silent or the environment forced a choice.
Each entry: the choice, the reason, and the impact.

## D0.1 — Cannot run or test the code in the author's build environment
The build sandbox has no Maven, no JDK compiler (only the JRE), and no MySQL. The
author can only produce source files (and a downloadable zip). All code is written
to compile and run on a standard Windows + Java 21 + Maven + hosted MySQL setup.
The user runs build and test commands on their laptop and pastes any errors back.
Impact: extra care is taken on imports, package names, dependency coordinates,
and SQL before each phase is committed. Expect compile errors to be fixed iteratively.

## D0.2 — Base package `com.example.gateway`
The spec uses `com/<you>/gateway/`. Replace `example` with your own name/organization
(global find-and-replace `com.example` -> `com.yourname` in `backend/`) before putting
this on your resume.

## D0.3 — Simplified Maven Wrapper scripts (not the canonical Apache ones)
`backend/mvnw.cmd` and `backend/mvnw` are short, self-contained scripts that download
Apache Maven 3.9.9 into `%USERPROFILE%\.m2\w\apache-maven-3.9.9` on first run and then
invoke it. This avoids shipping the ~150-line canonical wrapper batch/shell + the
binary `maven-wrapper.jar`. `.mvn/wrapper/maven-wrapper.properties` is still provided
so that `mvn wrapper:wrapper` can regenerate the canonical wrapper later if desired.
Impact: the wrapper is functionally equivalent for `mvnw.cmd <goals>`. If the download
ever fails, the fallback is `winget install Apache.Maven` (or any Maven 3.9.x) and use
`mvn` directly.

## D0.4 — Spring Boot 3.3.5
Spec says "Spring Boot 3.3.x". 3.3.5 is a stable GA release. Java 21 is the language level.

## D0.5 — Dependencies are added per phase, not all up front
Phase 0 POM includes only what Phase 0 needs: `web`, `actuator`, `data-jpa`,
`validation`, `flyway-core` + `flyway-mysql`, `mysql-connector-j`, `spring-boot-starter-test`.
`spring-boot-starter-security`, `io.jsonwebtoken` (jjwt), `caffeine`, `wiremock`,
`spring-boot-starter-cache` are added in the phase that first uses them. Reason: keeps
the Phase 0 done-check simple — `/actuator/health` is open (Spring Security would
otherwise secure it by default) and there are no unused libraries.

## D0.6 — `usage_logs.api_key_id` is nullable with NO foreign key
Spec only says "Nullable (a missing or bad key has no key row)". A FK with `ON DELETE SET
NULL` would add write overhead on the highest-volume table and is unnecessary because
`usage_logs.api_id` already cascades on API delete (which removes the key rows too).
Impact: bad-key requests still log with `api_key_id = NULL`. Top-keys analytics joins
`usage_logs.api_key_id` to `api_keys.id` and simply gets `NULL` for revoked-and-deleted
keys, which is fine.

## D0.7 — `users.email` stored lowercase, enforced in application code
Spec says "stored lowercase". Enforced by lowercasing before insert and lookup in the
auth service (Phase 1), not via a DB trigger or generated column. Reason: keeps all
business rules in Java, easier to test.

## D0.8 — Hibernate `Instant` <-> MySQL `DATETIME(3)`
Spec mandates `DATETIME(3)` for all timestamps. Hibernate 6 defaults `Instant` to a
6-digit scale and can fail `ddl-auto: validate` against a `DATETIME(3)` column. Fix
applied from Phase 1 onward: every `Instant` entity field is annotated
`@Column(columnDefinition = "DATETIME(3)")` so validation matches the Flyway schema.

## D0.9 — Hosted Aiven MySQL (no Docker)
The user runs a hosted Aiven MySQL that requires SSL. JDBC URL format:
`jdbc:mysql://<host>:<port>/<database>?sslMode=REQUIRED&serverTimezone=UTC`.
No Docker is used in this project. `docker-compose.yml` is NOT part of the repo.
The demo's echo upstream is the public `https://httpbin.org` (no local go-httpbin).

## D0.10 — Settings come from PowerShell `$env:` variables, not `.env` files
Spring Boot does not read `.env` files. The user sets `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGIN`, `GATEWAY_ALLOW_PRIVATE_UPSTREAMS`
as `$env:` variables in the same PowerShell window that runs `mvnw.cmd`.
`application.yml` has NO default DB password (fail-fast if unset). `.env.example`
remains in the repo only as a reference listing the variable names and formats.

## D0.11 — No Docker, no Testcontainers; tests are plain unit tests + MockMvc with mocks
Per the user's environment (no Docker, ~6 GB RAM), integration tests do NOT use
Testcontainers. Each test is either a pure unit test (e.g. `TokenBucket`,
`SsrfValidator`, `SlugValidator`, key generator/hasher) or a `@WebMvcTest` MockMvc test
with `@MockBean` service mocks and WireMock where an HTTP upstream is needed (WireMock
runs in-process in the JVM — no Docker). Full end-to-end DB-backed tests are out of
scope for this laptop setup; the user runs the real flow against the hosted Aiven MySQL.

## D0.12 — `curl.exe` used in all Windows commands
PowerShell aliases `curl` to `Invoke-WebRequest`. All examples use `curl.exe` so the
real curl bundled with Windows 10+ is invoked.

## D0.13 — `docs/SPEC.md` is the source spec, copied verbatim into the repo
The spec is the single source of truth. It lives at `docs/SPEC.md` so the repo matches
the layout in the spec.

## Open questions (none currently)
None. New questions get appended here as they arise.

---

## Per-phase decisions (D1–D9)

### D1 — `TokenBucket` created in Phase 1, not Phase 5
The spec's build order puts `TokenBucket` in Phase 5, but the login throttle (Phase 1)
needs it ("the same `TokenBucket` class also throttles login attempts"). So `TokenBucket`
is created in Phase 1 (in `ratelimit/`) with its basic unit tests; Phase 5 adds
`RateLimiterRegistry` + the 100-thread concurrency test. Functionally identical to the
spec's end state.

### D2 — `ApiKey` cache value (CachedKey) includes the API fields per spec
`CachedKey` carries the key id, api id, active flag, expiry, capacity, refill rate, plus
the spec's API fields (slug, upstream URL, timeout, enabled). The gateway resolves the
request slug → `Api` separately (a unique-index lookup) so it can honour "unknown slug is
not logged" (logging uses the request slug's api id, which is null when the slug is
unknown). The `CachedKey` API fields are carried for spec fidelity; the gateway uses the
fresh slug-resolved `Api` for the enabled/upstream/timeout decisions (never stale).

### D3 — Unknown slug + valid key → 403 KEY_NOT_FOR_API, not logged
When the request slug does not resolve to an API but the key is valid (for some other API),
the gateway returns `403 KEY_NOT_FOR_API` and does **not** log (there is no request-slug
api id). This satisfies "Only requests for an existing API are logged." Recorded because
the spec's error table does not list an explicit "API not found" code.

### D4 — Rate-limiter bucket eviction is self-healing, not hook-driven
The spec says "when a key's limits are changed through PATCH, remove its bucket so the
next request creates a new one." Instead of an eviction hook from `KeyService` into the
registry, `RateLimiterRegistry.tryConsume` detects that the existing bucket's
capacity/refill no longer match the requested limits and rebuilds it. Outcome is identical:
the next request after a PATCH uses the new limits. This keeps `KeyService` decoupled from
the rate limiter (no new interface). A revoke does not need bucket eviction because a
revoked key fails at the key-validity step before the bucket is consulted.

### D5 — `X-RateLimit-*` headers on every post-bucket response
`X-RateLimit-Limit` / `X-RateLimit-Remaining` are added on success, 413, 502, 504 (the
bucket was consulted and a token consumed) and on 429 (with `Retry-After`). Pre-bucket
errors (401/403/503) get only `X-Request-Id`. This matches the spec's core logic #2
("Response headers: X-RateLimit-Limit = capacity, X-RateLimit-Remaining = remaining. On
429 add Retry-After.").

### D6 — Async usage writer built directly (no intermediate synchronous version)
The spec's "Minimum version" table mentions a plain DB-save first version replaced by the
async batch writer later. To avoid churn, Phase 6 builds the async `AsyncUsageRecorder`
directly (bounded queue, background thread, `JdbcTemplate.batchUpdate`,
`last_used_at` update, shutdown flush, dropped counter). The synchronous intermediate is
not committed. The flush loop uses `queue.poll(flushInterval)` + `drainTo(batchSize)` so it
drains as soon as a batch is ready OR every 1s — approximating the spec's "every 1 second
or when 100 events are waiting, whichever comes first."

### D7 — Analytics use `JdbcTemplate` with parameterized SQL (no entity)
`usage_logs` is written by the async recorder via `JdbcTemplate.batchUpdate` and read by
`AnalyticsService` via parameterized `JdbcTemplate` queries (spec: "JdbcTemplate for batch
usage inserts"). No JPA entity for `usage_logs` is needed; the Flyway schema owns it. Time
series gap-filling is done in Java (full hourly/daily bucket sequence with zeros for gaps).

### D8 — Tests use `@WebMvcTest` + `@MockBean`, never `@SpringBootTest` with a DB
Per the user's environment (no Docker, no Testcontainers), every controller test is a
`@WebMvcTest` slice with `@AutoConfigureMockMvc(addFilters = false)` and mocked services;
service/logic tests are pure JUnit + Mockito. No test loads a real datasource. The
`JwtAuthFilter` (a `@Component` `OncePerRequestFilter`) is scanned by the slice, so each
controller test `@MockBean`s `JwtService` to satisfy the filter's constructor (it never
runs because filters are off).

### D9 — Load test is stdlib Python
`loadtest/loadtest.py` uses only `urllib`, `json`, `threading`, `statistics` so it runs on
any Python 3 with no `pip install`. It registers a user, creates an API + key, warms the
cache, measures direct upstream p95, hammers `/gw/{slug}` at the target rate, and prints
the 200/429 share, gateway p95 overhead, and dropped-events count. Numbers are filled into
the README after the user runs it.
