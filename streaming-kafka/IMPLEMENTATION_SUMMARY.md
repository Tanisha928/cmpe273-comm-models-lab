# Kafka Streaming Implementation - Complete Summary

## Directory Structure

```
streaming-kafka/
├── docker-compose.yml
├── README.md
├── metrics_report.txt
│
├── producer_order/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── .dockerignore
│   └── src/main/java/com/cmpe273/producer/
│       └── ProducerOrder.java
│
├── inventory_consumer/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── .dockerignore
│   └── src/main/java/com/cmpe273/inventory/
│       └── InventoryConsumer.java
│
├── analytics_consumer/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── .dockerignore
│   └── src/main/java/com/cmpe273/analytics/
│       └── AnalyticsConsumer.java
│
└── tests/
    ├── produce_10k.sh
    ├── lag_demo.sh
    ├── replay_demo.sh
    └── artifacts/
        └── .gitkeep
```

## Quick Start Commands

### 1. Start the System

```bash
cd streaming-kafka
docker compose up --build
```

This will:
- Start Zookeeper and Kafka
- Create topics (order_events, inventory_events)
- Start all three services

### 2. Produce 10,000 Events

```bash
# Using script (Linux/Mac/Git Bash)
chmod +x tests/produce_10k.sh
./tests/produce_10k.sh

# Or directly with docker compose
docker compose run --rm -e EVENTS=10000 producer_order
```

### 3. View Metrics Report

```bash
cat metrics_report.txt
```

### 4. Run Lag Demo

```bash
chmod +x tests/lag_demo.sh
./tests/lag_demo.sh
```

### 5. Run Replay Demo

```bash
chmod +x tests/replay_demo.sh
./tests/replay_demo.sh
```

## Docker Commands Reference

### View Running Containers
```bash
docker compose ps
```

### View Logs
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f producer_order
docker compose logs -f inventory_consumer
docker compose logs -f analytics_consumer
docker compose logs -f kafka
```

### Stop Services
```bash
docker compose down
```

### Check Kafka Topics
```bash
docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
docker compose exec kafka kafka-topics --describe --bootstrap-server localhost:9092 --topic order_events
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

### Reset Consumer Offsets
```bash
docker compose exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group analytics \
    --reset-offsets \
    --to-earliest \
    --execute \
    --topic order_events

docker compose exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group analytics \
    --reset-offsets \
    --to-earliest \
    --execute \
    --topic inventory_events
```

## Example Outputs

### metrics_report.txt Example
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

### lag_report.txt Example
```
GROUP           TOPIC           PARTITION  CURRENT-OFFSET  LAG
inventory       order_events    0          5000           5000
inventory       order_events    1          5000           5000
inventory       order_events    2          5000           5000
```

## Testing Checklist

- [x] Producer creates OrderPlaced events
- [x] Inventory consumer processes orders and creates inventory events
- [x] Analytics consumer computes metrics from both topics
- [x] Metrics report is generated and updated
- [x] Idempotency works (duplicate orders are skipped)
- [x] Replay produces identical results
- [x] Consumer lag can be measured
- [x] All services log structured JSON

## Architecture Features

1. **Event Time Processing**: Analytics uses `created_at` from events, ensuring replay consistency
2. **Idempotency**: Inventory consumer tracks processed orders to prevent double-processing
3. **Throttling Support**: Inventory consumer can be slowed down for lag demonstration
4. **Structured Logging**: All services output JSON logs with consistent format
5. **Docker Compose Integration**: Everything runs in containers, no manual setup needed
