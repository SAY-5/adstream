package com.say5.adstream.cap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrequencyCapTest {

    @Test
    void allowsBelowCap() {
        var cap = new FrequencyCap(3, 60_000);
        assertTrue(cap.allow("u1", "p1", 0L));
        cap.record("u1", "p1", 0L);
        assertTrue(cap.allow("u1", "p1", 1L));
    }

    @Test
    void blocksAtCap() {
        var cap = new FrequencyCap(2, 60_000);
        cap.record("u1", "p1", 0L);
        cap.record("u1", "p1", 100L);
        assertFalse(cap.allow("u1", "p1", 200L));
    }

    @Test
    void slidingWindowExpiresOldImpressions() {
        var cap = new FrequencyCap(2, 1000);
        cap.record("u1", "p1", 0L);
        cap.record("u1", "p1", 500L);
        // At t=2000 the impression at t=0 should have aged out.
        assertTrue(cap.allow("u1", "p1", 2000L));
    }

    @Test
    void perPlacementIsolation() {
        var cap = new FrequencyCap(1, 60_000);
        cap.record("u1", "p1", 0L);
        // Same user, different placement — independent.
        assertTrue(cap.allow("u1", "p2", 100L));
    }

    @Test
    void perUserIsolation() {
        var cap = new FrequencyCap(1, 60_000);
        cap.record("u1", "p1", 0L);
        assertTrue(cap.allow("u2", "p1", 100L));
    }

    @Test
    void currentCountReflectsWindow() {
        var cap = new FrequencyCap(5, 1000);
        for (int i = 0; i < 3; i++) cap.record("u1", "p1", i * 100L);
        assertEquals(3, cap.currentCount("u1", "p1", 250L));
    }

    @Test
    void invalidConfigThrows() {
        assertThrows(IllegalArgumentException.class, () -> new FrequencyCap(0, 1000));
        assertThrows(IllegalArgumentException.class, () -> new FrequencyCap(1, 0));
    }
}
