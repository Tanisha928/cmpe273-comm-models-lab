# Streaming-Kafka Report Files – What Each Is For

| File / path | Type | Who creates it | Use |
|-------------|------|----------------|-----|
| **STREAMING_REPORT.md** | File | You / `generate_report.ps1` | **Main submission report.** Filled with producer summary, metrics report, lag report, replay before/after, and summary table. Submit this (or its PDF) for Part C. |
| **analytics_reports/metrics_report.txt** | File (dir mount) | Analytics consumer (Docker) | Live analytics output: orders per minute, failure rate. Written every ~10 seconds. Used for Section 2 of STREAMING_REPORT and for replay before/after. |
| **tests/artifacts/producer_output.txt** | File | `generate_report.ps1` | Raw console output from the 10k producer run. Used to extract the “Producer Summary” block for STREAMING_REPORT. |
| **tests/artifacts/lag_report.txt** | File | `lag_demo.ps1` | Output of `kafka-consumer-groups --describe --group inventory`. Shows consumer lag under throttling (Section 3 of STREAMING_REPORT). |
| **tests/artifacts/metrics_report_before.txt** | **Must be a file** | `replay_demo.ps1` | Snapshot of analytics metrics **before** resetting offsets (replay). Used for “Before replay” in STREAMING_REPORT. |
| **tests/artifacts/metrics_report_after.txt** | **Must be a file** | `replay_demo.ps1` | Snapshot of analytics metrics **after** replay. Used for “After replay” in STREAMING_REPORT. |
| **tests/generate_report.ps1** | Script | N/A | Runs all tests and fills STREAMING_REPORT.md, or with `-GenerateOnly` only fills the report from existing artifacts. |

---

## Auto-fix

**generate_report.ps1** now checks at startup: if `metrics_report.txt` or `metrics_report_before.txt` / `metrics_report_after.txt` are directories, it removes them and creates empty files so the rest of the run works.

## If before/after artifact paths are directories

If `tests/artifacts/metrics_report_before.txt` or `metrics_report_after.txt` were created as **directories**, generate_report.ps1 will replace them with empty files at startup. To fix manually (run from `streaming-kafka`):

```powershell
$art = "tests\artifacts"
foreach ($name in "metrics_report_before.txt", "metrics_report_after.txt") {
  $p = Join-Path $art $name
  if (Test-Path $p) {
    if ((Get-Item $p).PSIsContainer) {
      Remove-Item $p -Recurse -Force
      New-Item -ItemType File -Path $p -Force
    }
  }
}
```
