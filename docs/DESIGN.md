# Design notes

Why Gatecraft works the way it does. These are the choices I would explain in an interview.

## What happens to a request

A call to `/gw/{slug}/...` goes through these checks in order:

1. Read the `X-API-Key` header. No header gives a 401.
2. Hash the key and look it up. Unknown, revoked or expired keys all give the same 401.
3. Check the key belongs to this API. If not, 403. An unknown slug also gives 403, so nobody can find out which slugs exist.
4. If the API is switched off, 503. This check comes before the rate limiter.
5. Take a token from the key's bucket. If it is empty, 429 with `Retry-After`.
6. Read the body (up to 1 MB) and forward it to the upstream. A refused connection gives 502 and a timeout gives 504.
7. Send back the upstream's answer and write a usage log row.

## Choices

**Keys are hashed with SHA-256, not BCrypt.** A key is 256 bits of random data, so it can't be guessed the way a password can. BCrypt is slow on purpose, and every gateway request has to hash its key, so SHA-256 is the right tool. Passwords still use BCrypt.

**The rate limiter is a token bucket held in memory.** There is one bucket per key in a `ConcurrentHashMap`. The bucket refills by `elapsed time x refill rate` each time it is checked, so no timer thread is needed. When a key's limits are edited, its bucket is replaced.

**Creating buckets had a race.** My first version checked for a bucket and then created one in two steps, so two threads could each create a full bucket. A concurrency test caught it (expected 50 allowed, got 79). `ConcurrentHashMap.compute` makes the check and the create one atomic.

**Key lookups are cached for 60 seconds** with Caffeine, so most requests never touch the database. Revoking or editing a key evicts it from the cache straight away, which is why a revoked key fails on its next request.

**Usage logs are written in batches.** Requests put an event on a queue, and a background thread writes them to MySQL together. This keeps logging off the request path. If the queue fills up, events are dropped and counted on the health endpoint.

**Everything is scoped to the signed-in user.** Asking for someone else's API or key returns 404, not 403, so it doesn't confirm the thing exists.

**Upstream URLs are checked for SSRF** when an API is saved and again on every forward. Localhost, private and link-local addresses are rejected.

**Dashboard sessions use a JWT that lasts 60 minutes.** There is no refresh token, so you sign in again after an hour.

## Endpoints

| Area | Endpoints |
| --- | --- |
| Account | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| APIs | `GET` and `POST /api/apis`, `GET`, `PATCH` and `DELETE /api/apis/{id}` |
| Keys | `GET` and `POST /api/apis/{id}/keys`, `PATCH /api/keys/{id}`, `POST /api/keys/{id}/revoke` |
| Analytics | `GET /api/analytics/summary`, `timeseries`, `top-paths`, `keys`, `recent` (all need `apiId`) |
| Gateway | any method on `/gw/{slug}/**` with an `X-API-Key` header |

## Limits

- 10 APIs per account, 20 active keys per API, request bodies up to 1 MB, analytics up to 90 days.
- Rate limit buckets live in memory, so this only works as a single server. Several servers would need shared counters, for example in Redis.
- No load test has been run yet.
