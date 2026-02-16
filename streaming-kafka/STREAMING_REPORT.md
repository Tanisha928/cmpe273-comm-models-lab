# Part C: Streaming (Kafka) – Metrics & Evidence Report

**Generated:** _(fill date)_  
**Purpose:** Evidence for Part C submission – 10k events, metrics report, consumer lag, replay.

---

## 1. Produce 10k Events

**Requirement:** Produce 10,000 events.

Paste the **Producer Summary** from the producer run (last few lines of `docker compose run --rm -e EVENTS=10000 producer_order` or from `.\tests\produce_10k.ps1`):

```
========================================
Producer Summary:
  Produced: 10000
  Duration: 4087 ms
  Throughput: 2446.78 events/sec
========================================
```

_(Paste your output below.)_

---

## 2. Metrics Report (Orders per Minute & Failure Rate)

**Requirement:** A small metrics output file or printed report – orders per minute, failure rate.

Contents of `metrics_report.txt` (or equivalent) after producing 10k events and waiting for analytics to process:

```
_(Paste full or relevant part of metrics_report.txt below.)_
```

---

## 3. Consumer Lag Under Throttling

**Requirement:** Show consumer lag under throttling.

Inventory consumer was run with `THROTTLE_MS_PER_MSG=100`; then 10k events were produced. Lag was captured with `kafka-consumer-groups --describe --group inventory`.

Contents of `tests/artifacts/lag_report.txt` (or screenshot):

```
_(Paste lag report below. LAG column should be > 0 for some partitions.)_
```

---

## 4. Replay (Before and After)

**Requirement:** Demonstrate replay: reset consumer offset and recompute metrics. Evidence: before and after.

### Before replay

Metrics captured **before** resetting the analytics consumer group offset to earliest:

**File:** `tests/artifacts/metrics_report_before.txt`

```
_(Paste first ~20–30 lines or full file below.)_
```

### After replay

Metrics captured **after** resetting offsets to earliest and letting analytics recompute:

**File:** `tests/artifacts/metrics_report_after.txt`

```
_(Paste first ~20–30 lines or full file below.)_
```

### Consistency

- [ ] Results are **identical** (replay produces consistent metrics).
- [ ] Results **differ** – brief explanation: _(e.g., different run, extra events, timing)_

---

## 5. Summary

| Check | Done |
|-------|------|
| 10k events produced | |
| Metrics report (orders/min, failure rate) | |
| Consumer lag under throttling | |
| Replay: offset reset + before/after evidence | |
| Replay produces consistent metrics (or explained) | |

**Notes:** _(Optional – any extra details for the grader.)_
