# Replay Demo - reset analytics offset to earliest, recompute metrics, compare before/after
#
# Usage: .\replay_demo.ps1           # Use existing Kafka data
#        .\replay_demo.ps1 -Fresh    # Fresh Kafka + inventory = zero failure run
#
# Flow: 1) (if -Fresh) Reset Kafka volumes and processed_orders for zero-failure run
#       2) Save current metrics (BEFORE)
#       3) Stop analytics consumer, reset offsets, restart
#       4) Wait 90s for reprocessing
#       5) Save metrics (AFTER)
param([switch]$Fresh)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
Set-Location $rootDir

Write-Host "=========================================="
Write-Host "Replay Demo - Testing Idempotency"
Write-Host "=========================================="

$artifactsDir = Join-Path $scriptDir "artifacts"
if (-not (Test-Path $artifactsDir)) { New-Item -ItemType Directory -Path $artifactsDir -Force | Out-Null }

# Metrics are written to analytics_reports/metrics_report.txt (directory mount)
$reportsDir = Join-Path $rootDir "analytics_reports"
if (-not (Test-Path $reportsDir)) { New-Item -ItemType Directory -Path $reportsDir -Force | Out-Null }

if ($Fresh) {
    Write-Host "Fresh mode: resetting Kafka and inventory for zero-failure run..."
    $ErrorActionPreference = "SilentlyContinue"
    docker compose down -v 2>$null | Out-Null
    $procOrders = Join-Path $rootDir "inventory_consumer\processed_orders\orders.txt"
    if (Test-Path $procOrders) { Remove-Item $procOrders -Force }
    $ErrorActionPreference = "Stop"
    Start-Sleep -Seconds 3
}

Write-Host "Starting services..."
$ErrorActionPreference = "SilentlyContinue"
docker compose up -d | Out-Null
$ErrorActionPreference = "Stop"

Write-Host "Waiting for services to initialize..."
Start-Sleep -Seconds 15

Write-Host "Producing initial events..."
$ErrorActionPreference = "SilentlyContinue"
docker compose run --rm -e EVENTS=1000 producer_order
$ErrorActionPreference = "Stop"

# Use the long-running analytics consumer (already up). Wait for it to process and write to analytics_reports/metrics_report.txt.
Write-Host "Waiting for analytics to process (20s)..."
Start-Sleep -Seconds 20

$metricsFile = Join-Path $rootDir "analytics_reports\metrics_report.txt"
$beforeFile = Join-Path $artifactsDir "metrics_report_before.txt"
$beforeContent = $null
if (Test-Path $metricsFile) {
    for ($r = 0; $r -lt 5; $r++) {
        try {
            $beforeContent = Get-Content $metricsFile -Raw -ErrorAction Stop
            if ($beforeContent -and $beforeContent.Trim().Length -gt 10) { break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if ($beforeContent -and $beforeContent.Trim().Length -gt 10) {
        [System.IO.File]::WriteAllText($beforeFile, $beforeContent)
        Write-Host "Saved metrics_report_before.txt"
    } else {
        Copy-Item -Path $metricsFile -Destination $beforeFile -Force -ErrorAction SilentlyContinue
        Write-Host "Saved metrics_report_before.txt (content may be empty if analytics has not written yet)"
    }
} else {
    Write-Host "Warning: metrics_report.txt not found"
}

Write-Host ""
Write-Host "Stopping analytics consumer so we can reset offsets..."
$ErrorActionPreference = "SilentlyContinue"
docker compose stop analytics_consumer | Out-Null
$ErrorActionPreference = "Stop"
Start-Sleep -Seconds 3

Write-Host "Resetting offsets for analytics group to earliest..."
$ErrorActionPreference = "SilentlyContinue"
docker compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group analytics --reset-offsets --to-earliest --execute --topic order_events 2>$null | Out-Null
docker compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group analytics --reset-offsets --to-earliest --execute --topic inventory_events 2>$null | Out-Null
$ErrorActionPreference = "Stop"

Write-Host "Starting analytics consumer for replay..."
$ErrorActionPreference = "SilentlyContinue"
docker compose up -d analytics_consumer | Out-Null
$ErrorActionPreference = "Stop"

# Wait for consumer to reprocess from earliest and write again.
# With 20k+ events (from produce_10k + lag_demo), reprocessing takes 45-90s. Use 90s to ensure completion.
$replayWaitSeconds = 90
Write-Host "Waiting for replay processing ($replayWaitSeconds s)..."
Start-Sleep -Seconds $replayWaitSeconds

$afterFile = Join-Path $artifactsDir "metrics_report_after.txt"
$afterContent = $null
if (Test-Path $metricsFile) {
    for ($r = 0; $r -lt 5; $r++) {
        try {
            $afterContent = Get-Content $metricsFile -Raw -ErrorAction Stop
            if ($afterContent -and $afterContent.Trim().Length -gt 10) { break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if ($afterContent -and $afterContent.Trim().Length -gt 10) {
        [System.IO.File]::WriteAllText($afterFile, $afterContent)
        Write-Host "Saved metrics_report_after.txt"
    } else {
        Copy-Item -Path $metricsFile -Destination $afterFile -Force -ErrorAction SilentlyContinue
        Write-Host "Saved metrics_report_after.txt (content may be empty if analytics has not written yet)"
    }
} else {
    Write-Host "Warning: metrics_report.txt not found"
}

Write-Host ""
Write-Host "=========================================="
Write-Host "Comparing results..."
Write-Host "=========================================="

# Display and compare (use in-memory content; artifact files are for submission)
Write-Host "BEFORE (first run):"
Write-Host "-------------------"
if ($beforeContent) {
    ($beforeContent -split "`n" | Select-Object -First 25) | Write-Host
} else {
    Write-Host "(See $beforeFile)"
}

Write-Host ""
Write-Host "AFTER (replay):"
Write-Host "-------------------"
if ($afterContent) {
    ($afterContent -split "`n" | Select-Object -First 25) | Write-Host
} else {
    Write-Host "(See $afterFile)"
}

Write-Host ""
try {
    if ($beforeContent -and $afterContent) {
        $beforeHash = [System.BitConverter]::ToString([System.Security.Cryptography.MD5]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes($beforeContent))).Replace("-","")
        $afterHash = [System.BitConverter]::ToString([System.Security.Cryptography.MD5]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes($afterContent))).Replace("-","")
        if ($beforeHash -eq $afterHash) {
            Write-Host "Results are IDENTICAL - Replay works correctly!"
        } else {
            Write-Host "Results differ - check the files for details"
        }
    } else {
        Write-Host "Compare files manually: $beforeFile vs $afterFile"
    }
} catch {
    Write-Host "Compare files manually: $beforeFile vs $afterFile"
}

Write-Host ""
Write-Host "=========================================="
Write-Host "Replay demo complete!"
Write-Host "=========================================="
