package com.redislite.model;

/**
 * CacheRequest – Payload for POST /cache.
 *
 * ttlSeconds is optional; omit it (or send 0) for a non-expiring entry.
 */
public class CacheRequest {

    private String key;
    private String value;
    private long   ttlSeconds;   // 0 = no expiry

    // ── Constructors ────────────────────────────────────────────────────────
    public CacheRequest() {}

    public CacheRequest(String key, String value, long ttlSeconds) {
        this.key        = key;
        this.value      = value;
        this.ttlSeconds = ttlSeconds;
    }

    // ── Getters / Setters ───────────────────────────────────────────────────
    public String getKey()            { return key; }
    public void   setKey(String key)  { this.key = key; }

    public String getValue()              { return value; }
    public void   setValue(String value)  { this.value = value; }

    public long getTtlSeconds()               { return ttlSeconds; }
    public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
}
