package com.redislite.publisher;

/**
 * CacheEventType – Enumeration of lifecycle events a cache entry can emit.
 *
 * In a real distributed cache (e.g. Redis Cluster, Hazelcast) these events
 * would be broadcast over the network so that other nodes can invalidate or
 * refresh their local state.  Here we simply log them, demonstrating the
 * pattern without the networking complexity.
 */
public enum CacheEventType {

    /** A key was inserted or its value / TTL was changed. */
    KEY_UPDATED,

    /** A key was removed because the cache reached capacity (LRU policy). */
    KEY_EVICTED,

    /** A key's time-to-live elapsed and it was swept by the background job. */
    KEY_EXPIRED
}
