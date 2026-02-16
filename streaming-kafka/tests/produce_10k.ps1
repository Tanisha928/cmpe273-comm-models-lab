# Produce 10,000 events for Part C testing
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
Set-Location $rootDir

Write-Host "=========================================="
Write-Host "Producing 10,000 events"
Write-Host "=========================================="

# Ensure Kafka init has run (start dependencies)
$ErrorActionPreference = "SilentlyContinue"
docker compose up -d zookeeper kafka | Out-Null
$ErrorActionPreference = "Stop"
Write-Host "Waiting for Kafka to be ready..."
Start-Sleep -Seconds 10
$ErrorActionPreference = "SilentlyContinue"
docker compose up -d kafka-init | Out-Null
$ErrorActionPreference = "Stop"
Start-Sleep -Seconds 5

# Run producer with 10k events
docker compose run --rm -e EVENTS=10000 producer_order

Write-Host ""
Write-Host "=========================================="
Write-Host "Production complete!"
Write-Host "=========================================="
