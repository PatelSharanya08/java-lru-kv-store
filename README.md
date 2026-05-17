# RedisLite – Distributed In-Memory Cache with LRU Eviction

A production-style Spring Boot project that demonstrates:

- Custom O(1) LRU cache using **HashMap + Doubly Linked List**
- **TTL-based expiry** with a background cleanup scheduler
- **Thread-safe** reads and writes using `ReentrantReadWriteLock`
- **REST API** for cache operations and live metrics
- **Distributed simulation** via an event-publishing abstraction

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [How LRU Works](#how-lru-works)
4. [Thread Safety Design](#thread-safety-design)
5. [TTL & Expiry](#ttl--expiry)
6. [Distributed Simulation](#distributed-simulation)
7. [REST API Reference](#rest-api-reference)
8. [Running the Project](#running-the-project)
9. [Configuration](#configuration)
10. [Running Tests](#running-tests)

---

## Architecture Overview

```
HTTP Request
     │
     ▼
┌─────────────────┐
│ CacheController │  ← REST layer: parses HTTP, returns status codes
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  CacheService   │  ← Orchestration: delegates + runs @Scheduled TTL sweep
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌──────────────────────┐
│    LRUCache     │────▶│  CacheEventPublisher │  ← logs eviction/expiry events
└────────┬────────┘     └──────────────────────┘
         │
    ┌────┴────┐
    ▼         ▼
HashMap   DoublyLinkedList
(O(1)      (access-order
 lookup)    tracking)
```

---

## Project Structure

```
src/main/java/com/redislite/
├── RedisLiteApplication.java          # Spring Boot entry point
│
├── cache/
│   ├── Node.java                      # DLL node: key, value, expiresAt, prev/next
│   ├── DoublyLinkedList.java          # O(1) add-to-front, remove, remove-last
│   └── LRUCache.java                  # HashMap + DLL + ReentrantReadWriteLock
│
├── config/
│   └── CacheConfig.java               # Wires LRUCache bean with properties
│
├── controller/
│   └── CacheController.java           # POST /cache  GET /cache/{key}  DELETE  GET /metrics
│
├── exception/
│   ├── KeyNotFoundException.java      # 404 when key not found
│   └── GlobalExceptionHandler.java    # Maps exceptions → JSON error responses
│
├── metrics/
│   └── CacheMetrics.java              # AtomicLong counters: hits, misses, evictions
│
├── model/
│   ├── CacheRequest.java              # Request body for POST /cache
│   ├── CacheResponse.java             # Response body for GET /cache/{key}
│   └── MetricsResponse.java           # Response body for GET /metrics
│
├── publisher/
│   ├── CacheEventType.java            # Enum: KEY_UPDATED, KEY_EVICTED, KEY_EXPIRED
│   ├── CacheEventPublisher.java       # Interface for event delivery
│   └── LocalCacheEventPublisher.java  # Implementation: logs to SLF4J
│
└── service/
    └── CacheService.java              # Delegates to LRUCache; owns @Scheduled sweep
```

---

## How LRU Works

### The Two Data Structures

| Structure           | Role                              | Complexity |
|---------------------|-----------------------------------|-----------|
| `HashMap<String, Node>` | Key → Node pointer lookup     | O(1)      |
| `DoublyLinkedList`  | Tracks recency (MRU ↔ LRU order) | O(1)      |

### Layout

```
HEAD ↔ [most recently used] ↔ … ↔ [least recently used] ↔ TAIL
```

### Operations

**GET(key)**
1. Look up node in HashMap → O(1)
2. Move node to front of DLL (mark as most-recent) → O(1)
3. Return value

**PUT(key, value)**
1. If key exists → update value, move to front → O(1)
2. If cache full → remove DLL tail (LRU), delete from HashMap → O(1)
3. Insert new node at DLL front, add to HashMap → O(1)

**DELETE(key)**
1. Remove from HashMap → O(1)
2. Unlink node from DLL → O(1)

### Why store the key inside Node?

When we evict the LRU tail we have the `Node` object but need to delete its entry from the `HashMap`. Without the key stored on the node we'd need an O(n) scan. With `node.key` it's a direct O(1) `map.remove(node.key)`.

---

## Thread Safety Design

```
ReentrantReadWriteLock
│
├── Read Lock (GET)
│     Multiple threads can hold simultaneously → high read throughput
│
└── Write Lock (PUT / DELETE / TTL sweep)
      Exclusive – blocks all readers and writers → data integrity
```

`CacheMetrics` uses `AtomicLong` for its counters, which rely on hardware CAS (compare-and-swap) rather than locks – faster for pure increment operations.

---

## TTL & Expiry

Each `Node` stores an `expiresAt` epoch-millisecond timestamp.

```
expiresAt = System.currentTimeMillis() + ttlSeconds * 1000
```

### Two-pronged expiry

| Mechanism     | When it fires         | Description                                          |
|---------------|-----------------------|------------------------------------------------------|
| **Lazy**      | On every `GET`        | Expired nodes are silently treated as misses         |
| **Proactive** | Every N seconds       | `@Scheduled` job calls `evictExpiredEntries()` which sweeps the entire map under write lock |

The scheduled interval is set in `application.properties`:

```properties
redislite.cache.ttl-cleanup-interval-seconds=5
```

---

## Distributed Simulation

`CacheEventPublisher` is an interface. `LocalCacheEventPublisher` logs each event:

```
[EVENT] KEY_EVICTED  | key='user1' | Evicted by LRU policy. Peer nodes should drop this key.
[EVENT] KEY_EXPIRED  | key='session42' | TTL elapsed. Peer nodes should purge this key.
[EVENT] KEY_UPDATED  | key='user1' | Peer nodes should invalidate/refresh this key.
```

In a real distributed system these events would be published to:

- **Kafka / Redis Pub/Sub** → peer cache nodes subscribe and invalidate their local copies
- **HTTP fan-out** → each node in the cluster receives an invalidation call

The pattern is called **write-invalidate cache coherence**. Swapping `LocalCacheEventPublisher` for a Kafka implementation requires zero changes to `LRUCache` or `CacheService`.

---

## REST API Reference

### POST /cache – Store a key

```bash
curl -X POST http://localhost:8080/cache \
  -H "Content-Type: application/json" \
  -d '{"key":"user1","value":"Sharanya","ttlSeconds":60}'
```

**Response 201 Created**
```json
{ "message": "Key stored successfully.", "key": "user1" }
```

Omit `ttlSeconds` (or set to `0`) for a non-expiring entry.

---

### GET /cache/{key} – Retrieve a value

```bash
curl http://localhost:8080/cache/user1
```

**Response 200 OK (hit)**
```json
{ "key": "user1", "value": "Sharanya", "message": "Cache HIT" }
```

**Response 404 Not Found (miss / expired)**
```json
{
  "timestamp": "2025-01-01T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Key not found: 'user1'"
}
```

---

### DELETE /cache/{key} – Remove a key

```bash
curl -X DELETE http://localhost:8080/cache/user1
```

**Response 200 OK**
```json
{ "message": "Key deleted successfully.", "key": "user1" }
```

---

### GET /metrics – Live cache stats

```bash
curl http://localhost:8080/metrics
```

**Response 200 OK**
```json
{
  "hits":      20,
  "misses":     5,
  "evictions":  2,
  "size":      10
}
```

| Field       | Meaning                                             |
|-------------|-----------------------------------------------------|
| `hits`      | Successful lookups (key found and not expired)      |
| `misses`    | Failed lookups (key absent or expired)              |
| `evictions` | Keys removed by LRU policy (capacity exceeded)      |
| `size`      | Current number of entries (may include expired ones not yet swept) |

Hit ratio = `hits / (hits + misses)` – a healthy cache should be > 80 %.

---

## Running the Project

### Prerequisites

- Java 17+
- Maven 3.8+

### Start the server

```bash
cd redislite
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

### Quick smoke test

```bash
# Store two keys
curl -X POST http://localhost:8080/cache \
  -H "Content-Type: application/json" \
  -d '{"key":"name","value":"Sharanya","ttlSeconds":120}'

curl -X POST http://localhost:8080/cache \
  -H "Content-Type: application/json" \
  -d '{"key":"city","value":"Bangalore"}'

# Retrieve
curl http://localhost:8080/cache/name

# Metrics
curl http://localhost:8080/metrics

# Delete
curl -X DELETE http://localhost:8080/cache/name
```

---

## Configuration

All tuneable settings live in `src/main/resources/application.properties`:

```properties
# HTTP port
server.port=8080

# Max keys before LRU eviction
redislite.cache.capacity=100

# Background TTL sweep interval (seconds)
redislite.cache.ttl-cleanup-interval-seconds=5
```

---

## Running Tests

```bash
mvn test
```

Tests cover:
- PUT → GET round trip
- Cache miss counting
- LRU eviction order
- DELETE correctness
- TTL lazy expiry (GET after TTL elapses)
- TTL proactive sweep (`evictExpiredEntries`)
- Size tracking

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| `HashMap + DoublyLinkedList` | Achieves O(1) for all cache operations – same internals as Java's `LinkedHashMap` |
| `ReentrantReadWriteLock` | Allows concurrent reads while serialising writes – better throughput than `synchronized` |
| `AtomicLong` for metrics | Lock-free counter increments using CPU CAS |
| Sentinel nodes in DLL | Eliminates null-pointer edge cases for head/tail operations |
| `CacheEventPublisher` interface | Decouples cache core from delivery mechanism – swap log for Kafka without touching LRUCache |
| `@Scheduled` TTL sweep | Proactive cleanup prevents unbounded memory growth from expired entries |
| Lazy TTL check on GET | Zero overhead for non-expiring entries; expired entries fail fast |
