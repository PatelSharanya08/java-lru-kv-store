package com.redislite.exception;

/**
 * KeyNotFoundException – thrown when a GET or DELETE targets a missing key.
 *
 * Extends RuntimeException so callers aren't forced to catch it; the global
 * handler in GlobalExceptionHandler translates it to HTTP 404.
 */
public class KeyNotFoundException extends RuntimeException {

    public KeyNotFoundException(String key) {
        super("Key not found: '" + key + "'");
    }
}
