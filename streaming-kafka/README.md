# Kafka Streaming Implementation (Part C)

This module implements a Kafka-based event streaming system for order processing, inventory management, and analytics.

## Architecture

```
Producer → order_events topic → InventoryConsumer → inventory_events topic
                                              ↓
                                    AnalyticsConsumer (consumes both topics)
```

## Components

### 1. Producer Order (`producer_order`)
- Produces `OrderPlaced` events to `order_events` topic
- Configurable number of events (default: 1000, supports up to 10,000)
- Spreads timestamps across 10 minutes for realistic analytics
- Supports rate limiting via `RATE_PER_SEC` environment variable

### 2. Inventory Consumer (`inventory_consumer`)
- Consumes `order_events` from Kafka
- Maintains in-memory inventory (burrito=5000, pizza=5000)
- Reserves stock or emits `InventoryFailed` events
- Publishes results to `inventory_events` topic
- Implements idempotency (tracks processed orders)
- Supports throttling via `THROTTLE_MS_PER_MSG` for lag demonstration

### 3. Analytics Consumer (`analytics_consumer`)
- Consumes both `order_events` and `inventory_events`
- Computes metrics based on event time (not wall clock):
  - Orders per minute
  - Failure rate
- Writes metrics report to `metrics_report.txt`
- Ensures replay produces identical results using event-time bucketing

## Topics

- **order_events**: 3 partitions, replication factor 1
- **inventory_events**: 3 partitions, replication factor 1

## Event Schemas

### OrderPlaced (order_events)
```json
{
  "type": "OrderPlaced",
  "event_id": "uuid",
  "order_id": "string",
  "user_id": "string",
  "item_id": "string",
  "qty": 10,
  "created_at": "2026-02-16T18:00:00Z"
}
```

### InventoryReserved / InventoryFailed (inventory_events)
```json
{
  "type": "InventoryReserved" | "InventoryFailed",
  "event_id": "uuid",
  "order_id": "string",
  "item_id": "string",
  "qty": 10,
  "reason": "string if failed",
  "created_at": "copied from order",
  "processed_at": "2026-02-16T18:00:05Z"
}
```

## Quick Start

### 1. Build and Start All Services

```bash
cd streaming-kafka
docker compose up --build
```

This will:
- Start Zookeeper and Kafka broker
- Create topics automatically
- Start producer, inventory consumer, and analytics consumer

### 2. Produce 10,000 Events

```bash
# Option 1: Using test script
chmod +x tests/produce_10k.sh
./tests/produce_10k.sh

# Option 2: Using docker compose directly
docker compose run --rm -e EVENTS=10000 producer_order
```

### 3. View Metrics Report

The analytics consumer continuously writes metrics to `metrics_report.txt`:

```bash
cat metrics_report.txt
```

Example output:
```
========================================
KAFKA STREAMING ANALYTICS REPORT
Generated: 2026-02-16T18:30:00Z
========================================

OVERALL METRICS:
  Total Orders: 10000
  Total Failed: 250
  Failure Rate: 2.50%

ORDERS PER MINUTE (Event Time):
  Minute Bucket                    | Orders | Failed | Failure Rate
  ----------------------------------|--------|--------|-------------
  2026-02-16T17:50:00Z              |   1000 |     25 |       2.50%
  2026-02-16T17:51:00Z              |   1000 |     25 |       2.50%
  ...
```

## Test Scripts

### Produce 10K Events

```bash
chmod +x tests/produce_10k.sh
./tests/produce_10k.sh
```

### Consumer Lag Demo

Demonstrates consumer lag by:
1. Starting inventory consumer with throttling (100ms per message)
2. Producing 10,000 events
3. Checking lag using Kafka CLI

```bash
chmod +x tests/lag_demo.sh
./tests/lag_demo.sh
```

Output saved to `tests/artifacts/lag_report.txt`

### Replay Demo

Tests idempotency and replay consistency:
1. Produces events and runs analytics (saves `metrics_report_before.txt`)
2. Resets consumer offsets to earliest
3. Replays events and runs analytics again (saves `metrics_report_after.txt`)
4. Compares results (should be identical)

```bash
chmod +x tests/replay_demo.sh
./tests/replay_demo.sh
```

## Environment Variables

### Producer Order
- `BOOTSTRAP_SERVERS`: Kafka broker address (default: `kafka:29092`)
- `EVENTS`: Number of events to produce (default: `1000`)
- `RATE_PER_SEC`: Optional rate limit (events per second)

### Inventory Consumer
- `BOOTSTRAP_SERVERS`: Kafka broker address (default: `kafka:29092`)
- `CONSUMER_GROUP`: Consumer group name (default: `inventory`)
- `THROTTLE_MS_PER_MSG`: Delay per message in milliseconds (default: `0`)

### Analytics Consumer
- `BOOTSTRAP_SERVERS`: Kafka broker address (default: `kafka:29092`)
- `CONSUMER_GROUP`: Consumer group name (default: `analytics`)

## Docker Commands

### Start Services
```bash
docker compose up -d
```

### View Logs
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f producer_order
docker compose logs -f inventory_consumer
docker compose logs -f analytics_consumer
```

### Stop Services
```bash
docker compose down
```

### Check Kafka Topics
```bash
docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Check Consumer Groups
```bash
docker compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
```

### Check Consumer Lag
```bash
docker compose exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group inventory \
    --describe
```

## Output Files

- `metrics_report.txt`: Analytics metrics report (updated every 10 seconds)
- `tests/artifacts/lag_report.txt`: Consumer lag report from lag demo
- `tests/artifacts/metrics_report_before.txt`: Metrics before replay
- `tests/artifacts/metrics_report_after.txt`: Metrics after replay

## Logging

All services log structured JSON:

```json
{
  "timestamp": "2026-02-16T18:00:00Z",
  "service": "producer_order",
  "level": "INFO",
  "message": "Produced event",
  "event_id": "uuid",
  "order_id": "uuid",
  "topic": "order_events",
  "partition": 0,
  "offset": 12345
}
```

## Troubleshooting

### Kafka not ready
Wait a few seconds after starting services:
```bash
sleep 10
```

### Topics not created
Check kafka-init logs:
```bash
docker compose logs kafka-init
```

### Consumer not processing
Check consumer logs:
```bash
docker compose logs inventory_consumer
```

### Reset consumer offsets manually
```bash
docker compose exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group analytics \
    --reset-offsets \
    --to-earliest \
    --execute \
    --topic order_events
```

## Architecture Notes

- **Idempotency**: Inventory consumer tracks processed orders in `/app/processed_orders/orders.txt`
- **Event Time Processing**: Analytics uses `created_at` from events, not wall clock time
- **Replay Consistency**: Metrics are computed using event-time bucketing, ensuring identical results on replay
- **Consumer Groups**: 
  - `inventory`: Processes order_events
  - `analytics`: Processes both order_events and inventory_events
