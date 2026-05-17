package com.redislite;

import com.redislite.cache.LRUCache;
import com.redislite.metrics.CacheMetrics;
import com.redislite.publisher.CacheEventPublisher;
import com.redislite.publisher.CacheEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LRUCacheTest – Unit tests for core cache behaviour.
 *
 * No Spring context loaded here – pure unit tests for speed.
 */
class LRUCacheTest {

    private LRUCache      cache;
    private CacheMetrics  metrics;

    // Stub publisher that does nothing (we don't want log noise in tests)
    private final CacheEventPublisher noOpPublisher =
            (CacheEventType type, String key) -> {};

    @BeforeEach
    void setUp() {
        metrics = new CacheMetrics();
        cache   = new LRUCache(3, metrics, noOpPublisher);
    }

    // ─── Basic GET / PUT ────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT then GET returns the stored value")
    void putAndGet() {
        cache.put("k1", "v1", 0);
        assertEquals("v1", cache.get("k1"));
        assertEquals(1, metrics.getHits());
        assertEquals(0, metrics.getMisses());
    }

    @Test
    @DisplayName("GET on absent key returns null and counts miss")
    void getMissing() {
        assertNull(cache.get("ghost"));
        assertEquals(1, metrics.getMisses());
    }

    // ─── LRU Eviction ───────────────────────────────────────────────────────

    @Test
    @DisplayName("LRU entry is evicted when capacity is exceeded")
    void lruEviction() {
        cache.put("a", "1", 0);
        cache.put("b", "2", 0);
        cache.put("c", "3", 0);   // cache full: [c, b, a]

        // Access 'a' so 'b' becomes the LRU
        cache.get("a");           // order: [a, c, b]

        cache.put("d", "4", 0);   // should evict 'b' (LRU)

        assertNull(cache.get("b"),    "b should have been evicted");
        assertNotNull(cache.get("a"), "a should still be present");
        assertNotNull(cache.get("d"), "d should be present");
        assertEquals(1, metrics.getEvictions());
    }

    // ─── DELETE ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE removes the key")
    void delete() {
        cache.put("x", "y", 0);
        assertTrue(cache.delete("x"));
        assertNull(cache.get("x"));
        assertFalse(cache.delete("x"));   // second delete: key gone
    }

    // ─── TTL ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Expired entry is not returned on GET")
    void ttlExpiry() throws InterruptedException {
        cache.put("temp", "data", 1);    // 1-second TTL
        assertEquals("data", cache.get("temp"));

        Thread.sleep(1100);              // wait for expiry

        assertNull(cache.get("temp"), "Expired key should return null");
    }

    @Test
    @DisplayName("evictExpiredEntries removes stale keys")
    void ttlSweep() throws InterruptedException {
        cache.put("short", "lived", 1);
        assertEquals(1, cache.size());

        Thread.sleep(1100);
        cache.evictExpiredEntries();

        assertEquals(0, cache.size());
    }

    // ─── Size ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("size() reflects current live entry count")
    void sizeTracking() {
        assertEquals(0, cache.size());
        cache.put("one",   "1", 0);
        cache.put("two",   "2", 0);
        assertEquals(2, cache.size());
        cache.delete("one");
        assertEquals(1, cache.size());
    }
}
