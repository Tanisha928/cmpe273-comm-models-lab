# Generate STREAMING_REPORT.md from test runs or existing artifacts.
# Usage:
#   .\tests\generate_report.ps1           # Run all tests then generate report
#   .\tests\generate_report.ps1 -GenerateOnly   # Only fill report from existing artifacts
param([switch]$GenerateOnly)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
$artifactsDir = Join-Path $scriptDir "artifacts"
$reportPath = Join-Path $rootDir "STREAMING_REPORT.md"

if (-not (Test-Path $artifactsDir)) { New-Item -ItemType Directory -Path $artifactsDir -Force | Out-Null }

# Ensure analytics_reports dir exists; ensure before/after artifact paths are files not dirs
function Ensure-FileNotDirectory {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    if ((Get-Item $Path -ErrorAction SilentlyContinue).PSIsContainer) {
        Remove-Item $Path -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType File -Path $Path -Force | Out-Null
    }
}
Set-Location $rootDir
$reportsDir = Join-Path $rootDir "analytics_reports"
if (-not (Test-Path $reportsDir)) { New-Item -ItemType Directory -Path $reportsDir -Force | Out-Null }
Ensure-FileNotDirectory (Join-Path $artifactsDir "metrics_report_before.txt")
Ensure-FileNotDirectory (Join-Path $artifactsDir "metrics_report_after.txt")

function Get-SafeFileContent {
    param([string]$Path, [int]$MaxLines = 0)
    if (-not (Test-Path $Path)) { return "(File not found: $Path)" }
    try {
        if ($MaxLines -gt 0) {
            $lines = Get-Content $Path -TotalCount $MaxLines -ErrorAction Stop
            return ($lines -join "`n").Trim()
        }
        return (Get-Content $Path -Raw -ErrorAction Stop).Trim()
    } catch {
        return "(Could not read: $Path)"
    }
}

# ----- Run tests if not GenerateOnly -----
if (-not $GenerateOnly) {
    Write-Host "=========================================="
    Write-Host "Running tests and generating report"
    Write-Host "=========================================="
    Set-Location $rootDir

    # 1. Ensure stack is up
    Write-Host "`n[1/4] Starting stack..."
    $ErrorActionPreference = "SilentlyContinue"
    docker compose up -d | Out-Null
    $ErrorActionPreference = "Stop"
    Start-Sleep -Seconds 15

    # 2. Produce 10k and capture output
    Write-Host "`n[2/4] Producing 10,000 events..."
    $producerOut = Join-Path $artifactsDir "producer_output.txt"
    $ErrorActionPreference = "SilentlyContinue"
    & .\tests\produce_10k.ps1 2>&1 | Tee-Object -FilePath $producerOut
    $ErrorActionPreference = "Stop"
    Write-Host "Waiting for analytics to process..."
    Start-Sleep -Seconds 45

    # 3. Lag demo
    Write-Host "`n[3/4] Running lag demo (throttled consumer + 10k events)..."
    $ErrorActionPreference = "SilentlyContinue"
    & .\tests\lag_demo.ps1 2>&1 | Out-Null
    $ErrorActionPreference = "Stop"

    # 4. Replay demo
    Write-Host "`n[4/4] Running replay demo..."
    $ErrorActionPreference = "SilentlyContinue"
    & .\tests\replay_demo.ps1 2>&1 | Out-Null
    $ErrorActionPreference = "Stop"

    Write-Host "`nTests complete. Generating report..."
} else {
    Write-Host "GenerateOnly: using existing artifacts in tests\artifacts\"
    Set-Location $rootDir
}

# ----- Collect content for report -----
$dateStr = Get-Date -Format "yyyy-MM-dd HH:mm"
$producerSummary = "(No producer output)"
$producerOutPath = Join-Path $artifactsDir "producer_output.txt"
if (Test-Path $producerOutPath) {
    $lines = Get-Content $producerOutPath -ErrorAction SilentlyContinue
    if ($lines) {
        $idx = -1
        for ($i = $lines.Count - 1; $i -ge 0; $i--) {
            if ($lines[$i] -match "Producer Summary") { $idx = $i; break }
        }
        if ($idx -ge 0) {
            $endIdx = [Math]::Min($idx + 6, $lines.Count - 1)
            $producerSummary = ($lines[$idx..$endIdx] -join "`n").Trim()
        } else {
            $producerSummary = ($lines | Select-Object -Last 8) -join "`n"
        }
    }
}

$metricsReport = Get-SafeFileContent (Join-Path $rootDir "analytics_reports\metrics_report.txt")
$lagReport = Get-SafeFileContent (Join-Path $artifactsDir "lag_report.txt")
$beforeReport = Get-SafeFileContent (Join-Path $artifactsDir "metrics_report_before.txt") -MaxLines 30
$afterReport = Get-SafeFileContent (Join-Path $artifactsDir "metrics_report_after.txt") -MaxLines 30

# Consistency: compare before/after files if both exist
$replayIdentical = $false
$beforePath = Join-Path $artifactsDir "metrics_report_before.txt"
$afterPath = Join-Path $artifactsDir "metrics_report_after.txt"
if ((Test-Path $beforePath) -and (Test-Path $afterPath)) {
    try {
        $b = (Get-Content $beforePath -Raw -ErrorAction Stop).Trim()
        $a = (Get-Content $afterPath -Raw -ErrorAction Stop).Trim()
        $replayIdentical = ($b -eq $a)
    } catch { }
}

# ----- Build checkmarks and consistency block -----
$noProducer = $producerSummary -match "\(No producer"
$hasMetrics = $metricsReport -notmatch "not found|Could not read"
$hasLag = $lagReport -notmatch "not found|Could not read"
$hasBeforeAfter = $beforeReport -notmatch "not found" -and $afterReport -notmatch "not found"
$c1 = if ($noProducer) { "" } else { "x" }
$c2 = if ($hasMetrics) { "x" } else { "" }
$c3 = if ($hasLag) { "x" } else { "" }
$c4 = if ($hasBeforeAfter) { "x" } else { "" }
$c5 = if ($replayIdentical) { "x" } else { "" }
$consistencyBlock = if ($replayIdentical) {
  "- [x] Results are **identical** (replay produces consistent metrics).`n- [ ] Results **differ** - brief explanation"
} else {
  "- [ ] Results are **identical** (replay produces consistent metrics).`n- [x] Results **differ** - brief explanation"
}

# ----- Build and write report -----
$report = @"
# Part C: Streaming (Kafka) - Metrics & Evidence Report

**Generated:** $dateStr
**Purpose:** Evidence for Part C submission - 10k events, metrics report, consumer lag, replay.

---

## 1. Produce 10k Events

**Requirement:** Produce 10,000 events.

Producer Summary:

```
$producerSummary
```

---

## 2. Metrics Report (Orders per Minute & Failure Rate)

**Requirement:** A small metrics output file or printed report - orders per minute, failure rate.

Contents of metrics_report.txt after producing 10k events and waiting for analytics to process:

```
$metricsReport
```

---

## 3. Consumer Lag Under Throttling

**Requirement:** Show consumer lag under throttling.

Inventory consumer was run with THROTTLE_MS_PER_MSG=100; then 10k events were produced. Lag was captured with kafka-consumer-groups --describe --group inventory.

Contents of tests/artifacts/lag_report.txt:

```
$lagReport
```

---

## 4. Replay (Before and After)

**Requirement:** Demonstrate replay: reset consumer offset and recompute metrics. Evidence: before and after.

### Before replay

**File:** tests/artifacts/metrics_report_before.txt

```
$beforeReport
```

### After replay

**File:** tests/artifacts/metrics_report_after.txt

```
$afterReport
```

### Consistency

$consistencyBlock

---

## 5. Summary

| Check | Done |
|-------|------|
| 10k events produced | $c1 |
| Metrics report (orders/min, failure rate) | $c2 |
| Consumer lag under throttling | $c3 |
| Replay: offset reset + before/after evidence | $c4 |
| Replay produces consistent metrics (or explained) | $c5 |

**Notes:** (Optional - any extra details for the grader.)
"@

[System.IO.File]::WriteAllText($reportPath, $report, [System.Text.Encoding]::UTF8)

Write-Host "Report written to: $reportPath"
Write-Host "Open STREAMING_REPORT.md to review or submit."
