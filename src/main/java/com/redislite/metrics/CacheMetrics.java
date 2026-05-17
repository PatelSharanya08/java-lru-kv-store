package com.redislite.metrics;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * CacheMetrics – Thread-safe counters for observability.
 *
 * Uses AtomicLong so increments don't need explicit synchronization –
 * they piggyback on the CPU's compare-and-swap (CAS) instruction.
 *
 * Exposed via GET /metrics endpoint.
 */
@Component
public class CacheMetrics {

    private final AtomicLong hits      = new AtomicLong(0);
    private final AtomicLong misses    = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    public void incrementHits()      { hits.incrementAndGet(); }
    public void incrementMisses()    { misses.incrementAndGet(); }
    public void incrementEvictions() { evictions.incrementAndGet(); }

    public long getHits()      { return hits.get(); }
    public long getMisses()    { return misses.get(); }
    public long getEvictions() { return evictions.get(); }
}
