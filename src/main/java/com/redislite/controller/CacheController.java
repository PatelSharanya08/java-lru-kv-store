package com.redislite.controller;

import com.redislite.exception.KeyNotFoundException;
import com.redislite.model.CacheRequest;
import com.redislite.model.CacheResponse;
import com.redislite.model.MetricsResponse;
import com.redislite.service.CacheService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CacheController – HTTP entry point for RedisLite.
 *
 * Endpoints:
 *   POST   /cache            → store a key-value pair (optional TTL)
 *   GET    /cache/{key}      → retrieve a value
 *   DELETE /cache/{key}      → remove a key
 *   GET    /metrics          → cache stats (hits, misses, evictions, size)
 *
 * The controller intentionally contains no business logic; it only:
 *   1. Validates the request at a surface level.
 *   2. Delegates to CacheService.
 *   3. Maps results to HTTP status codes.
 */
@RestController
@RequestMapping   // No base path; paths defined on each method
public class CacheController {

    private final CacheService cacheService;

    public CacheController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /cache
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Store a key-value pair.
     *
     * Request body example:
     * {
     *   "key":        "user1",
     *   "value":      "Sharanya",
     *   "ttlSeconds": 60
     * }
     *
     * Returns 201 Created on success.
     */
    @PostMapping("/cache")
    public ResponseEntity<Map<String, String>> put(@RequestBody CacheRequest request) {
        validateRequest(request);
        cacheService.put(request.getKey(), request.getValue(), request.getTtlSeconds());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Key stored successfully.",
                        "key",     request.getKey()
                ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /cache/{key}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieve a value by key.
     *
     * Returns 200 + CacheResponse on hit.
     * Returns 404 if key is absent or expired.
     */
    @GetMapping("/cache/{key}")
    public ResponseEntity<CacheResponse> get(@PathVariable String key) {
        String value = cacheService.get(key);
        if (value == null) {
            throw new KeyNotFoundException(key);
        }
        return ResponseEntity.ok(CacheResponse.hit(key, value));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /cache/{key}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Remove a key explicitly.
     *
     * Returns 200 on success, 404 if the key didn't exist.
     */
    @DeleteMapping("/cache/{key}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String key) {
        boolean removed = cacheService.delete(key);
        if (!removed) {
            throw new KeyNotFoundException(key);
        }
        return ResponseEntity.ok(Map.of(
                "message", "Key deleted successfully.",
                "key",     key
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /metrics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Return live cache statistics.
     *
     * Example response:
     * {
     *   "hits":      20,
     *   "misses":     5,
     *   "evictions":  2,
     *   "size":      10
     * }
     */
    @GetMapping("/metrics")
    public ResponseEntity<MetricsResponse> metrics() {
        return ResponseEntity.ok(cacheService.getMetrics());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation helper
    // ─────────────────────────────────────────────────────────────────────────

    private void validateRequest(CacheRequest request) {
        if (request.getKey() == null || request.getKey().isBlank()) {
            throw new IllegalArgumentException("'key' must not be blank.");
        }
        if (request.getValue() == null) {
            throw new IllegalArgumentException("'value' must not be null.");
        }
    }
}
