#!/bin/bash

set -e

echo "=========================================="
echo "Consumer Lag Demo"
echo "=========================================="

cd "$(dirname "$0")/.."

# Create artifacts directory
mkdir -p tests/artifacts

# Start services
echo "Starting services..."
docker compose up -d

# Wait for services to be ready
echo "Waiting for services to initialize..."
sleep 15

# Stop existing inventory consumer if running
docker compose stop inventory_consumer || true

# Start inventory consumer with throttling (100ms per message)
echo "Starting inventory consumer with THROTTLE_MS_PER_MSG=100..."
THROTTLE_MS_PER_MSG=100 docker compose up -d inventory_consumer

# Wait a bit for consumer to start
sleep 5

# Produce 10k events
echo "Producing 10,000 events..."
docker compose run --rm -e EVENTS=10000 producer_order

# Wait for some processing
echo "Waiting 30 seconds for processing..."
sleep 30

# Check consumer lag
echo ""
echo "Checking consumer lag for group 'inventory'..."
docker compose exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group inventory \
    --describe > tests/artifacts/lag_report.txt 2>&1 || true

echo ""
echo "=========================================="
echo "Lag Report saved to tests/artifacts/lag_report.txt"
echo "=========================================="
cat tests/artifacts/lag_report.txt
