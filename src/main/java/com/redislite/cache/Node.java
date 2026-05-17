package com.redislite.cache;

/**
 * Node – A single element in our Doubly Linked List.
 *
 * Each node holds:
 *  - key        : the cache key (used to remove it from the HashMap in O(1))
 *  - value      : the stored data
 *  - expiresAt  : epoch-millisecond timestamp after which this entry is stale
 *                 (Long.MAX_VALUE means "never expires")
 *  - prev/next  : pointers that wire it into the doubly-linked list
 *
 * Why store the key inside the node?
 *   When LRU eviction pops the tail node we need to delete its HashMap entry.
 *   Without the key we'd have to scan the whole map – O(n).  With it we get O(1).
 */
public class Node {

    String key;
    String value;
    long   expiresAt;   // System.currentTimeMillis() + ttlMs, or Long.MAX_VALUE

    Node prev;
    Node next;

    /** Constructor used when inserting a new cache entry. */
    Node(String key, String value, long expiresAt) {
        this.key       = key;
        this.value     = value;
        this.expiresAt = expiresAt;
    }

    /** Convenience: is this node still alive right now? */
    boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
