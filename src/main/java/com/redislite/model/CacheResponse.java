package com.redislite.model;

/**
 * CacheResponse – Standard envelope returned by GET /cache/{key}.
 */
public class CacheResponse {

    private String key;
    private String value;
    private String message;

    // ── Factory helpers ─────────────────────────────────────────────────────
    public static CacheResponse hit(String key, String value) {
        CacheResponse r = new CacheResponse();
        r.key     = key;
        r.value   = value;
        r.message = "Cache HIT";
        return r;
    }

    public static CacheResponse miss(String key) {
        CacheResponse r = new CacheResponse();
        r.key     = key;
        r.value   = null;
        r.message = "Cache MISS – key not found or expired";
        return r;
    }

    // ── Getters / Setters ───────────────────────────────────────────────────
    public String getKey()              { return key; }
    public void   setKey(String key)    { this.key = key; }

    public String getValue()              { return value; }
    public void   setValue(String value)  { this.value = value; }

    public String getMessage()                { return message; }
    public void   setMessage(String message)  { this.message = message; }
}
