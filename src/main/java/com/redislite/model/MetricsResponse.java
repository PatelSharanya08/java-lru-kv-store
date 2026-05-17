package com.redislite.model;

/**
 * MetricsResponse – Payload returned by GET /metrics.
 *
 * hit ratio = hits / (hits + misses)  – useful for tuning cache capacity.
 */
public class MetricsResponse {

    private long hits;
    private long misses;
    private long evictions;
    private int  size;

    // ── Constructor ─────────────────────────────────────────────────────────
    public MetricsResponse(long hits, long misses, long evictions, int size) {
        this.hits      = hits;
        this.misses    = misses;
        this.evictions = evictions;
        this.size      = size;
    }

    // ── Getters ─────────────────────────────────────────────────────────────
    public long getHits()      { return hits; }
    public long getMisses()    { return misses; }
    public long getEvictions() { return evictions; }
    public int  getSize()      { return size; }
}
