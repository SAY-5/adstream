package com.say5.adstream.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BudgetGuardTest {

    @Test
    void unregisteredBidderUnlimited() {
        var g = new BudgetGuard();
        assertTrue(g.tryReserve("a", 1_000_000_000L));
    }

    @Test
    void respectsCap() {
        var g = new BudgetGuard();
        g.register("a", 500);
        assertTrue(g.tryReserve("a", 200));
        assertTrue(g.tryReserve("a", 300));
        assertFalse(g.tryReserve("a", 1));  // would overshoot
    }

    @Test
    void refundRestoresHeadroom() {
        var g = new BudgetGuard();
        g.register("a", 100);
        assertTrue(g.tryReserve("a", 100));
        assertFalse(g.tryReserve("a", 1));
        g.refund("a", 100);
        assertTrue(g.tryReserve("a", 100));
    }

    @Test
    void spentAndRemainingTrack() {
        var g = new BudgetGuard();
        g.register("a", 1000);
        g.tryReserve("a", 300);
        assertEquals(300L, g.spent("a"));
        assertEquals(700L, g.remaining("a"));
    }

    @Test
    void perBidderIsolation() {
        var g = new BudgetGuard();
        g.register("a", 100);
        g.register("b", 100);
        g.tryReserve("a", 100);
        // a is exhausted; b is still wide open.
        assertFalse(g.tryReserve("a", 1));
        assertTrue(g.tryReserve("b", 100));
    }

    @Test
    void refundClampsToZero() {
        var g = new BudgetGuard();
        g.register("a", 1000);
        g.refund("a", 999_999);
        assertEquals(0L, g.spent("a"));
    }
}
