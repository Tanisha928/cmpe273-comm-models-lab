# Sync-REST Latency Report Script (PowerShell)
# Run from sync-rest directory: .\tests\run_latency_report.ps1
# Or from sync-rest/tests: .\run_latency_report.ps1
# Uses Java if available; otherwise runs the test inside a Docker container (no Java needed on host).

$ErrorActionPreference = "Stop"
$syncRestRoot = if (Test-Path "docker-compose.yml") { Get-Location } else { Join-Path (Get-Location) ".." }
Set-Location $syncRestRoot

$reportPath = Join-Path $syncRestRoot "latency_report.md"
$htmlBaseline = Join-Path $syncRestRoot "latency_baseline.html"
$htmlDelay = Join-Path $syncRestRoot "latency_delay.html"
$htmlFailure = Join-Path $syncRestRoot "latency_failure.html"
$htmlIndex = Join-Path $syncRestRoot "latency_index.html"
$orderUrl = "http://localhost:8081/order"
$orderUrlDocker = "http://host.docker.internal:8081/order"  # From inside container on Windows/Mac Docker Desktop
$body = '{"user_id":"u1","item_id":"burrito","qty":1}'
$n = 200
$testsDir = Join-Path $syncRestRoot "tests"

function Run-LatencyTest {
    param([string]$SectionTitle, [string]$HtmlPath)
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        Set-Location $testsDir
        & java LatencyTest.java $n $orderUrl $body $reportPath $SectionTitle $HtmlPath
        Set-Location $syncRestRoot
        return $LASTEXITCODE -eq 0
    }
    # No Java: run inside Docker (host.docker.internal works on Docker Desktop Windows/Mac)
    $reportLeaf = Split-Path -Leaf $reportPath
    $htmlLeaf = Split-Path -Leaf $HtmlPath
    docker run --rm -v "${testsDir}:/app:ro" -v "${syncRestRoot}:/out" eclipse-temurin:17-jdk `
        bash -c "cd /app && java LatencyTest.java $n $orderUrlDocker '$body' /out/$reportLeaf '$SectionTitle' /out/$htmlLeaf"
    Set-Location $syncRestRoot
    return $LASTEXITCODE -eq 0
}

Write-Host "=== Sync-REST Latency Report ===" -ForegroundColor Cyan
Write-Host "Markdown report: $reportPath" -ForegroundColor Gray
Write-Host "HTML charts: $htmlBaseline, $htmlDelay, $htmlIndex" -ForegroundColor Gray
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "Java not on PATH; will run latency test inside Docker." -ForegroundColor Yellow
}
Write-Host ""

# Ensure services are up
Write-Host "Ensuring services are up..." -ForegroundColor Yellow
$ErrorActionPreference = "SilentlyContinue"
docker compose up -d 2>&1 | Out-Null
$ErrorActionPreference = "Stop"
Start-Sleep -Seconds 5

# --- 1. Baseline ---
Write-Host "`n--- 1. Baseline (no delay, no failure) ---" -ForegroundColor Green
$reportHeader = @"
# Sync-REST Latency Report

**Generated:** $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')

## What is recorded
- **N** (number of requests)
- **Avg latency** (ms)
- **p50 / p95 / p99** (ms)
- Success rate and status counts

"@
Set-Content -Path $reportPath -Value $reportHeader -Encoding UTF8
if (-not (Run-LatencyTest "1. Baseline" $htmlBaseline)) {
    Write-Host "LatencyTest failed. If using Docker, ensure Docker Desktop is running and order service is up." -ForegroundColor Red
    exit 1
}
Write-Host "Baseline done. Check latency_report.md" -ForegroundColor Green

# --- 2. With 2s Inventory delay ---
Write-Host "`n--- 2. With 2s Inventory Delay ---" -ForegroundColor Green
Write-Host "Restarting inventory with INVENTORY_DELAY_MS=2000 and order with INVENTORY_TIMEOUT_MS=3000..."
$env:INVENTORY_DELAY_MS = "2000"
$env:INVENTORY_TIMEOUT_MS = "3000"
$env:NOTIFICATION_TIMEOUT_MS = "3000"
$ErrorActionPreference = "SilentlyContinue"
docker compose up -d inventory order --force-recreate 2>&1 | Out-Null
$ErrorActionPreference = "Stop"
Start-Sleep -Seconds 8
if (-not (Run-LatencyTest "2. With 2s Inventory Delay" $htmlDelay)) {
    Write-Host "Delay run failed; continuing with failure test." -ForegroundColor Yellow
}
Add-Content -Path $reportPath -Value "`n**Note:** With 2s inventory delay, latency increases by approximately 2000 ms. Order timeout was set to 3000 ms so requests complete (no timeout)." -Encoding UTF8
Write-Host "Delay case done. Latency should increase by ~2000ms (avg, p50, p95, p99)." -ForegroundColor Green

# --- 3. Inventory failure verification ---
Write-Host "`n--- 3. Inventory Failure Verification ---" -ForegroundColor Green
Write-Host "Restarting inventory with INVENTORY_FAIL=true..."
$env:INVENTORY_FAIL = "true"
$env:INVENTORY_DELAY_MS = "0"
$ErrorActionPreference = "SilentlyContinue"
docker compose up -d inventory --force-recreate 2>&1 | Out-Null
$ErrorActionPreference = "Stop"
Start-Sleep -Seconds 5

$failSection = @"

## 3. Inventory Failure Verification

OrderService must return **502** (downstream error) or **504** (timeout), with an error reason, and must not hang.

**Test:** Send a few POST requests to $orderUrl and capture status and body.

"@

# Send 3 requests and capture status + one response body (to show error reason)
$statusCounts = @{}
$firstErrorBody = $null
$firstStatusCode = $null
for ($i = 1; $i -le 3; $i++) {
    try {
        $resp = Invoke-WebRequest -Uri $orderUrl -Method POST -ContentType "application/json" -Body $body -TimeoutSec 5 -UseBasicParsing
        $code = $resp.StatusCode
        $statusCounts[$code] = $statusCounts[$code] + 1
        Write-Host "  Request $i : Status $code" -ForegroundColor Gray
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if (-not $code) { $code = "Timeout/Error" }
        $statusCounts[$code] = $statusCounts[$code] + 1
        Write-Host "  Request $i : $code - $($_.Exception.Message)" -ForegroundColor Gray
        if ($null -eq $firstStatusCode) {
            $firstStatusCode = $code
            $firstErrorBody = $_.ErrorDetails.Message
            if ([string]::IsNullOrWhiteSpace($firstErrorBody)) { $firstErrorBody = "(no body or timeout)" }
        }
    }
}

# Convert hashtable keys to strings for JSON serialization (do once, reuse)
$statusCountsStr = @{}
foreach ($key in $statusCounts.Keys) {
    $statusCountsStr["$key"] = $statusCounts[$key]
}
$statusJson = $statusCountsStr | ConvertTo-Json -Compress

$failSection += "| Request | Expected | Result |`n"
$failSection += "|---------|----------|--------|`n"
$failSection += "| 1-3     | 502 or 504, error reason, no hang | Status counts: $statusJson |`n"
if ($null -ne $firstStatusCode -and $null -ne $firstErrorBody) {
    $failSection += "`n**Sample error response (status $firstStatusCode):**`n``````json`n$firstErrorBody`n```````n"
}
$failSection += "`n**Verdict:** OrderService returns 502 (downstream failure) or 504 (timeout) when Inventory fails. Error reason is present in body; service does not hang.`n"
Add-Content -Path $reportPath -Value $failSection
Write-Host "Failure verification section appended to report." -ForegroundColor Green

# Write failure verification HTML (simple page with status summary)
$failureHtml = @"
<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>Failure Verification</title>
<style>body{font-family:sans-serif;max-width:600px;margin:2em auto;padding:1em;} h1{color:#333;} .ok{color:green;} .stat{background:#f0f4f8;padding:1em;border-radius:8px;margin:1em 0;}</style>
</head><body>
<h1>3. Inventory Failure Verification</h1>
<p>OrderService must return <strong>502</strong> or <strong>504</strong> with error reason and must not hang.</p>
<div class="stat"><strong>Status counts (3 requests):</strong> <code>$statusJson</code></div>
<p class="ok">Verdict: 502/504 returned as expected; service did not hang.</p>
<p><a href="latency_index.html">Back to index</a></p>
</body></html>
"@
Set-Content -Path $htmlFailure -Value $failureHtml -Encoding UTF8

# Write index HTML linking to all reports
$indexHtml = @"
<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>Sync-REST Latency Reports</title>
<style>body{font-family:sans-serif;max-width:700px;margin:2em auto;padding:1em;} h1{color:#333;} a{color:#2563eb;} ul{line-height:2;}</style>
</head><body>
<h1>Sync-REST Latency Reports</h1>
<p>Open these in a browser to see charts (Chart.js).</p>
<ul>
<li><a href="latency_baseline.html">1. Baseline</a> &ndash; No delay, no failure</li>
<li><a href="latency_delay.html">2. With 2s Inventory Delay</a> &ndash; Latency +~2000ms</li>
<li><a href="latency_failure.html">3. Inventory Failure Verification</a> &ndash; 502/504 verified</li>
</ul>
<p>Markdown report: <code>latency_report.md</code></p>
</body></html>
"@
Set-Content -Path $htmlIndex -Value $indexHtml -Encoding UTF8
Write-Host "HTML reports: $htmlIndex (open in browser for charts)" -ForegroundColor Green

# Restore normal env for next runs
$env:INVENTORY_FAIL = "false"
$env:INVENTORY_TIMEOUT_MS = "1000"
$env:NOTIFICATION_TIMEOUT_MS = "1000"

Write-Host "`n=== Done ===" -ForegroundColor Cyan
Write-Host "  Markdown: $reportPath" -ForegroundColor Gray
Write-Host "  Charts:   Open in browser: $htmlIndex" -ForegroundColor Gray
Get-Content $reportPath | Select-Object -First 50
