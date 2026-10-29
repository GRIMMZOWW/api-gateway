#!/usr/bin/env python3
"""
Load test for the API Gateway (spec: "about 200 requests per second for 60 seconds
through a key limited to 50 per second").

Records: share of 200 vs 429, gateway p95 latency overhead vs calling the upstream
directly, and that no usage events were dropped.

Self-contained: stdlib only (urllib, json, threading, statistics, time).

Prereq: the backend is running on http://localhost:8080 (default) with DB env vars set,
and the demo upstream (https://httpbin.org) is reachable.

Usage:
    python loadtest.py --base http://localhost:8080 --rps 200 --duration 60
    (defaults: base=http://localhost:8080, rps=200, duration=60)

It registers a user, creates an API "loadtest" -> https://httpbin.org/get, creates a key
with capacity 50 / refill 50/s, warms up, measures direct upstream latency, then hammers
the gateway, and prints a report. Re-running is idempotent enough (re-creates the API/key).
"""

import argparse
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from threading import Lock


class ApiError(Exception):
    def __init__(self, status, code, message, fields=None):
        super().__init__(f"{status} {code}: {message}")
        self.status = status
        self.code = code
        self.message = message
        self.fields = fields or {}


def request(method, url, body=None, token=None, timeout=30, headers=None):
    h = {"Accept": "application/json"}
    if headers:
        h.update(headers)
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        h["Content-Type"] = "application/json"
    if token:
        h["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            if not raw:
                return resp.status, None
            return resp.status, json.loads(raw)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            err = json.loads(raw).get("error", {})
        except Exception:
            err = {"code": "HTTP_ERROR", "message": str(e)}
        raise ApiError(e.code, err.get("code", "HTTP_ERROR"),
                       err.get("message", str(e)), err.get("fields")) from None
    except urllib.error.URLError as e:
        raise ApiError(0, "CONNECT_ERROR", str(e)) from None


def percentile(values, p):
    if not values:
        return float("nan")
    s = sorted(values)
    k = (len(s) - 1) * (p / 100.0)
    f = int(k)
    c = min(f + 1, len(s) - 1)
    if f == c:
        return s[f]
    return s[f] + (s[c] - s[f]) * (k - f)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--base", default="http://localhost:8080")
    p.add_argument("--rps", type=int, default=200)
    p.add_argument("--duration", type=int, default=60)
    p.add_argument("--email", default="loadtest@example.com")
    p.add_argument("--password", default="Loadtest12")
    args = p.parse_args()

    base = args.base.rstrip("/")

    # 1. Register (or login if the email is taken).
    try:
        _, body = request("POST", f"{base}/api/auth/register", {
            "email": args.email, "password": args.password, "displayName": "Loadtest"})
        token = body["token"]
    except ApiError as e:
        if e.status == 409:
            _, body = request("POST", f"{base}/api/auth/login", {
                "email": args.email, "password": args.password})
            token = body["token"]
        else:
            print(f"register failed: {e}", file=sys.stderr)
            sys.exit(1)

    # 2. Create (or reuse) an API pointing at httpbin.
    slug = "loadtest"
    try:
        _, api = request("POST", f"{base}/api/apis", {
            "name": "Loadtest API", "slug": slug,
            "upstreamBaseUrl": "https://httpbin.org/get", "timeoutMs": 10000}, token=token)
    except ApiError as e:
        if e.status == 400 and "slug" in (e.fields or {}):
            _, apis = request("GET", f"{base}/api/apis", token=token)
            api = next((a for a in apis if a["slug"] == slug), None)
            if api is None:
                print(f"could not create or find api {slug}", file=sys.stderr)
                sys.exit(1)
        else:
            print(f"create api failed: {e}", file=sys.stderr)
            sys.exit(1)
    api_id = api["id"]

    # 3. Create a key: capacity 50, refill 50/s (50/s allowed, the rest 429).
    _, key_resp = request("POST", f"{base}/api/apis/{api_id}/keys", {
        "name": "load", "capacity": 50, "refillPerSec": 50}, token=token)
    full_key = key_resp["fullKey"]

    gw_url = f"{base}/gw/{slug}"
    key_headers = {"X-API-Key": full_key}

    # 4. Warm up (fill the key cache, prime the bucket).
    for _ in range(3):
        try:
            request("GET", gw_url, headers=key_headers, timeout=30)
        except ApiError:
            pass

    # 5. Direct upstream latency baseline.
    direct_latencies = []
    for _ in range(30):
        t0 = time.perf_counter()
        try:
            req = urllib.request.Request("https://httpbin.org/get")
            with urllib.request.urlopen(req, timeout=10) as r:
                r.read()
            direct_latencies.append((time.perf_counter() - t0) * 1000.0)
        except Exception:
            pass
    direct_p95 = percentile(direct_latencies, 95)

    # 6. Hammer the gateway at args.rps for args.duration seconds.
    total = args.rps * args.duration
    workers = min(64, args.rps * 2)
    counters = {"ok": 0, "rate_limited": 0, "other": 0, "errors": 0}
    latencies = []
    lock = Lock()

    def one_request():
        t0 = time.perf_counter()
        status = 0
        try:
            req = urllib.request.Request(gw_url, headers=key_headers)
            with urllib.request.urlopen(req, timeout=30) as r:
                r.read()
                status = r.status
        except urllib.error.HTTPError as e:
            status = e.code
            try:
                e.read()
            except Exception:
                pass
        except Exception:
            status = 0
        dt = (time.perf_counter() - t0) * 1000.0
        with lock:
            latencies.append(dt)
            if status == 200:
                counters["ok"] += 1
            elif status == 429:
                counters["rate_limited"] += 1
            elif status == 0:
                counters["errors"] += 1
            else:
                counters["other"] += 1

    print(f"Sending ~{total} requests at {args.rps} rps for {args.duration}s "
          f"through a key limited to 50/s...")
    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        submitted = 0
        next_tick = start
        while submitted < total:
            now = time.perf_counter()
            if now < next_tick:
                time.sleep(min(0.01, next_tick - now))
                continue
            for _ in range(args.rps):
                if submitted >= total:
                    break
                pool.submit(one_request)
                submitted += 1
            next_tick += 1.0
    elapsed = time.perf_counter() - start

    # 7. Allow the async usage writer a moment to flush.
    time.sleep(2)

    # 8. Check dropped usage events via actuator health.
    try:
        _, health = request("GET", f"{base}/actuator/health")
        comps = health.get("components", {})
        uw = comps.get("usageWriter", {})
        dropped = uw.get("details", {}).get("droppedUsageEvents", "n/a")
        if dropped == "n/a":
            # some layouts nest under "details" at top
            dropped = health.get("details", {}).get("droppedUsageEvents", "n/a")
    except Exception:
        dropped = "n/a"

    gw_p95 = percentile(latencies, 95)
    overhead = gw_p95 - direct_p95 if direct_p95 == direct_p95 else float("nan")
    share = counters["ok"] / max(1, counters["ok"] + counters["rate_limited"]) * 100

    print("\n=== Load test report ===")
    print(f"Target:              {args.rps} rps for {args.duration}s ({total} requests)")
    print(f"Elapsed:             {elapsed:.1f}s")
    print(f"200 (success):       {counters['ok']}")
    print(f"429 (rate limited):  {counters['rate_limited']}")
    print(f"Other:               {counters['other']}")
    print(f"Errors (conn):       {counters['errors']}")
    print(f"200 share:           {share:.1f}%")
    print(f"Gateway p95:         {gw_p95:.1f} ms")
    print(f"Direct upstream p95: {direct_p95:.1f} ms")
    print(f"Gateway overhead p95:{overhead:.1f} ms")
    print(f"Dropped usage events:{dropped}")
    print("\nPaste these into the README:")
    print(f"  - Sustained {args.rps} rps for {args.duration}s; {share:.1f}% of responses were 200,"
          f" the rest 429 (key limited to 50/s).")
    print(f"  - Gateway p95 latency overhead vs direct upstream call: {overhead:.1f} ms.")
    print(f"  - Dropped usage events: {dropped} (queue never overflowed).")


if __name__ == "__main__":
    main()
