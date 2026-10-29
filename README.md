# API Gateway with Usage Analytics

A multi-tenant API gateway: developers register an upstream API, issue hashed API keys,
enforce per-key token-bucket rate limits, and see usage analytics on a React dashboard.

**Stack:** Java 21, Spring Boot 3.3.x, MySQL 8 (hosted on Aiven, SSL), Flyway,
Spring Security + JWT (HS256), React 19 + Vite + React Router 7 (library mode),
Recharts, plain CSS. No Lombok. No Docker.

> **Status:** Phase 0 — project skeleton, Flyway schema, `/actuator/health`. See
> `docs/SPEC.md` for the full specification and the 10-phase build plan.
> One git commit per phase: `git log --oneline` shows what each phase added.

## Prerequisites (Windows)

- Java 21 — check with `java -version`. If Maven can't find Java, set `JAVA_HOME`:
  `$env:JAVA_HOME="C:\Program Files\Java\jdk-21"` (adjust to your install path).
- A MySQL 8 database. This project targets a **hosted Aiven MySQL** that requires SSL.
- Internet access — the Maven Wrapper downloads Maven 3.9.9 on first run.

## Aiven MySQL setup (one-time)

1. Create a MySQL 8 service in the Aiven console.
2. Create a database for the app (e.g. `api_gateway`). You can use the default
   `avnadmin` user. Find host, port, database name, user, password under
   **"Connection information"**.
3. Build the JDBC URL in this exact format (note `sslMode=REQUIRED`):

   ```
   jdbc:mysql://<host>:<port>/<database>?sslMode=REQUIRED&serverTimezone=UTC
   ```

   Example:
   ```
   jdbc:mysql://my-gw-myproj.aivencloud.com:12345/api_gateway?sslMode=REQUIRED&serverTimezone=UTC
   ```

## First-time setup

1. **Set environment variables** in the PowerShell window you will run the app from.
   Spring Boot does not read `.env` files — use `$env:` variables. Replace the values
   with your Aiven details:
   ```powershell
   $env:DB_URL="jdbc:mysql://<host>:<port>/<database>?sslMode=REQUIRED&serverTimezone=UTC"
   $env:DB_USERNAME="avnadmin"
   $env:DB_PASSWORD="<your Aiven password>"
   $env:JWT_SECRET="<at least 32 bytes>"
   ```
   Generate a JWT secret in the same window:
   ```powershell
   $env:JWT_SECRET=[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
   ```
   These variables live only in this PowerShell session, so run `mvnw.cmd` from
   the same window.

2. **Build and run the backend** (first run downloads Maven 3.9.9 + all deps; ~2–4 min):
   ```powershell
   cd backend
   .\mvnw.cmd clean spring-boot:run
   ```

## What you should see (Phase 0 done-check)

App logs include:
- `Successfully validated 1 Flyway migration` (or `Migrating schema "<db>" to version "1 - init"`)
- `Tomcat started on port 8080 (http) with context path ''`
- `Started GatewayApplication in X.XXX seconds`

In a second PowerShell window:
```powershell
curl.exe http://localhost:8080/actuator/health
```
Expected: `{"status":"UP"}`

Verify the tables were created — open your Aiven service's SQL console in the browser
and run:
```sql
SHOW TABLES;
```
Expected rows: `apis`, `api_keys`, `flyway_schema_history`, `usage_logs`, `users`.

Optional smoke test (no DB required — stop the running app first with Ctrl+C):
```powershell
cd backend
.\mvnw.cmd test
```
Expected: `BUILD SUCCESS`, `Tests run: 1, Failures: 0, Errors: 0`.

## Project layout

```
backend/                 Spring Boot app
  src/main/java/com/example/gateway/   feature packages: auth, apis, keys, gateway,
                                       ratelimit, usage, analytics, common
  src/main/resources/
    application.yml
    db/migration/V1__init.sql          Flyway schema (4 tables)
  src/test/java/...
  pom.xml
  mvnw.cmd / mvnw         Maven Wrapper (downloads Maven 3.9.9)
frontend/                 React 19 + Vite (built in Phase 8)
docs/SPEC.md              The full specification
docs/DECISIONS.md         Design decisions and open questions
.env.example              Variable names and formats (NOT read by Spring)
README.md
```

## Known limitations (full list in `docs/DECISIONS.md`)

- **Single gateway instance only** — token-bucket state lives in one JVM. Stretch goal:
  move buckets to Redis with an atomic Lua script.
- Tests are unit tests + MockMvc with mocks (and WireMock in-process). No Docker or
  Testcontainers required.
