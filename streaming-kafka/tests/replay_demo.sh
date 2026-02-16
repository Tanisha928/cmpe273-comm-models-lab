#!/bin/bash

set -e

echo "=========================================="
echo "Replay Demo - Testing Idempotency"
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

# Produce some events first
echo "Producing initial events..."
docker compose run --rm -e EVENTS=1000 producer_order

# Wait for processing
echo "Waiting for initial processing..."
sleep 10

# Run analytics and save first report
echo "Running analytics (first run)..."
docker compose run --rm analytics_consumer timeout 30s || true
sleep 2

if [ -f metrics_report.txt ]; then
    cp metrics_report.txt tests/artifacts/metrics_report_before.txt
    echo "Saved metrics_report_before.txt"
else
    echo "Warning: metrics_report.txt not found"
fi

# Reset offsets for analytics group
echo ""
echo "Resetting offsets for analytics group to earliest..."
docker compose exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group analytics \
    --reset-offsets \
    --to-earliest \
    --execute \
    --topic order_events || true

docker compose exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group analytics \
    --reset-offsets \
    --to-earliest \
    --execute \
    --topic inventory_events || true

echo "Offsets reset. Waiting 5 seconds..."
sleep 5

# Run analytics again
echo ""
echo "Running analytics (replay)..."
docker compose run --rm analytics_consumer timeout 30s || true
sleep 2

if [ -f metrics_report.txt ]; then
    cp metrics_report.txt tests/artifacts/metrics_report_after.txt
    echo "Saved metrics_report_after.txt"
else
    echo "Warning: metrics_report.txt not found"
fi

# Compare results
echo ""
echo "=========================================="
echo "Comparing results..."
echo "=========================================="

if [ -f tests/artifacts/metrics_report_before.txt ] && [ -f tests/artifacts/metrics_report_after.txt ]; then
    echo "BEFORE (first run):"
    echo "-------------------"
    head -20 tests/artifacts/metrics_report_before.txt
    
    echo ""
    echo "AFTER (replay):"
    echo "-------------------"
    head -20 tests/artifacts/metrics_report_after.txt
    
    echo ""
    if diff -q tests/artifacts/metrics_report_before.txt tests/artifacts/metrics_report_after.txt > /dev/null; then
        echo "✓ Results are IDENTICAL - Replay works correctly!"
    else
        echo "⚠ Results differ - check the files for details"
    fi
else
    echo "Warning: Could not compare - files missing"
fi

echo ""
echo "=========================================="
echo "Replay demo complete!"
echo "=========================================="
