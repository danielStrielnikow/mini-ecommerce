# Mini eCommerce — Microservices System

A microservices-based eCommerce backend built with Java 21 and Spring Boot 3.4.

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                          Docker Network                          │
│                                                                  │
│  ┌─────────────┐   REST    ┌─────────────┐   REST   ┌─────────┐  │
│  │   Client    │──────────▶│order-service│─────────▶│inventory│  │
│  │             │           │  :8082      │          │ :8083   │  │
│  └─────────────┘           └──────┬──────┘          └────┬────┘  │
│                                   │                      │       │
│  ┌─────────────┐   REST           │                      │       │
│  │   Client    │──────────▶product-service               │       │
│  │             │            :8081                        │       │
│  └─────────────┘                  │                      │       │
│                                   ▼                      ▼       │
│                    ┌──────────────────────────────────┐          │
│                    │              Kafka               │          │
│                    │  order-created  │ order-expired  │          │
│                    │  order-cancelled│ product-created│          │
│                    │  stock-depleted │ stock-restored │          │
│                    │                 │ product-deleted│          │
│                    └──────────────────────────────────┘          │
│                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐  │
│  │  PostgreSQL  │   │    Redis     │   │   Kafka + Zookeeper  │  │
│  │  :5432       │   │    :6379     │   │       :9092          │  │
│  └──────────────┘   └──────────────┘   └──────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

## Quick Start

```bash
git clone https://github.com/danielStrielnikow/mini-ecommerce
cd mini-ecommerce/mini-ecommerce
docker compose up --build
```

| Service           | URL                                       |
|-------------------|-------------------------------------------|
| product-service   | http://localhost:8081/swagger-ui.html     |
| order-service     | http://localhost:8082/swagger-ui.html     |
| inventory-service | http://localhost:8083/swagger-ui.html     |

## API Endpoints

### product-service `:8081`

| Method | Path                          | Description                       |
|--------|-------------------------------|-----------------------------------|
| GET    | /api/products                 | List products (filter + pageable) |
| GET    | /api/products/{id}            | Get product by ID                 |
| POST   | /api/products                 | Create product                    |
| PUT    | /api/products/{id}            | Update product                    |
| PATCH  | /api/products/{id}/activate   | Activate product                  |
| PATCH  | /api/products/{id}/deactivate | Deactivate product                |
| DELETE | /api/products/{id}            | Soft delete                       |
| DELETE | /api/products/{id}/permanent  | Hard delete + notify inventory    |

### order-service `:8082`

| Method | Path                      | Description                          |
|--------|---------------------------|--------------------------------------|
| POST   | /api/orders               | Create order (rate limited: 10/min)  |
| GET    | /api/orders               | List orders (filter by status/product) |
| GET    | /api/orders/{id}          | Get order by ID                      |
| PATCH  | /api/orders/{id}/confirm  | Confirm order                        |
| PATCH  | /api/orders/{id}/cancel   | Cancel order + restore stock         |

### inventory-service `:8083`

| Method | Path                              | Description              |
|--------|-----------------------------------|--------------------------|
| GET    | /api/inventory/check              | Check availability       |
| GET    | /api/inventory                    | List inventory (filtered)|
| GET    | /api/inventory/{productId}        | Get by product ID        |
| POST   | /api/inventory                    | Create inventory record  |
| PATCH  | /api/inventory/{productId}/restock| Restock product          |
| DELETE | /api/inventory/{productId}        | Delete inventory record  |

## Typical Usage Flow

A complete happy-path scenario from product creation to order confirmation:

```bash
# 1. Create a product (inventory record is created automatically via Kafka)
curl -X POST http://localhost:8081/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": "Laptop Pro", "description": "High-end laptop", "price": 4999.99}'
# → returns { "id": "<PRODUCT_ID>", "status": "ACTIVE", ... }

# 2. Restock the product (inventory starts at 0)
curl -X PATCH http://localhost:8083/api/inventory/<PRODUCT_ID>/restock \
  -H "Content-Type: application/json" \
  -d '{"quantity": 10}'

# 3. Create an order (validates product is ACTIVE, checks stock, reserves for 15 min)
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "<PRODUCT_ID>", "quantity": 2}'
# → returns { "id": "<ORDER_ID>", "status": "CREATED", "reservedUntil": "...", "totalPrice": 9999.98 }

# 4. Confirm the order
curl -X PATCH http://localhost:8082/api/orders/<ORDER_ID>/confirm
# → returns { "status": "CONFIRMED", "confirmedAt": "...", ... }

# Alternative: cancel the order (Saga compensation — stock is restored via Kafka)
curl -X PATCH http://localhost:8082/api/orders/<ORDER_ID>/cancel
# → returns { "status": "CANCELLED", ... }
```

> All endpoints are also available via **Swagger UI** at `/swagger-ui.html` on each service port.

## Kafka Event Flow

```
POST /api/orders
  │
  ├─ [sync]  GET /api/products/{id}       → fetch price, validate ACTIVE
  ├─ [sync]  GET /api/inventory/check     → validate availability
  ├─ [save]  Order{status=CREATED, reservedUntil=now+15min, totalPrice=price×qty}
  └─ [async] → order-created
                    │
                    └─ inventory-service: decreaseStock()
                          ├─ OK → (done)
                          ├─ InsufficientStock → order-cancelled → order CANCELLED
                          └─ ConcurrentModification → order-cancelled → order CANCELLED

ReservationExpiryScheduler (every 60s)
  └─ finds CREATED orders where reservedUntil < now
        ├─ order → CANCELLED
        └─ order-expired → inventory-service: restoreStock()

POST /api/products  →  product-created  →  inventory-service: create(qty=0)
DELETE /api/products/{id}/permanent  →  product-deleted  →  inventory-service: delete()

inventory decreaseStock() hits 0  →  stock-depleted  →  product-service: deactivate()
inventory restockProduct()        →  stock-restored  →  product-service: activate()
```

## Design Decisions

### Saga Pattern (compensation on failure)
When `inventory-service` can't decrease stock (insufficient or concurrent modification), it publishes `OrderCancelledEvent` → `order-service` sets order to CANCELLED. Stock was never changed, so no rollback needed.

### Reservation Pattern (15-minute hold)
On order creation, `reservedUntil = now + 15 min` is stored. A scheduler running every 60 seconds finds expired CREATED orders, cancels them and publishes `OrderExpiredEvent` → inventory restores stock.
> **Known limitation:** In a multi-instance deployment the scheduler runs on every node. A distributed lock (e.g. ShedLock with PostgreSQL backend) would prevent duplicate processing.

### Optimistic Locking
`Inventory` has a `@Version Long version` field. If two threads simultaneously try to decrease the same stock, one will get `ObjectOptimisticLockingFailureException` — caught in `OrderCreatedConsumer` and treated as insufficient stock, resulting in order cancellation.

### Caching Strategy (product-service)
- `findById` → cached per ID with 10-minute TTL (Redis)
- `findAll` → **not cached** — filter combinations + pagination produce too many cache keys; safe to cache only by ID
- All write operations evict the affected product from cache by ID

### Circuit Breaker (order-service)
`InventoryClient` and `ProductClient` are wrapped with Resilience4j `@CircuitBreaker`. If either service is unavailable, the circuit opens after 50% failure rate over a 5-call window, and falls back gracefully (order creation refused with 404/503).

### Rate Limiting
`POST /api/orders` is limited to **10 requests per minute** per instance using Bucket4j (token bucket algorithm).

### Soft Delete (product-service)
Products have a `deleted_at` column and `@SQLRestriction("deleted_at IS NULL")` on the entity. Soft-deleted products are invisible to all queries without altering any query code. Hard delete (`DELETE /permanent`) physically removes the product and notifies inventory via Kafka.

## Observability & Logging

Every HTTP request gets an `X-Correlation-Id` header (generated if not provided by the caller).
The ID is injected into the MDC context and printed in **every log line** for the duration of that request — including logs from services, consumers, and schedulers that are triggered as a result.

**Log format:**
```
HH:mm:ss.SSS LEVEL [service-name] [correlation-id] logger - message
```

**Example — tracing a single order across services:**
```
# order-service
10:15:30.010 INFO  [order-service]     [abc-123] c.e.o.s.i.OrderServiceImpl     - Order created: orderId=...
10:15:30.015 INFO  [order-service]     [abc-123] c.e.o.c.RequestLoggingFilter    - POST /api/orders → 201 (47ms)

# inventory-service (Kafka consumer — no HTTP, no correlationId)
10:15:30.120 INFO  [inventory-service] [no-corr-id] c.e.i.c.OrderCreatedConsumer - Received OrderCreatedEvent: orderId=...
10:15:30.135 INFO  [inventory-service] [no-corr-id] c.e.i.s.i.InventoryServiceImpl - Stock decreased: productId=..., remaining=3
```

> Kafka consumers run outside of an HTTP thread, so they don't have a correlationId. You can correlate them manually via `orderId` or `productId` logged in every consumer message.

### Following logs with Docker Compose

```bash
# All services at once
docker compose logs -f

# Single service
docker compose logs -f order-service
docker compose logs -f product-service
docker compose logs -f inventory-service

# Trace a specific request across all services
docker compose logs -f | grep "abc-123"

# Show only errors and warnings
docker compose logs -f | grep -E "ERROR|WARN"
```

### Sending a custom Correlation ID (e.g. from Postman or curl)

```bash
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: my-trace-123" \
  -d '{"productId": "...", "quantity": 2}'
```

The same `my-trace-123` will appear in all logs generated by this request in `order-service`. The `X-Correlation-Id` header is also returned in the response.

## Running Tests

```bash
# Unit tests (all services)
./mvnw test

# Integration tests with Testcontainers (PostgreSQL, Kafka, Redis)
./mvnw verify -pl product-service
./mvnw verify -pl order-service
./mvnw verify -pl inventory-service

# End-to-end test (all 3 services + shared infrastructure)
./mvnw verify -pl e2e-tests
```

## Potential Future Improvements

| Area | Improvement |
|------|-------------|
| Distributed locking | ShedLock on `ReservationExpiryScheduler` to prevent duplicate processing when running multiple order-service instances |
| API Gateway | Single entry point (e.g. Spring Cloud Gateway) for routing, auth, and global rate limiting instead of per-service rate limiting |
| Authentication | JWT-based auth (Spring Security + OAuth2) — currently all endpoints are public |
| Outbox Pattern | Replace direct Kafka publishes with a transactional outbox table to guarantee at-least-once delivery even if Kafka is temporarily unavailable |
| Distributed tracing | `X-Correlation-Id` propagation is in place; full distributed tracing with OpenTelemetry + Jaeger/Zipkin would add automatic span propagation through Kafka consumers |
| Multi-item orders | Currently an order holds a single product. Supporting a cart (multiple `OrderItem` entries per order) would require a new `order_items` table, updating `OrderCreatedEvent` to carry a list, and extending the Saga compensation logic to handle partial stock failures across multiple products |
| Payment service | A dedicated `payment-service` that participates in the order saga (CREATED → PAID → CONFIRMED) |
| Service discovery | Eureka or Consul so services locate each other dynamically instead of hardcoded URLs |
| Dead Letter Queue | DLQ for failed Kafka consumers to avoid silent event loss on repeated processing errors |

## Technology Stack

| Technology          | Usage                              |
|---------------------|------------------------------------|
| Java 21             | Language                           |
| Spring Boot 3.4     | Application framework              |
| Spring Data JPA     | ORM + Specifications               |
| Flyway              | Database migrations                |
| PostgreSQL          | Primary database (prod)            |
| H2                  | In-memory database (dev/test)      |
| Apache Kafka        | Async event-driven communication   |
| Redis               | Distributed caching                |
| Resilience4j        | Circuit breaker                    |
| Bucket4j            | Rate limiting                      |
| MapStruct           | DTO mapping                        |
| Lombok              | Boilerplate reduction              |
| SpringDoc OpenAPI   | Swagger UI                         |
| Testcontainers      | Integration & E2E testing          |
| Docker Compose      | Local environment orchestration    |
