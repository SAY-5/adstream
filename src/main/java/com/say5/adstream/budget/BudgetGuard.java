package com.say5.adstream.budget;

import java.util.HashMap;
import java.util.Map;

/**
 * v4: per-bidder budget tracking. Each bidder declares a daily
 * spend cap; once they hit it, their bids stop counting toward
 * auctions even if they're still on the wire.
 *
 * <p>Without this, a misconfigured bidder can blow through a
 * month of spend in an hour. With it, the guard takes them
 * offline cleanly.
 *
 * <p>The math is straightforward — running total, compare to cap —
 * but the contract matters: tryReserve() does the check and the
 * reserve in one atomic step so two concurrent auctions can't
 * both push the same bidder over budget. We use synchronized
 * because the operation is fast and contention is per-bidder.
 */
public final class BudgetGuard {

    public static final class BidderBudget {
        long capMicros;
        long spentMicros;

        BidderBudget(long capMicros) { this.capMicros = capMicros; }
    }

    private final Map<String, BidderBudget> budgets = new HashMap<>();

    public synchronized void register(String bidderId, long dailyCapMicros) {
        budgets.put(bidderId, new BidderBudget(dailyCapMicros));
    }

    /**
     * Atomically check that bidder has room for {@code priceMicros}
     * and (if so) record the reservation. Returns true if the bid
     * is allowed; false if it would overshoot the cap.
     */
    public synchronized boolean tryReserve(String bidderId, long priceMicros) {
        BidderBudget b = budgets.get(bidderId);
        if (b == null) return true;  // unregistered = unlimited
        if (b.spentMicros + priceMicros > b.capMicros) return false;
        b.spentMicros += priceMicros;
        return true;
    }

    /** Refund a reservation that didn't actually clear (loser of an
     * auction). Keeps spend numbers honest. */
    public synchronized void refund(String bidderId, long priceMicros) {
        BidderBudget b = budgets.get(bidderId);
        if (b != null) b.spentMicros = Math.max(0, b.spentMicros - priceMicros);
    }

    public synchronized long spent(String bidderId) {
        BidderBudget b = budgets.get(bidderId);
        return b == null ? 0L : b.spentMicros;
    }

    public synchronized long remaining(String bidderId) {
        BidderBudget b = budgets.get(bidderId);
        return b == null ? Long.MAX_VALUE : Math.max(0L, b.capMicros - b.spentMicros);
    }
}
