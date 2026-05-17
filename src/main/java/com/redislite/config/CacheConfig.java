package com.redislite.config;

import com.redislite.cache.LRUCache;
import com.redislite.metrics.CacheMetrics;
import com.redislite.publisher.CacheEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CacheConfig – Spring wires the LRUCache bean here.
 *
 * Keeping the bean definition in a @Configuration class (rather than
 * annotating LRUCache itself) means we can supply constructor arguments
 * read from application.properties, and we keep the cache core free of
 * Spring annotations (better testability).
 */
@Configuration
public class CacheConfig {

    /**
     * Maximum number of entries before LRU eviction triggers.
     * Configured via redislite.cache.capacity in application.properties.
     */
    @Value("${redislite.cache.capacity:100}")
    private int capacity;

    /**
     * Build the singleton LRUCache, injecting metrics and the event publisher.
     */
    @Bean
    public LRUCache lruCache(CacheMetrics metrics, CacheEventPublisher publisher) {
        return new LRUCache(capacity, metrics, publisher);
    }
}
