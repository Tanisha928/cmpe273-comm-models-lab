# Part B – Async Communication using RabbitMQ

## Overview

This project implements the Campus Food Ordering workflow using asynchronous messaging with RabbitMQ.

Architecture:

Client → OrderService → RabbitMQ (Broker) → InventoryService → NotificationService

The system is event-driven, resilient to service downtime, and supports idempotent message processing and dead-letter queue handling.

---

# System Components

## OrderService
- REST API: `POST /order`
- Stores orders in local H2 database
- Publishes `OrderPlaced` event to RabbitMQ

## InventoryService
- Consumes `OrderPlaced`
- Reserves stock (idempotent logic)
- Publishes:
  - `InventoryReserved`
  - `InventoryFailed`
- Routes malformed messages to Dead Letter Queue (DLQ)

## NotificationService
- Consumes `InventoryReserved`
- Logs confirmation message

## RabbitMQ (Broker)
- Manages exchanges and queues
- Stores backlog when consumers are down
- Handles Dead Letter Queues

---

# Exchanges

- `orders`
- `orders.dlx`
- `inventory`

# Queues

- `q.order_placed`
- `q.order_placed.dlq`
- `q.inventory_reserved`

---

# How to Run

From the `async-rabbitmq` directory:

```bash
docker compose up -d
docker compose ps
```

RabbitMQ UI:

```
http://localhost:15672
username: guest
password: guest
```

---

# End-to-End Workflow

1. Client sends:
   ```bash
   POST http://localhost:8100/order
   ```

2. OrderService:
   - Saves order locally
   - Publishes `OrderPlaced`

3. InventoryService:
   - Consumes `OrderPlaced`
   - Reserves stock
   - Publishes `InventoryReserved`

4. NotificationService:
   - Consumes `InventoryReserved`
   - Logs confirmation

---

# Complete Testing Steps (All Requirements)

---

## 1. Basic End-to-End Verification

Send a test order:

```bash
curl -X POST http://localhost:8100/order \
  -H "Content-Type: application/json" \
  -d '{"user_id":"u1","item_id":"burger","quantity":1}'
```

Expected response:

```json
{"accepted":true,"order_id":"..."}
```

Verify Inventory logs:

```bash
docker compose logs --tail=30 inventory_service
```

Expected:
```
[inventory] order=... type=InventoryReserved reason=reserved
```

Verify Notification logs:

```bash
docker compose logs --tail=30 notification_service
```

Expected:
```
[notification] CONFIRM order=... user=u1 reason=reserved
```

Screenshot: Notification confirmation log.

---

## 2. Backlog and Recovery Test (Required)

Requirement:
Kill InventoryService for 60 seconds, keep publishing orders, restart and show backlog drain.

### Step 1: Stop InventoryService

```bash
docker compose stop inventory_service
```

### Step 2: Publish Multiple Orders

```bash
for i in $(seq 1 80); do
  curl -s -X POST http://localhost:8100/order \
    -H "Content-Type: application/json" \
    -d "{\"order_id\":\"backlog-$i\",\"user_id\":\"u1\",\"item_id\":\"burger\",\"quantity\":1}" >/dev/null
done
```

### Step 3: Observe Backlog

Open RabbitMQ UI → Queues → `q.order_placed`

Expected:
- Ready > 0

Screenshot: Backlog built (Ready count high).

### Step 4: Restart InventoryService

```bash
docker compose up -d inventory_service
```

Refresh queue page.

Expected:
- Ready count decreases to 0

Screenshot: Backlog drained (Ready = 0).

---

## 3. Idempotency Test (Required)

Requirement:
Re-deliver the same OrderPlaced message twice and ensure no double reservation.

Send same order twice:

```bash
curl -X POST http://localhost:8100/order \
  -H "Content-Type: application/json" \
  -d '{"order_id":"dup-1","user_id":"u1","item_id":"burger","quantity":1}'

curl -X POST http://localhost:8100/order \
  -H "Content-Type: application/json" \
  -d '{"order_id":"dup-1","user_id":"u1","item_id":"burger","quantity":1}'
```

Check Inventory logs:

```bash
docker compose logs --tail=80 inventory_service
```

Expected:

```
[inventory] order=dup-1 type=InventoryReserved reason=reserved
[inventory] order=dup-1 type=InventoryReserved reason=idempotent
```

Screenshot: Idempotency proof.

---

## 4. Poison Message / DLQ Test (Required)

Requirement:
Show DLQ handling for malformed event.

Send malformed JSON:

```bash
curl -u guest:guest -H "content-type:application/json" -XPOST \
  http://localhost:15672/api/exchanges/%2f/orders/publish \
  -d '{"properties":{},"routing_key":"OrderPlaced","payload":"{\"type\":\"OrderPlaced\",\"order_id\":\"bad-1\",","payload_encoding":"string"}'
```

Check Inventory logs:

```bash
docker compose logs --tail=30 inventory_service
```

Expected:
```
[inventory] POISON message -> DLQ
```

Check DLQ:

RabbitMQ UI → Queues → `q.order_placed.dlq`

Expected:
- Ready > 0

Screenshot: DLQ Ready count > 0.

---

# Idempotency Strategy

InventoryService ensures idempotency using a database table `reservations` where `order_id` is the primary key.

Before reserving stock:
- If `order_id` already exists → treat as idempotent and skip reservation.
- If not → reserve stock and insert record.

This guarantees duplicate message delivery does not cause double reservation.

---

# Evidence Checklist

- Backlog built screenshot (Ready > 0)
- Backlog drained screenshot (Ready = 0)
- Idempotency log screenshot
- DLQ queue screenshot
- Notification confirmation screenshot

---

# Conclusion

This implementation satisfies all Part B requirements:

- Asynchronous messaging
- Durable queue behavior
- Backlog recovery
- Idempotent processing
- Dead Letter Queue handling
- End-to-end confirmation logging
