# Consumer Lag Demo - throttle inventory consumer, produce 10k, show lag
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
Set-Location $rootDir

Write-Host "=========================================="
Write-Host "Consumer Lag Demo"
Write-Host "=========================================="

$artifactsDir = Join-Path $scriptDir "artifacts"
if (-not (Test-Path $artifactsDir)) { New-Item -ItemType Directory -Path $artifactsDir -Force | Out-Null }

Write-Host "Starting services..."
$ErrorActionPreference = "SilentlyContinue"
docker compose up -d | Out-Null
$ErrorActionPreference = "Stop"

Write-Host "Waiting for services to initialize..."
Start-Sleep -Seconds 15

# Stop inventory consumer, then start with throttling
Write-Host "Stopping inventory consumer..."
$ErrorActionPreference = "SilentlyContinue"
docker compose stop inventory_consumer | Out-Null
$ErrorActionPreference = "Stop"

Write-Host "Starting inventory consumer with THROTTLE_MS_PER_MSG=100..."
$env:THROTTLE_MS_PER_MSG = "100"
$ErrorActionPreference = "SilentlyContinue"
docker compose up -d inventory_consumer | Out-Null
$ErrorActionPreference = "Stop"

Write-Host "Waiting for consumer to start..."
Start-Sleep -Seconds 5

Write-Host "Producing 10,000 events..."
$ErrorActionPreference = "SilentlyContinue"
docker compose run --rm -e EVENTS=10000 producer_order
$ErrorActionPreference = "Stop"

Write-Host "Waiting 30 seconds for processing..."
Start-Sleep -Seconds 30

Write-Host ""
Write-Host "Checking consumer lag for group 'inventory'..."
$lagReport = Join-Path $artifactsDir "lag_report.txt"
docker compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group inventory --describe 2>&1 | Set-Content -Path $lagReport

Write-Host ""
Write-Host "=========================================="
Write-Host "Lag Report saved to tests/artifacts/lag_report.txt"
Write-Host "=========================================="
Get-Content $lagReport
