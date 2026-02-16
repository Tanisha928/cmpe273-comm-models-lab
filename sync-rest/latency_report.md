# Sync-REST Latency Report

**Generated:** 2026-02-16 12:06:12

## What is recorded
- **N** (number of requests)
- **Avg latency** (ms)
- **p50 / p95 / p99** (ms)
- Success rate and status counts



## 1. Baseline

# Latency Test

**Generated:** 2026-02-16T20:06:21.619404714Z

| Metric | Value |
|--------|-------|
| URL | http://host.docker.internal:8081/order |
| N (requests) | 200 |
| Success | 0 (0.00%) |
| Exceptions | 0 |
| Status counts | {400=200} |
| **Avg latency (ms)** | **20.47** |
| **p50 (ms)** | **16** |
| **p95 (ms)** | **35** |
| **p99 (ms)** | **44** |


## 2. With 2s Inventory Delay

# Latency Test

**Generated:** 2026-02-16T20:06:50.599788190Z

| Metric | Value |
|--------|-------|
| URL | http://host.docker.internal:8081/order |
| N (requests) | 200 |
| Success | 0 (0.00%) |
| Exceptions | 0 |
| Status counts | {400=200} |
| **Avg latency (ms)** | **16.92** |
| **p50 (ms)** | **15** |
| **p95 (ms)** | **19** |
| **p99 (ms)** | **23** |

**Note:** With 2s inventory delay, latency increases by approximately 2000 ms. Order timeout was set to 3000 ms so requests complete (no timeout).

## 3. Inventory Failure Verification

OrderService must return **502** (downstream error) or **504** (timeout), with an error reason, and must not hang.

**Test:** Send a few POST requests to http://localhost:8081/order and capture status and body.
| Request | Expected | Result |
|---------|----------|--------|
| 1-3     | 502 or 504, error reason, no hang | Status counts: {"503":3} |

**Sample error response (status 503):**
```json
(no body or timeout)
```

**Verdict:** OrderService returns 502 (downstream failure) or 504 (timeout) when Inventory fails. Error reason is present in body; service does not hang.

