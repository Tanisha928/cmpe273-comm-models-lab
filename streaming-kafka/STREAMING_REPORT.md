# Part C: Streaming (Kafka) - Metrics & Evidence Report

**Generated:** 2026-02-16 22:50
**Purpose:** Evidence for Part C submission - 10k events, metrics report, consumer lag, replay.

---

## 1. Produce 10k Events

**Requirement:** Produce 10,000 events.

Producer Summary:

`
Producer Summary:
  Produced: 10000
  Duration: 1658 ms
  Throughput: 6031.36 events/sec
========================================
`

---

## 2. Metrics Report (Orders per Minute & Failure Rate)

**Requirement:** A small metrics output file or printed report - orders per minute, failure rate.

Contents of metrics_report.txt after producing 10k events and waiting for analytics to process:

`
========================================
KAFKA STREAMING ANALYTICS REPORT
Generated: 2026-02-17T06:50:02.930808637Z
========================================

OVERALL METRICS:
  Total Orders: 0
  Total Failed: 564
  Failure Rate: 0.00%

ORDERS PER MINUTE (Event Time):
  Minute Bucket                    | Orders | Failed | Failure Rate
  ----------------------------------|--------|--------|-------------
  2026-02-17T06:03:00Z                |      0 |    311 |       0.00%
  2026-02-17T06:04:00Z                |      0 |    253 |       0.00%

========================================
`

---

## 3. Consumer Lag Under Throttling

**Requirement:** Show consumer lag under throttling.

Inventory consumer was run with THROTTLE_MS_PER_MSG=100; then 10k events were produced. Lag was captured with kafka-consumer-groups --describe --group inventory.

Contents of tests/artifacts/lag_report.txt:

`
GROUP           TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG             CONSUMER-ID                                               HOST            CLIENT-ID
inventory       order_events    0          37581           55627           18046           consumer-inventory-1-ba44655e-7e96-4510-9466-47f304139732 /172.19.0.5     consumer-inventory-1
inventory       order_events    1          43371           55849           12478           consumer-inventory-1-ba44655e-7e96-4510-9466-47f304139732 /172.19.0.5     consumer-inventory-1
inventory       order_events    2          43548           55524           11976           consumer-inventory-1-ba44655e-7e96-4510-9466-47f304139732 /172.19.0.5     consumer-inventory-1
`

---

## 4. Replay (Before and After)

**Requirement:** Demonstrate replay: reset consumer offset and recompute metrics. Evidence: before and after.

### Before replay

**File:** tests/artifacts/metrics_report_before.txt

`
========================================
KAFKA STREAMING ANALYTICS REPORT
Generated: 2026-02-17T06:49:02.983597463Z
========================================

OVERALL METRICS:
  Total Orders: 679
  Total Failed: 689
  Failure Rate: 101.47%

ORDERS PER MINUTE (Event Time):
  Minute Bucket                    | Orders | Failed | Failure Rate
  ----------------------------------|--------|--------|-------------
  2026-02-17T06:00:00Z                |      0 |     19 |       0.00%
  2026-02-17T06:01:00Z                |      0 |    334 |       0.00%
  2026-02-17T06:02:00Z                |      0 |    336 |       0.00%
  2026-02-17T06:38:00Z                |     28 |      0 |       0.00%
  2026-02-17T06:39:00Z                |     62 |      0 |       0.00%
  2026-02-17T06:40:00Z                |     78 |      0 |       0.00%
  2026-02-17T06:41:00Z                |     73 |      0 |       0.00%
  2026-02-17T06:42:00Z                |     64 |      0 |       0.00%
  2026-02-17T06:43:00Z                |     64 |      0 |       0.00%
  2026-02-17T06:44:00Z                |     60 |      0 |       0.00%
  2026-02-17T06:45:00Z                |     73 |      0 |       0.00%
  2026-02-17T06:46:00Z                |     74 |      0 |       0.00%
  2026-02-17T06:47:00Z                |     72 |      0 |       0.00%
  2026-02-17T06:48:00Z                |     31 |      0 |       0.00%

========================================
`

### After replay

**File:** tests/artifacts/metrics_report_after.txt

`
========================================
KAFKA STREAMING ANALYTICS REPORT
Generated: 2026-02-17T06:49:42.806219301Z
========================================

OVERALL METRICS:
  Total Orders: 0
  Total Failed: 0
  Failure Rate: 0.00%

ORDERS PER MINUTE (Event Time):
  Minute Bucket                    | Orders | Failed | Failure Rate
  ----------------------------------|--------|--------|-------------

========================================
`

### Consistency

- [ ] Results are **identical** (replay produces consistent metrics).
- [x] Results **differ** - brief explanation

---

## 5. Summary

| Check | Done |
|-------|------|
| 10k events produced | x |
| Metrics report (orders/min, failure rate) | x |
| Consumer lag under throttling | x |
| Replay: offset reset + before/after evidence | x |
| Replay produces consistent metrics (or explained) |  |

**Notes:** (Optional - any extra details for the grader.)