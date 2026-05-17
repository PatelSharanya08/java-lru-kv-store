package com.redislite.publisher;

/**
 * CacheEventPublisher – Abstraction for broadcasting cache lifecycle events.
 *
 * Why an interface?
 *   It decouples the cache core from the delivery mechanism.  Today we log;
 *   tomorrow the impl could push to Kafka, Redis Pub/Sub, or a WebSocket feed
 *   without touching a single line of LRUCache.
 *
 * In a real distributed system this would fanout to peer nodes so they can
 * invalidate their local copies of the changed key – this is called
 * "cache invalidation via event streaming."
 */
public interface CacheEventPublisher {

    /**
     * Publish a cache lifecycle event.
     *
     * @param type  what happened (updated / evicted / expired)
     * @param key   the affected cache key
     */
    void publish(CacheEventType type, String key);
}
