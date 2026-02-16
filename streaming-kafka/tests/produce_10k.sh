#!/bin/bash

set -e

echo "=========================================="
echo "Producing 10,000 events"
echo "=========================================="

cd "$(dirname "$0")/.."

# Start services if not running
docker compose up -d kafka-init

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
sleep 10

# Run producer with 10k events
docker compose run --rm -e EVENTS=10000 producer_order

echo ""
echo "=========================================="
echo "Production complete!"
echo "=========================================="
