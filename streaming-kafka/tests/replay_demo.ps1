# Replay Demo - reset analytics offset to earliest, recompute metrics, compare before/after
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
Set-Location $rootDir

Write-Host "=========================================="
Write-Host "Replay Demo - Testing Idempotency"
Write-Host "=========================================="

$artifactsDir = Join-Path $scriptDir "artifacts"
if (-not (Test-Path $artifactsDir)) { New-Item -ItemType Directory -Path $artifactsDir -Force | Out-Null }

Write-Host "Starting services..."
docker compose up -d

Write-Host "Waiting for services to initialize..."
Start-Sleep -Seconds 15

Write-Host "Producing initial events..."
docker compose run --rm -e EVENTS=1000 producer_order

Write-Host "Waiting for initial processing..."
Start-Sleep -Seconds 10

# Run analytics for ~30s then stop (captures first pass)
Write-Host "Running analytics (first run) for 30 seconds..."
$job = Start-Job -ScriptBlock {
    Set-Location $using:rootDir
    docker compose run --rm analytics_consumer
}
Start-Sleep -Seconds 30
Stop-Job $job -ErrorAction SilentlyContinue
Remove-Job $job -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

$metricsFile = Join-Path $rootDir "metrics_report.txt"
$beforeFile = Join-Path $artifactsDir "metrics_report_before.txt"
$beforeContent = $null
if (Test-Path $metricsFile) {
    for ($r = 0; $r -lt 3; $r++) {
        try { $beforeContent = Get-Content $metricsFile -Raw -ErrorAction Stop; break } catch { Start-Sleep -Seconds 2 }
    }
    if ($beforeContent) {
        [System.IO.File]::WriteAllText($beforeFile, $beforeContent)
        Write-Host "Saved metrics_report_before.txt"
    } else {
        Copy-Item -Path $metricsFile -Destination $beforeFile -Force -ErrorAction SilentlyContinue
        Write-Host "Saved metrics_report_before.txt (open manually if needed)"
    }
} else {
    Write-Host "Warning: metrics_report.txt not found"
}

Write-Host ""
Write-Host "Resetting offsets for analytics group to earliest..."
docker compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group analytics --reset-offsets --to-earliest --execute --topic order_events 2>$null
docker compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group analytics --reset-offsets --to-earliest --execute --topic inventory_events 2>$null

Write-Host "Offsets reset. Waiting 5 seconds..."
Start-Sleep -Seconds 5

Write-Host ""
Write-Host "Running analytics (replay) for 30 seconds..."
$job2 = Start-Job -ScriptBlock {
    Set-Location $using:rootDir
    docker compose run --rm analytics_consumer
}
Start-Sleep -Seconds 30
Stop-Job $job2 -ErrorAction SilentlyContinue
Remove-Job $job2 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

$afterFile = Join-Path $artifactsDir "metrics_report_after.txt"
$afterContent = $null
if (Test-Path $metricsFile) {
    for ($r = 0; $r -lt 3; $r++) {
        try { $afterContent = Get-Content $metricsFile -Raw -ErrorAction Stop; break } catch { Start-Sleep -Seconds 2 }
    }
    if ($afterContent) {
        [System.IO.File]::WriteAllText($afterFile, $afterContent)
        Write-Host "Saved metrics_report_after.txt"
    } else {
        Copy-Item -Path $metricsFile -Destination $afterFile -Force -ErrorAction SilentlyContinue
        Write-Host "Saved metrics_report_after.txt (open manually if needed)"
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
