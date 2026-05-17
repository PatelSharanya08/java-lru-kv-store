package com.redislite.cache;

/**
 * DoublyLinkedList – Tracks access order for LRU eviction.
 *
 * Layout (most-recently-used → least-recently-used):
 *
 *   [HEAD sentinel] ↔ [newest node] ↔ … ↔ [oldest node] ↔ [TAIL sentinel]
 *
 * Why sentinel nodes?
 *   They eliminate all null-pointer edge cases.  We never need to check
 *   "is prev null?" – it's always HEAD.  Same for TAIL.
 *
 * Key operations (all O(1)):
 *   addToFront(node)   – called on PUT and on cache-hit (mark as most-recent)
 *   remove(node)       – called on DELETE or before re-inserting on GET
 *   removeLast()       – called during LRU eviction to drop the oldest entry
 */
public class DoublyLinkedList {

    // Sentinel nodes – never hold real data, just act as stable head/tail anchors
    private final Node head = new Node("HEAD", null, Long.MAX_VALUE);
    private final Node tail = new Node("TAIL", null, Long.MAX_VALUE);

    private int size = 0;

    DoublyLinkedList() {
        // Wire sentinels together so the list is always non-null at both ends
        head.next = tail;
        tail.prev = head;
    }

    /** Insert node right after HEAD (makes it the most-recently-used entry). */
    void addToFront(Node node) {
        node.next      = head.next;
        node.prev      = head;
        head.next.prev = node;
        head.next      = node;
        size++;
    }

    /**
     * Unlink a node from wherever it currently sits.
     * Used before re-inserting (on GET / PUT update) and on DELETE.
     */
    void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        // Help GC – clear dangling pointers
        node.prev = null;
        node.next = null;
        size--;
    }

    /**
     * Remove and return the node just before TAIL (the least-recently-used entry).
     * Returns null if the list is logically empty (only sentinels remain).
     */
    Node removeLast() {
        if (head.next == tail) return null;   // list is empty
        Node lru = tail.prev;
        remove(lru);
        return lru;
    }

    int size() {
        return size;
    }
}
