# Part C: Streaming (Kafka) – Step-by-Step Testing Guide

This guide walks you through testing the Kafka implementation and collecting evidence for submission.

---

## Prerequisites

- Docker and Docker Compose installed and running.
- Terminal: use **PowerShell** on Windows, or **bash** (Git Bash / WSL / Mac/Linux) for `.sh` scripts.

---

## Step 1: Start the Stack

From the **streaming-kafka** directory:

```powershell
# Windows (PowerShell)
cd path\to\cmpe273-comm-models-lab\streaming-kafka
docker compose up -d
```

```bash
# Linux/Mac/Git Bash
cd streaming-kafka
docker compose up -d
```

Wait ~15–20 seconds for Zookeeper, Kafka, topic creation, and consumers to be ready.

**Check:** All services should be up (producer may show "Exited" after its batch—that’s OK).

```powershell
docker compose ps
```

---

## Step 2: Produce 10,000 Events (Testing Requirement)

**Requirement:** Produce 10k events.

### Option A: Use the test script

**PowerShell (Windows):**
```powershell
cd tests
.\produce_10k.ps1
```

**Bash:**
```bash
chmod +x tests/produce_10k.sh
./tests/produce_10k.sh
```

### Option B: Run producer manually

```powershell
docker compose run --rm -e EVENTS=10000 producer_order
```

**Evidence to collect:**
- Producer console output showing **Produced: 10000** and throughput.
- Screenshot or copy-paste of the final "Producer Summary" block.

**Where to put it:** In `STREAMING_REPORT.md` under **Section 1: Produce 10k Events**.

---

## Step 3: Metrics Report (Orders per Minute & Failure Rate)

**Requirement:** Show a small metrics output file or printed report (orders per minute, failure rate).

The analytics consumer writes to `metrics_report.txt` in the **streaming-kafka** directory (updated every 10 seconds).

1. Wait ~30–60 seconds after producing 10k events so the analytics consumer can process them.
2. Open or print the report:

```powershell
# From streaming-kafka directory
Get-Content metrics_report.txt
# Or: type metrics_report.txt
```

```bash
cat metrics_report.txt
```

**Evidence to collect:**
- Copy the full contents of `metrics_report.txt` (or at least the overall metrics and a few minute buckets).

**Where to put it:** In `STREAMING_REPORT.md` under **Section 2: Metrics Report**. You can also submit `metrics_report.txt` as a file.

---

## Step 4: Consumer Lag Under Throttling (Testing Requirement)

**Requirement:** Show consumer lag under throttling.

This step slows the inventory consumer so it falls behind the producer; then we measure lag.

1. **Create artifacts folder** (if it doesn’t exist):
   ```powershell
   New-Item -ItemType Directory -Force -Path tests\artifacts
   ```

2. **Run the lag demo:**

   **PowerShell:**
   ```powershell
   .\tests\lag_demo.ps1
   ```

   **Bash:**
   ```bash
   chmod +x tests/lag_demo.sh
   ./tests/lag_demo.sh
   ```

   The script will:
   - Restart the inventory consumer with **100 ms delay per message** (throttling).
   - Produce 10,000 events.
   - Wait 30 seconds.
   - Run Kafka’s consumer-group describe and save output to `tests/artifacts/lag_report.txt`.

3. **View the lag report:**
   ```powershell
   Get-Content tests\artifacts\lag_report.txt
   ```

**Evidence to collect:**
- Contents of `tests/artifacts/lag_report.txt` (or a screenshot). Look for **LAG** column > 0 for the `inventory` group.

**Where to put it:** In `STREAMING_REPORT.md` under **Section 3: Consumer Lag Under Throttling**. You can also submit `tests/artifacts/lag_report.txt`.

---

## Step 5: Replay – Reset Offset and Recompute Metrics (Testing Requirement)

**Requirement:** Demonstrate replay: reset consumer offset and recompute metrics. Show before/after evidence; replay should produce consistent metrics (or explain why not).

The replay demo:
1. Produces events and captures metrics **before** replay.
2. Resets the **analytics** consumer group offsets to **earliest** (replay).
3. Runs analytics again and captures metrics **after** replay.
4. Compares before vs after (should be identical for event-time–based metrics).

1. **Start from a clean state** (recommended):
   ```powershell
   docker compose down
   docker compose up -d
   ```
   Wait ~15 seconds.

2. **Run the replay demo:**

   **PowerShell:**
   ```powershell
   .\tests\replay_demo.ps1
   ```

   **Bash:**
   ```bash
   chmod +x tests/replay_demo.sh
   ./tests/replay_demo.sh
   ```

3. **Check artifacts:**
   - `tests/artifacts/metrics_report_before.txt` – metrics before offset reset.
   - `tests/artifacts/metrics_report_after.txt` – metrics after replay.

**Evidence to collect:**
- Paste or attach **before** and **after** metrics (full file or first 20–30 lines each).
- Note whether the script reported “Results are IDENTICAL” or “Results differ.”
- If results differ, add a short explanation (e.g., timing, different event set).

**Where to put it:** In `STREAMING_REPORT.md` under **Section 4: Replay (Before and After)**. Submit the two files as well if required.

---

## Step 6: Fill the Report and What to Submit

1. Open **STREAMING_REPORT.md** in the `streaming-kafka` folder.
2. Fill in each section with:
   - Pasted output (producer summary, metrics report, lag report, before/after replay).
   - Short notes (e.g., “Lag visible under throttling,” “Replay produced identical metrics”).
3. Save the report.

**Suggested submission checklist:**

| Item | Description |
|------|-------------|
| **Metrics output** | `metrics_report.txt` and/or the “Metrics Report” section in `STREAMING_REPORT.md` |
| **10k events** | Producer summary (in report or screenshot) showing 10,000 events produced |
| **Consumer lag** | `tests/artifacts/lag_report.txt` and/or “Consumer Lag” section in `STREAMING_REPORT.md` |
| **Replay evidence** | `metrics_report_before.txt`, `metrics_report_after.txt`, and “Replay” section in `STREAMING_REPORT.md` |
| **Report** | Completed `STREAMING_REPORT.md` (or PDF/print of it) |

---

## Quick Reference – Commands (PowerShell)

Run from **streaming-kafka**:

```powershell
# Start
docker compose up -d

# Produce 10k
.\tests\produce_10k.ps1

# View metrics
Get-Content metrics_report.txt

# Lag demo (throttling + lag report)
.\tests\lag_demo.ps1
Get-Content tests\artifacts\lag_report.txt

# Replay demo (before/after metrics)
.\tests\replay_demo.ps1
Get-Content tests\artifacts\metrics_report_before.txt
Get-Content tests\artifacts\metrics_report_after.txt

# Stop
docker compose down
```

---

## Troubleshooting

- **Producer “Exited”:** Normal; it’s a batch job. Check logs: `docker compose logs producer_order`.
- **metrics_report.txt empty or old:** Ensure analytics consumer is running (`docker compose ps`) and wait at least 10–20 seconds after producing events.
- **Lag report shows LAG 0:** Run the lag demo so that the producer sends 10k events while the inventory consumer is throttled (100 ms/msg); then check `lag_report.txt` again after the wait.
- **Replay results differ:** Analytics uses event-time bucketing; with the same events and same offsets, results should match. If they differ, note in the report (e.g., different run or extra events).
