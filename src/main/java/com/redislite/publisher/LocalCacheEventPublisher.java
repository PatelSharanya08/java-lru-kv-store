package com.redislite.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LocalCacheEventPublisher – Log-based implementation of CacheEventPublisher.
 *
 * In production you would swap this bean for one that pushes to Kafka,
 * Redis Streams, or an HTTP fan-out service.  The rest of the codebase
 * never needs to change because it only knows about the interface.
 *
 * Simulated distributed behaviour:
 *   Each log line represents a message that would travel over the network to
 *   peer cache nodes.  Those nodes would receive the event and either:
 *     - KEY_UPDATED  → re-fetch the value from the source-of-truth DB
 *     - KEY_EVICTED  → drop the key from their local cache maps
 *     - KEY_EXPIRED  → do the same TTL cleanup on their side
 *
 * This pattern is known as "write-invalidate" cache coherence.
 */
@Component
public class LocalCacheEventPublisher implements CacheEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalCacheEventPublisher.class);

    @Override
    public void publish(CacheEventType type, String key) {
        switch (type) {
            case KEY_UPDATED ->
                log.info("[EVENT] KEY_UPDATED  | key='{}' | Peer nodes should invalidate/refresh this key.", key);
            case KEY_EVICTED ->
                log.info("[EVENT] KEY_EVICTED  | key='{}' | Evicted by LRU policy. Peer nodes should drop this key.", key);
            case KEY_EXPIRED ->
                log.info("[EVENT] KEY_EXPIRED  | key='{}' | TTL elapsed. Peer nodes should purge this key.", key);
        }
    }
}
