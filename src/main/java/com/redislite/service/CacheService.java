package com.redislite.service;

import com.redislite.cache.LRUCache;
import com.redislite.metrics.CacheMetrics;
import com.redislite.model.MetricsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * CacheService – Application-layer facade over LRUCache.
 *
 * Responsibilities:
 *  1. Delegate PUT / GET / DELETE to the underlying LRUCache.
 *  2. Run the periodic TTL cleanup job via @Scheduled.
 *  3. Expose aggregated metrics to the controller.
 *
 * Why a service layer?
 *   The controller should only care about HTTP concerns (request parsing,
 *   status codes).  Business logic – including scheduling – lives here.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final LRUCache     cache;
    private final CacheMetrics metrics;

    public CacheService(LRUCache cache, CacheMetrics metrics) {
        this.cache   = cache;
        this.metrics = metrics;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core operations – thin delegation
    // ─────────────────────────────────────────────────────────────────────────

    public void put(String key, String value, long ttlSeconds) {
        cache.put(key, value, ttlSeconds);
    }

    /**
     * @return the cached value, or null on miss / expiry.
     */
    public String get(String key) {
        return cache.get(key);
    }

    /**
     * @return true if the key existed and was removed.
     */
    public boolean delete(String key) {
        return cache.delete(key);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TTL background sweep
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scheduled TTL cleanup – runs every N seconds (configured in properties).
     *
     * @Scheduled(fixedRateString = "…") reads from application.properties so
     * the interval can be tuned without recompiling.
     *
     * ScheduledExecutorService is managed by Spring; @EnableScheduling in
     * RedisLiteApplication activates it.
     */
    @Scheduled(fixedRateString =
            "${redislite.cache.ttl-cleanup-interval-seconds:5}000")
    public void cleanUpExpiredEntries() {
        log.debug("TTL sweep started …");
        cache.evictExpiredEntries();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metrics
    // ─────────────────────────────────────────────────────────────────────────

    public MetricsResponse getMetrics() {
        return new MetricsResponse(
                metrics.getHits(),
                metrics.getMisses(),
                metrics.getEvictions(),
                cache.size()
        );
    }
}
