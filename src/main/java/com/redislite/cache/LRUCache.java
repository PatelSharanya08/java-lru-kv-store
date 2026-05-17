package com.redislite.cache;

import com.redislite.metrics.CacheMetrics;
import com.redislite.publisher.CacheEventPublisher;
import com.redislite.publisher.CacheEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRUCache – The heart of RedisLite.
 *
 * Data structures:
 *   HashMap<String, Node>  →  O(1) key lookup
 *   DoublyLinkedList       →  O(1) move-to-front and remove-from-tail
 *
 * Thread safety:
 *   ReentrantReadWriteLock allows multiple concurrent readers (GET)
 *   but exclusive access for writers (PUT / DELETE / eviction).
 *
 * TTL:
 *   Each node stores an expiresAt timestamp.  Expired nodes are lazily
 *   rejected on GET and proactively swept by the scheduler in CacheService.
 */
public class LRUCache {

    private static final Logger log = LoggerFactory.getLogger(LRUCache.class);

    private final int capacity;
    private final Map<String, Node> map;
    private final DoublyLinkedList  list;
    private final CacheMetrics      metrics;
    private final CacheEventPublisher publisher;

    // Read-write lock: many readers OR one writer at a time
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock  readLock  = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public LRUCache(int capacity, CacheMetrics metrics, CacheEventPublisher publisher) {
        this.capacity  = capacity;
        this.metrics   = metrics;
        this.publisher = publisher;
        this.map  = new HashMap<>(capacity);
        this.list = new DoublyLinkedList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieve a value.
     *
     * Steps:
     *  1. Acquire READ lock – safe for concurrent reads.
     *  2. Look up node in HashMap.
     *  3. If absent or expired → cache miss.
     *  4. Upgrade to WRITE lock to move node to front (marking it most-recent).
     *  5. Return value.
     */
    public String get(String key) {
        readLock.lock();
        try {
            Node node = map.get(key);

            // Cache miss: key doesn't exist
            if (node == null) {
                metrics.incrementMisses();
                log.debug("MISS  key={}", key);
                return null;
            }

            // Lazy TTL check: treat expired entries as misses
            if (node.isExpired()) {
                metrics.incrementMisses();
                log.debug("EXPIRED key={}", key);
                // We don't delete here (inside a read lock); the scheduler handles it
                return null;
            }

            metrics.incrementHits();
            log.debug("HIT   key={}", key);

            // Must upgrade to write lock to mutate the linked list
            readLock.unlock();
            writeLock.lock();
            try {
                // Re-check: another thread may have evicted while we re-locked
                if (map.containsKey(key)) {
                    list.remove(node);
                    list.addToFront(node);
                }
                return node.value;
            } finally {
                readLock.lock();   // re-acquire read lock before finally block releases it
                writeLock.unlock();
            }

        } finally {
            readLock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Insert or update a key.
     *
     * Steps:
     *  1. Acquire WRITE lock (exclusive).
     *  2. If key exists → update value/TTL and move to front.
     *  3. If new key and at capacity → evict LRU tail first.
     *  4. Create new node, add to map and list front.
     *
     * @param ttlSeconds 0 or negative means "no expiry"
     */
    public void put(String key, String value, long ttlSeconds) {
        writeLock.lock();
        try {
            long expiresAt = (ttlSeconds > 0)
                    ? System.currentTimeMillis() + ttlSeconds * 1000L
                    : Long.MAX_VALUE;

            // UPDATE existing entry
            if (map.containsKey(key)) {
                Node existing = map.get(key);
                existing.value     = value;
                existing.expiresAt = expiresAt;
                list.remove(existing);
                list.addToFront(existing);
                publisher.publish(CacheEventType.KEY_UPDATED, key);
                log.info("UPDATE key={}", key);
                return;
            }

            // EVICT if at capacity
            if (map.size() >= capacity) {
                Node lru = list.removeLast();
                if (lru != null) {
                    map.remove(lru.key);
                    metrics.incrementEvictions();
                    publisher.publish(CacheEventType.KEY_EVICTED, lru.key);
                    log.info("EVICT  key={} (LRU)", lru.key);
                }
            }

            // INSERT new node
            Node newNode = new Node(key, value, expiresAt);
            map.put(key, newNode);
            list.addToFront(newNode);
            log.info("PUT    key={} ttlSeconds={}", key, ttlSeconds > 0 ? ttlSeconds : "∞");

        } finally {
            writeLock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Explicitly remove a key.
     *
     * @return true if the key existed and was removed, false otherwise.
     */
    public boolean delete(String key) {
        writeLock.lock();
        try {
            Node node = map.remove(key);
            if (node == null) return false;
            list.remove(node);
            log.info("DELETE key={}", key);
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TTL CLEANUP (called by scheduler in CacheService)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sweep through all entries and remove those whose TTL has elapsed.
     * Called periodically by a ScheduledExecutorService.
     */
    public void evictExpiredEntries() {
        writeLock.lock();
        try {
            // Collect expired keys first to avoid ConcurrentModificationException
            var expiredKeys = map.entrySet().stream()
                    .filter(e -> e.getValue().isExpired())
                    .map(Map.Entry::getKey)
                    .toList();

            for (String key : expiredKeys) {
                Node node = map.remove(key);
                if (node != null) {
                    list.remove(node);
                    publisher.publish(CacheEventType.KEY_EXPIRED, key);
                    log.info("TTL-EXPIRED key={}", key);
                }
            }

            if (!expiredKeys.isEmpty()) {
                log.info("TTL sweep removed {} expired key(s)", expiredKeys.size());
            }
        } finally {
            writeLock.unlock();
        }
    }

    /** Current number of live (not-yet-swept) entries in the cache. */
    public int size() {
        readLock.lock();
        try {
            return map.size();
        } finally {
            readLock.unlock();
        }
    }
}
