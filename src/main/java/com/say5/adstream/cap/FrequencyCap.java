package com.say5.adstream.cap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-(user, placement) sliding window. The cap rejects requests
 * once a user has seen N impressions for a given placement within
 * windowMs of the current moment.
 *
 * <p>Implementation is a deque of timestamps per (user, placement)
 * pair. On every check we trim entries older than `now - windowMs`,
 * then compare size against the cap. O(N) worst-case where N is
 * the cap; for typical caps (5-20 impressions/24h) this is
 * negligible.
 *
 * <p>Production swaps the in-memory map for Redis with a sorted
 * set per key. The contract here — `allow(userId, placementId, now)`
 * + `record(userId, placementId, now)` — stays.
 */
public final class FrequencyCap {

    private final int maxImpressions;
    private final long windowMs;
    private final Map<String, Deque<Long>> impressions = new HashMap<>();

    public FrequencyCap(int maxImpressions, long windowMs) {
        if (maxImpressions <= 0) throw new IllegalArgumentException("maxImpressions > 0");
        if (windowMs <= 0) throw new IllegalArgumentException("windowMs > 0");
        this.maxImpressions = maxImpressions;
        this.windowMs = windowMs;
    }

    public boolean allow(String userId, String placementId, long nowMs) {
        Deque<Long> q = trim(userId, placementId, nowMs);
        return q.size() < maxImpressions;
    }

    public void record(String userId, String placementId, long nowMs) {
        Deque<Long> q = trim(userId, placementId, nowMs);
        q.addLast(nowMs);
    }

    private Deque<Long> trim(String userId, String placementId, long nowMs) {
        String key = userId + "|" + placementId;
        Deque<Long> q = impressions.computeIfAbsent(key, k -> new ArrayDeque<>());
        long cutoff = nowMs - windowMs;
        while (!q.isEmpty() && q.peekFirst() < cutoff) {
            q.pollFirst();
        }
        return q;
    }

    public int currentCount(String userId, String placementId, long nowMs) {
        return trim(userId, placementId, nowMs).size();
    }
}
