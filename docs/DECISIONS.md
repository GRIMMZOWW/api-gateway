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
